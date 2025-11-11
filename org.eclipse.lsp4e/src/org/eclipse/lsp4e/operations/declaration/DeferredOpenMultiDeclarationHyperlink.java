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
 ******************************************************************************/
package org.eclipse.lsp4e.operations.declaration;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;

/**
 * An implementation of {@link IHyperlink} which asynchronously opens a chooser
 * of links once the language server has responded. Opening is dismissed if the
 * editor was closed in the meantime, the document was modified, or the response
 * took longer than a given timeout.
 */
final class DeferredOpenMultiDeclarationHyperlink implements IHyperlink {

	private static final long DEFERRED_OPEN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5);

	private final ITextViewer viewer;
	private final IDocument document;
	private final long documentInitialModificationStamp;
	private final IRegion region;
	private final CompletableFuture<? extends List<? extends IHyperlink>> future;
	private final long createdNanos = System.nanoTime();

	DeferredOpenMultiDeclarationHyperlink(final ITextViewer viewer, final IDocument document, final IRegion region,
			final CompletableFuture<? extends List<? extends IHyperlink>> future) {
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
		return "Open Declaration (resolving...)"; //$NON-NLS-1$
	}

	@Override
	public @Nullable String getHyperlinkText() {
		return "Open Declaration (resolving...)"; //$NON-NLS-1$
	}

	@Override
	public void open() {
		future.whenComplete((links, ex) -> {
			if (ex != null) {
				LanguageServerPlugin.logError(ex.getLocalizedMessage(), ex);
				return;
			}
			final var widget = viewer.getTextWidget();
			if (widget == null)
				return;
			if (links.isEmpty()) {
				LanguageServerPlugin.logWarning("No hyperlink targets resolved for Open Declaration"); //$NON-NLS-1$
				return;
			}
			widget.getDisplay().asyncExec(() -> {
				if (isStale())
					return;

				if (links.size() == 1) {
					links.get(0).open();
					return;
				}

				final Shell shell = widget.getShell();
				final var dialog = new ElementListSelectionDialog(shell, new LabelProvider() {
					@Override
					public String getText(final @Nullable Object element) {
						if (element instanceof final IHyperlink link) {
							final String text = link.getHyperlinkText();
							return text != null ? text : link.getTypeLabel();
						}
						return element == null ? "" : element.toString(); //$NON-NLS-1$
					}
				});
				dialog.setTitle(Messages.declarationHyperlinkLabel);
				dialog.setMessage("Select a target:"); //$NON-NLS-1$
				dialog.setElements(links.toArray());
				dialog.setMultipleSelection(false);
				if (dialog.open() == Window.OK) {
					Object result = dialog.getFirstResult();
					if (result instanceof IHyperlink link) {
						link.open();
					}
				}
			});
		});
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
