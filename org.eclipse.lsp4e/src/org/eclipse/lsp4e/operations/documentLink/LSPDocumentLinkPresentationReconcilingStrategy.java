/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.documentLink;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerLifecycle;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

/**
 * Reconciling strategy used to display links coming from LSP
 * 'textDocument/documentLink' with underline style.
 *
 * @author Angelo ZERR
 *
 */
public class LSPDocumentLinkPresentationReconcilingStrategy
		implements IReconcilingStrategy, IReconcilingStrategyExtension, ITextViewerLifecycle {

	/** The target viewer. */
	private @Nullable ITextViewer viewer;

	private @Nullable CompletableFuture<@Nullable Void> request;

	private @Nullable IDocument document;

	@Override
	public void install(@Nullable ITextViewer viewer) {
		this.viewer = viewer;
	}

	@Override
	public void uninstall() {
		this.viewer = null;
		cancel();
	}

	private void underline() {
		ITextViewer theViewer = viewer;
		if (theViewer == null)
			return;

		final IDocument document = theViewer.getDocument();
		if (document == null) {
			return;
		}

		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			return;
		}
		cancel();
		final var params = new DocumentLinkParams(LSPEclipseUtils.toTextDocumentIdentifier(uri));
		final Control control = theViewer.getTextWidget();
		if (control != null && !control.isDisposed()) {
			Display display = control.getDisplay();
			request = LanguageServers.forDocument(document)
					.withFilter(capabilities -> capabilities.getDocumentLinkProvider() != null)
					.collectAll(languageServer -> languageServer.getTextDocumentService().documentLink(params))
					.thenAcceptAsync(links -> links.forEach(this::underline), display);
		}
	}

	private void underline(@Nullable List<DocumentLink> links) {
		final var viewer = this.viewer;
		final var document = this.document;
		if (document == null || links == null || viewer == null) {
			return;
		}
		TextViewer textViewer = viewer instanceof TextViewer ? (TextViewer) viewer : null;
		for (DocumentLink link : links) {
			try {
				// Compute link region
				int start = LSPEclipseUtils.toOffset(link.getRange().getStart(), document);
				int end = LSPEclipseUtils.toOffset(link.getRange().getEnd(), document);
				int length = end - start;
				var linkRegion = new Region(start, length);
				// Update existing style range with underline or create a new style range with underline
				StyleRange styleRange = null;
				StyleRange[] styleRanges = null;
				int startOffset = 0;
				if (textViewer != null) {
					// returns widget region just for visible part of link region
					var widgetRange = textViewer.modelRange2WidgetRange(linkRegion);
					if (widgetRange != null) {
						int widgetOffset = widgetRange.getOffset();
						styleRanges = textViewer.getTextWidget().getStyleRanges(widgetOffset, widgetRange.getLength());

						// only part of the link area may be visible, so we need to adjust our document coordinates
						int visibleStart = textViewer.widgetOffset2ModelOffset(widgetOffset);
						int visibleLength =
								textViewer.widgetOffset2ModelOffset(widgetOffset + widgetRange.getLength())
								- visibleStart;
						startOffset = visibleStart - widgetOffset;
						if (visibleLength > linkRegion.getLength()) {
							/*
							 * It's possible that link falls into a folded area in a such way that
							 * translating widget range back to document range results in larger range, in
							 * which case we must back-off by 1 from widget range.
							 *
							 * Example:
							 * 1     text of visible line
							 * 2*    start-of-the-link
							 * 3*    end-of-the-link
							 * 4     text of visible line
							 *
							 * Line 3 is folded into line 2, therefore not visible.
							 * Link range spanning region until end of line 3 (includes line 2 terminator but not
							 * line 3 terminator) is translated to widget range spanning whole line 2 including it's
							 * line terminator. Since visually 4th line starts right after line terminator at the end
							 * of line 2, when translated back to document range it becomes region spanning line 2 and 3
							 * until start of line 4, therefore line 3 terminator as well - region larger than link.
							 */
							visibleLength =
								textViewer.widgetOffset2ModelOffset(widgetOffset + widgetRange.getLength() - 1)
								- visibleStart;
						}
						if (visibleStart > linkRegion.getOffset() || visibleLength < linkRegion.getLength()) {
							linkRegion = new Region(visibleStart, visibleLength);
						}
					} else {
						// whole link is not visible - outside model coverage (visible region) or inside folded areas
						continue;
					}
				} else {
					styleRanges = viewer.getTextWidget().getStyleRanges(start, length);
				}
				if (styleRanges != null && styleRanges.length > 0) {
					// It exists some styles for the range of document link, update just the
					// underline style.
					for (StyleRange s : styleRanges) {
						s.underline = true;
						s.start += startOffset; // shift to align with start of link (in document coordinates)
					}
					final var presentation = new TextPresentation(linkRegion, 100);
					presentation.replaceStyleRanges(styleRanges);
					viewer.changeTextPresentation(presentation, false);

				} else {
					// No styles for the range of document link, create a style range with underline
					styleRange = new StyleRange();
					styleRange.underline = true;
					styleRange.start = start;
					styleRange.length = length;

					final var presentation = new TextPresentation(linkRegion, 100);
					presentation.replaceStyleRange(styleRange);
					viewer.changeTextPresentation(presentation, false);
				}

			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	@Override
	public void initialReconcile() {
		underline();
	}

	/**
	 * Cancel the last call of 'documenLink'.
	 */
	private void cancel() {
		if (request != null) {
			request.cancel(true);
			request = null;
		}
	}

	@Override
	public void setDocument(@Nullable IDocument document) {
		this.document = document;
	}

	@Override
	public void setProgressMonitor(@Nullable IProgressMonitor monitor) {
		// Do nothing
	}

	@Override
	public void reconcile(DirtyRegion dirtyRegion, IRegion subRegion) {
		// Do nothing
	}

	@Override
	public void reconcile(IRegion partition) {
		// Underline document by using textDocument/documentLink with some delay as
		// reconcile method is called in a Thread background.
		underline();
	}

}