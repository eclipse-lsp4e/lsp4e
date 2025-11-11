/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation.
 *******************************************************************************/
package org.eclipse.lsp4e.operations.declaration;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.DocumentUtil;

/**
 * An implementation of {@link IHyperlink} which asynchronously opens the link
 * once the language server has responded. Opening is dismissed if the editor
 * was closed in the meantime, the document was modified, or the response took
 * longer than a given timeout.
 */
final class DeferredOpenDeclarationHyperlink implements IHyperlink {

	private static final long DEFERRED_OPEN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

	private final ITextViewer viewer;
	private final IDocument document;
	private final long documentInitialModificationStamp;
	private final IRegion region;
	private final CompletableFuture<@Nullable IHyperlink> future;
	private final long createdNanos = System.nanoTime();

	DeferredOpenDeclarationHyperlink(final ITextViewer viewer, final IDocument document, final IRegion region,
			final CompletableFuture<@Nullable IHyperlink> future) {
		this.viewer = viewer;
		this.document = document;
		this.region = region;
		this.future = future;
		this.documentInitialModificationStamp = DocumentUtil.getDocumentModificationStamp(document);
	}

	@Override
	public IRegion getHyperlinkRegion() {
		return region;
	}

	@Override
	public @Nullable String getTypeLabel() {
		final var link = getResolvedLink();
		return link != null ? link.getTypeLabel() : null;
	}

	@Override
	public @Nullable String getHyperlinkText() {
		final var link = getResolvedLink();
		return link != null ? link.getHyperlinkText() : null;
	}

	@Override
	public void open() {
		future.whenComplete((link, ex) -> {
			if (ex != null) {
				LanguageServerPlugin.logError(ex.getLocalizedMessage(), ex);
				return;
			}
			final var widget = viewer.getTextWidget();
			if (widget == null)
				return;
			if (link == null) {
				LanguageServerPlugin.logWarning("No hyperlink target resolved for Open Declaration"); //$NON-NLS-1$
				return;
			}
			widget.getDisplay().asyncExec(() -> {
				if (isStale())
					return;
				link.open();
			});
		});
	}

	private @Nullable IHyperlink getResolvedLink() {
		try {
			return future.getNow(null);
		} catch (CompletionException ex) {
			LanguageServerPlugin.logError(ex.getLocalizedMessage(), ex);
			return null;
		}
	}

	private boolean isStale() {
		// LS response came too late?
		if (System.nanoTime() - createdNanos > DEFERRED_OPEN_TIMEOUT_NANOS)
			return true;

		// Editor was closed?
		final var widget = viewer.getTextWidget();
		if (widget == null || widget.isDisposed())
			return true;

		// Document was modified?
		if (documentInitialModificationStamp != IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP
				&& DocumentUtil.getDocumentModificationStamp(document) != documentInitialModificationStamp)
			return true;

		return false;
	}
}
