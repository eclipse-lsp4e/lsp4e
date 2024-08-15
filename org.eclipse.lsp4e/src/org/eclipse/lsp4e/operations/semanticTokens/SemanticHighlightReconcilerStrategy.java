/*******************************************************************************
 * Copyright (c) 2022 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextPresentationListener;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerLifecycle;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.CancellationUtil;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4e.internal.Pair;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;

/**
 * A semantic reconciler strategy using LSP.
 * <p>
 * This strategy applies semantic highlight on top of the syntactic highlight
 * provided by TM4E by implementing {@link ITextPresentationListener}. Because
 * semantic highlight involves remote calls, it is expected to be slower than
 * syntactic highlight. Thus we execute semantic highlight processing in a
 * separate reconciler thread that will never block TM4E.
 * <p>
 * This strategy records the version of the document when the semantic highlight
 * is sent to the LS and when TM4E highlight is applied. If the response for a
 * particular document version comes after the TM4E highlight has been applied,
 * the text presentation is invalidated so that highlight is extended with the
 * results.
 * <p>
 * To avoid flickering, {@link StyleRangeHolder} implement {@link ITextListener}
 * to adapt recorded semantic highlights and apply those instead of nothing
 * where needed.
 * <p>
 * If the response comes before, the data is saved and applied later on top of
 * the presentation provided by TM4E. The results from our reconciler are
 * recorded
 * <p>
 * For simplicity, out-dated responses are discarded, as we know we shall get
 * newer ones.
 * <p>
 * In case the reconciler produces bogus results, it can be disabled with the key
 * {@literal semanticHighlightReconciler.disabled} until fix is provided.
 */
