/*******************************************************************************
 * Copyright (c) 2017, 2019 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Martin Lippert (Pivotal Inc.) - bug 531452
 *******************************************************************************/
package org.eclipse.lsp4e.operations.documentLink;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.DocumentOffsetAsyncCache;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;

public class DocumentLinkDetector extends AbstractHyperlinkDetector {

	private static final long UI_BLOCKING_BUDGET_MS = 200;

	// document links do not depend on the hovered offset, so one entry per document is enough
	private static final int DOCUMENT_KEY = 0;

	private static final DocumentOffsetAsyncCache<List<DocumentLink>> CACHE = new DocumentOffsetAsyncCache<>(
			Duration.ofSeconds(10));

	public static class DocumentHyperlink implements IHyperlink {

		private final String uri;
		private final String label;
		private final IRegion highlightRegion;

		public DocumentHyperlink(String uri, IRegion highlightRegion) {
			this.uri = uri;
			this.label = toLabel(uri);
			this.highlightRegion = highlightRegion;
		}

		@Override
		public IRegion getHyperlinkRegion() {
			return this.highlightRegion;
		}

		@Override
		public String getTypeLabel() {
			return label;
		}

		@Override
		public String getHyperlinkText() {
			return label;
		}

		@Override
		public void open() {
			LSPEclipseUtils.open(uri, null, true);
		}

		private static String toLabel(final String uri) {
			if (uri.isEmpty())
				return uri;

			if (uri.startsWith(LSPEclipseUtils.FILE_URI)) {
				try {
					final URI fileUri = URI.create(uri);
					final String path = fileUri.getPath();
					if (path != null && !path.isEmpty())
						return path;
				} catch (final IllegalArgumentException ex) {
					LanguageServerPlugin.logError(ex);
				}
			}
			return uri;
		}
	}

	@Override
	public IHyperlink @Nullable [] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		final IDocument document = textViewer.getDocument();
		if (document == null) {
			return null;
		}
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			return null;
		}
		final CompletableFuture<List<DocumentLink>> request = CACHE.computeIfAbsent(document, DOCUMENT_KEY, () -> {
			final var params = new DocumentLinkParams(LSPEclipseUtils.toTextDocumentIdentifier(uri));
			return LanguageServers.forDocument(document)
					.withFilter(capabilities -> capabilities.getDocumentLinkProvider() != null)
					.collectAll(languageServer -> languageServer.getTextDocumentService().documentLink(params))
					.thenApply(links -> links.stream().flatMap(List<DocumentLink>::stream).filter(Objects::nonNull)
							.filter(link -> link.getTarget() != null).toList());
		});
		try {
			// a slow server must not freeze the UI; the request keeps running and serves the next mouse move
			IHyperlink[] res = request.get(UI_BLOCKING_BUDGET_MS, TimeUnit.MILLISECONDS).stream()
					.map(link -> toHyperlink(region, document, link)).filter(Objects::nonNull)
					.toArray(IHyperlink[]::new);
			return res.length == 0 ? null : res;
		} catch (ExecutionException e) {
			LanguageServerPlugin.logError(e);
			return null;
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
			return null;
		} catch (TimeoutException e) {
			return null;
		}
	}

	private @Nullable DocumentHyperlink toHyperlink(IRegion region, final IDocument document, DocumentLink link) {
		DocumentHyperlink jfaceLink = null;
		try {
			int start = LSPEclipseUtils.toOffset(link.getRange().getStart(), document);
			int end = LSPEclipseUtils.toOffset(link.getRange().getEnd(), document);
			final var linkRegion = new Region(start, end - start);
			if (TextUtilities.overlaps(region, linkRegion)) {
				jfaceLink = new DocumentHyperlink(link.getTarget(), linkRegion);
			}
		} catch (BadLocationException ex) {
			LanguageServerPlugin.logError(ex);
		}
		return jfaceLink;
	}

}
