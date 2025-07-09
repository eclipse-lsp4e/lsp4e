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
import java.util.List;
import java.util.Objects;
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
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;

public class DocumentLinkDetector extends AbstractHyperlinkDetector {

	public static class DocumentHyperlink implements IHyperlink {

		private final String uri;
		private final IRegion highlightRegion;

		public DocumentHyperlink(String uri, IRegion highlightRegion) {
			this.uri = uri;
			this.highlightRegion = highlightRegion;
		}

		@Override
		public IRegion getHyperlinkRegion() {
			return this.highlightRegion;
		}

		@Override
		public String getTypeLabel() {
			return uri;
		}

		@Override
		public String getHyperlinkText() {
			return uri;
		}

		@Override
		public void open() {
			LSPEclipseUtils.open(uri, null, true);
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
		final var params = new DocumentLinkParams(LSPEclipseUtils.toTextDocumentIdentifier(uri));
		try {
			return LanguageServers.forDocument(document)
					.withFilter(capabilities -> capabilities.getDocumentLinkProvider() != null)
					.collectAll(languageServer -> languageServer.getTextDocumentService().documentLink(params))
					.thenApply(links -> {
						IHyperlink[] res = links.stream().flatMap(List<DocumentLink>::stream).filter(Objects::nonNull)
								.filter(link -> link.getTarget() != null).map(link -> toHyperlink(region, document, link))
								.filter(Objects::nonNull).toArray(IHyperlink[]::new);
						if (res.length == 0) {
							return null;
						} else {
							return res;
						}
					}).get(4, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			LanguageServerPlugin.logError(e);
			return null;
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
			return null;
		} catch (TimeoutException e) {
			LanguageServerPlugin.logWarning("Could not detect hyperlinks due to timeout after 4 seconds"); //$NON-NLS-1$
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