public class SemanticHighlightReconcilerStrategy
		implements IReconcilingStrategy, IReconcilingStrategyExtension, ITextPresentationListener, ITextViewerLifecycle {

	public static final String SEMANTIC_HIGHLIGHT_RECONCILER_DISABLED = "semanticHighlightReconciler.disabled"; //$NON-NLS-1$

	private final boolean disabled;

	private @Nullable ITextViewer viewer;

	private @Nullable IDocument document;

	private @Nullable StyleRangeHolder styleRangeHolder;

	private @Nullable SemanticTokensDataStreamProcessor semanticTokensDataStreamProcessor;

	/**
	 * Written in {@link this.class#applyTextPresentation(TextPresentation)}
	 * applyTextPresentation and read in the lambda in
	 * {@link this.class#semanticTokensFull(LanguageServer, int)}, the lambda and
	 * the former method are executed in the display thread, thus serializing access
	 * using volatile without using explicit synchronized blocks is enough to avoid
	 * that org.eclipse.jface.text.ITextViewer.invalidateTextPresentation() is
	 * called by use while the presentation is being updated.
	 */
	private volatile long documentTimestampAtLastAppliedTextPresentation;

	private volatile long timestamp = 0;

	private @Nullable CompletableFuture<Optional<VersionedSemanticTokens>> semanticTokensFullFuture;

	private StyleRangeMerger merger;

	public SemanticHighlightReconcilerStrategy() {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		disabled = store.getBoolean(SEMANTIC_HIGHLIGHT_RECONCILER_DISABLED);
		boolean overrideBold = !store.getBoolean("semanticHighlightReconciler.ignoreBoldNormal"); //$NON-NLS-1$
		boolean overrideItalic = !store.getBoolean("semanticHighlightReconciler.ignoreItalicNormal"); //$NON-NLS-1$
		merger = new StyleRangeMerger(overrideBold, overrideItalic);
	}

	/**
	 * Installs the reconciler on the given text viewer. After this method has been
	 * finished, the reconciler is operational, i.e., it works without requesting
	 * further client actions until <code>uninstall</code> is called.
	 *
	 * @param textViewer
	 *            the viewer on which the reconciler is installed
	 */
	@Override
	public void install(final ITextViewer textViewer) {
		if (disabled || viewer != null) {
			return;
		}
		semanticTokensDataStreamProcessor = new SemanticTokensDataStreamProcessor(TokenTypeMapper.create(textViewer),
				offsetMapper());

		if (textViewer instanceof final TextViewer textViewerImpl) {
			textViewerImpl.addTextPresentationListener(this);
		}
		styleRangeHolder = new StyleRangeHolder();
		textViewer.addTextListener(styleRangeHolder);
		viewer = textViewer;
	}

	/**
	 * Removes the reconciler from the text viewer it has previously been installed
	 * on.
	 */
	@Override
	public void uninstall() {
		final var viewer = this.viewer;
		if (disabled || viewer == null) {
			return;
		}
		this.viewer = null; // Indicate that we're not installed or in the phase of deinstalling
		cancelSemanticTokensFull();
		semanticTokensDataStreamProcessor = null;
		if (viewer instanceof final TextViewer textViewerImpl) {
			textViewerImpl.removeTextPresentationListener(this);
		}
		if (styleRangeHolder != null) {
			viewer.removeTextListener(styleRangeHolder);
			styleRangeHolder = null;
		}
	}

	private Function<Position, Integer> offsetMapper() {
		return p -> {
			try {
				return LSPEclipseUtils.toOffset(p, castNonNull(document));
			} catch (BadLocationException e) {
				throw new RuntimeException(e);
			}
		};
	}

	private void saveStyle(final Pair<@Nullable SemanticTokens, @Nullable SemanticTokensLegend> pair) {
		final SemanticTokens semanticTokens = pair.first();
		final SemanticTokensLegend semanticTokensLegend = pair.second();

		// Skip any processing if not installed or at least one of the pair values is null
		if (viewer == null || semanticTokens == null || semanticTokensLegend == null) {
			return;
		}
		List<Integer> dataStream = semanticTokens.getData();
		final var semanticTokensDataStreamProcessor = this.semanticTokensDataStreamProcessor;
		final var styleRangeHolder = this.styleRangeHolder;
		if (!dataStream.isEmpty() && semanticTokensDataStreamProcessor != null && styleRangeHolder != null) {
			List<StyleRange> styleRanges = semanticTokensDataStreamProcessor.getTokensData(dataStream,
					semanticTokensLegend);
			styleRangeHolder.saveStyles(styleRanges);
		}
	}

	@Override
	public void setProgressMonitor(final @Nullable IProgressMonitor monitor) {
	}

	@Override
	public void setDocument(final @Nullable IDocument document) {
		this.document = document;
	}

	/** The presentation is invalidated if applyTextPresentation has never been called (e.g. there is
	 * no syntactic reconciler as in our unit tests) or the syntactic reconciler has already been applied
	 * for the given document. Otherwise the style rages will be applied when applyTextPresentation is
	 * called as part of the syntactic reconciliation.
	 */

	private boolean outdatedTextPresentation(final long documentTimestamp) {
		return documentTimestampAtLastAppliedTextPresentation == 0
				|| documentTimestampAtLastAppliedTextPresentation == documentTimestamp;
	}

	private void invalidateTextPresentation(final Long documentTimestamp) {
		final var viewer = this.viewer;
		if (viewer == null) { // Skip any processing
			return;
		}
		StyledText textWidget = viewer.getTextWidget();
		textWidget.getDisplay().asyncExec(() -> {
			if (!textWidget.isDisposed() && outdatedTextPresentation(documentTimestamp)) {
				ITextViewer theViewer = this.viewer;
				if (theViewer != null) {
					theViewer.invalidateTextPresentation();
				}
			}
		});
	}

	private void cancelSemanticTokensFull() {
		if (semanticTokensFullFuture != null) {
			semanticTokensFullFuture.cancel(true);
		}
	}

	private void fullReconcile() {
		final var viewer = this.viewer;
		if (disabled || viewer == null) { // Skip any processing
			return;
		}
		final var document = this.document;
		cancelSemanticTokensFull();
		if (document != null) {
			long modificationStamp = DocumentUtil.getDocumentModificationStamp(document);
			try {
				final var semanticTokensFullFuture = SemanticTokensClient.DEFAULT.requestFullSemanticTokens(document,
						(legend, semanticTokens) -> new VersionedSemanticTokens(modificationStamp, Pair.of(semanticTokens, legend), document));
				this.semanticTokensFullFuture = semanticTokensFullFuture;
				semanticTokensFullFuture.get() // background thread with cancellation support, no timeout needed
						.ifPresent(versionedSemanticTokens ->
								versionedSemanticTokens.apply(this::saveStyle, this::invalidateTextPresentation));
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				if (!CancellationUtil.isRequestCancelledException(e)) { // do not report error if the server has cancelled the request
					LanguageServerPlugin.logError(e);
				}
			}
		}
	}

	@Override
	public void initialReconcile() {
		fullReconcile();
	}

	@Override
	public void reconcile(final DirtyRegion dirtyRegion, final IRegion subRegion) {
		fullReconcileOnce();
	}

	@Override
	public void reconcile(final IRegion partition) {
		fullReconcileOnce();
	}

	/**
	 * Because a full reconcile will be performed always on the whole document,
	 * prevent consecutive reconciling on dirty regions or partitions if the document has not changed.
	 */
	private void fullReconcileOnce() {
		var ts = DocumentUtil.getDocumentModificationStamp(document);
		if (ts != timestamp) {
			fullReconcile();
			timestamp = ts;
		}
	}

	@Override
	public void applyTextPresentation(final TextPresentation textPresentation) {
		documentTimestampAtLastAppliedTextPresentation = DocumentUtil.getDocumentModificationStamp(document);
		merger.mergeStyleRanges(textPresentation, styleRangeHolder);
	}
}
