/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Jozef Tomek - fix getting misaligned style ranges (#1220)
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
				final var linkRegion = new Region(start, length);

				// Update existing style range with underline or create a new style range with
				// underline
				StyleRange styleRange = null;
				StyleRange[] styleRanges = null;
				if (textViewer != null) {
					// returns widget region just for visible part of link region
					var widgetRange = textViewer.modelRange2WidgetRange(linkRegion);
					if (widgetRange != null) {
						int widgetOffset = widgetRange.getOffset();
						styleRanges = textViewer.getTextWidget().getStyleRanges(widgetOffset, widgetRange.getLength());
						if (styleRanges != null && styleRanges.length > 0) {
							// There are some styles for the range of document link, update just the underline style.

							// only part of the link area may be visible, so we need to adjust our document coordinates
							int visibleStart = textViewer.widgetOffset2ModelOffset(widgetOffset);
							int startOffset = visibleStart - widgetOffset;
							for (StyleRange s : styleRanges) {
								s.underline = true;
								s.start += startOffset; // shift to align with start of link in document coordinates
							}
							// fill the gaps at the start/end of the link region if not fully covered by existing styles
							styleRanges = fillStartEndGaps(styleRanges, start, end);
						}
					}
				} else {
					styleRanges = viewer.getTextWidget().getStyleRanges(start, length);
					if (styleRanges != null && styleRanges.length > 0) {
						// There are some styles for the range of document link, update just the underline style.
						for (StyleRange s : styleRanges) {
							s.underline = true;
						}
					}
				}
				if (styleRanges != null && styleRanges.length > 0) {
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

	private StyleRange[] fillStartEndGaps(StyleRange[] styleRanges, int linkStart, int linkEnd) {
		var lastStyle = styleRanges[styleRanges.length - 1];
		int startGap = styleRanges[0].start - linkStart;
		int endGap = linkEnd - (lastStyle.start + lastStyle.length);
		int stylesToAdd = startGap > 0 ? 1 : 0 + endGap > 0 ? 1 : 0;
		if (stylesToAdd > 0) {
			StyleRange[] modifiedRanges = new StyleRange[styleRanges.length + stylesToAdd];
			StyleRange styleRange;
			if (startGap > 0) {
				System.arraycopy(styleRanges, 0, modifiedRanges, 1, styleRanges.length);
				modifiedRanges[0] = styleRange = new StyleRange();
				styleRange.underline = true;
				styleRange.start = linkStart;
				styleRange.length = startGap;
			} else {
				System.arraycopy(styleRanges, 0, modifiedRanges, 0, styleRanges.length);
			}
			if (endGap > 0) {
				modifiedRanges[modifiedRanges.length - 1] = styleRange = new StyleRange();
				styleRange.underline = true;
				styleRange.start = linkEnd - endGap;
				styleRange.length = endGap;
			}
			return modifiedRanges;
		}
		return styleRanges;
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