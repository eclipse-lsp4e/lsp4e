/*******************************************************************************
 * Copyright (c) 2022 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextPresentationListener;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.IReconcilingStrategyExtension;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.LanguageServiceAccessor.LSPDocumentInfo;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.LanguageServer;
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
		implements IReconcilingStrategy, IReconcilingStrategyExtension, ITextPresentationListener {

    private static final int FULL_RECONCILE_TIMEOUT_S = 10;

    private final boolean disabled;

	private ITextViewer viewer;

	private IDocument document;

	private StyleRangeHolder styleRangeHolder;

	private SemanticTokensDataStreamProcessor semanticTokensDataStreamProcessor;

	/**
	 * Written in {@link this.class#applyTextPresentation(TextPresentation)}
	 * applyTextPresentation and read in the lambda in
	 * {@link this.class#semanticTokensFull(LanguageServer, int)}, the lambda and
	 * the former method are executed in the display thread, thus serializing access
	 * using volatile without using explicit synchronized blocks is enough to avoid
	 * that org.eclipse.jface.text.ITextViewer.invalidateTextPresentation() is
	 * called by use while the presentation is being updated.
	 */
	private volatile int documentVersionAtLastAppliedTextPresentation;

	private CompletableFuture<Void> semanticTokensFullFuture;

	public SemanticHighlightReconcilerStrategy() {
	    IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		disabled = store.getBoolean("semanticHighlightReconciler.disabled"); //$NON-NLS-1$
	}

	/**
	 * Installs the reconciler on the given text viewer. After this method has been
	 * finished, the reconciler is operational, i.e., it works without requesting
	 * further client actions until <code>uninstall</code> is called.
	 *
	 * @param textViewer
	 *            the viewer on which the reconciler is installed
	 */
	public void install(@NonNull final ITextViewer textViewer) {
		if (disabled) {
			return;
		}
		viewer = textViewer;
		styleRangeHolder = new StyleRangeHolder();
		semanticTokensDataStreamProcessor = new SemanticTokensDataStreamProcessor(new TokenTypeMapper(textViewer),
				offsetMapper());

		if (viewer instanceof TextViewer) {
			((TextViewer) viewer).addTextPresentationListener(this);
		}
		viewer.addTextListener(styleRangeHolder);
	}

	/**
	 * Removes the reconciler from the text viewer it has previously been installed
	 * on.
	 */
	public void uninstall() {
		if (disabled) {
			return;
		}
		semanticTokensDataStreamProcessor = null;
		if (viewer instanceof TextViewer) {
			((TextViewer) viewer).removeTextPresentationListener(this);
		}
		viewer.removeTextListener(styleRangeHolder);
		viewer = null;
		styleRangeHolder = null;
	}

	private @NonNull Function<Position, Integer> offsetMapper() {
		return (p) -> {
			try {
				return LSPEclipseUtils.toOffset(p, document);
			} catch (BadLocationException e) {
				throw new RuntimeException(e);
			}
		};
	}

	private SemanticTokensParams getSemanticTokensParams() {
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri != null) {
			SemanticTokensParams semanticTokensParams = new SemanticTokensParams();
			semanticTokensParams.setTextDocument(new TextDocumentIdentifier(uri.toString()));
			return semanticTokensParams;
		}
		return null;
	}

	private void saveStyle(final SemanticTokens semanticTokens, final SemanticTokensLegend semanticTokensLegend) {
		if (semanticTokens == null || semanticTokensLegend == null) {
			return;
		}
		List<Integer> dataStream = semanticTokens.getData();
		if (!dataStream.isEmpty()) {
			List<StyleRange> styleRanges = semanticTokensDataStreamProcessor.getStyleRanges(dataStream,
					semanticTokensLegend);
			styleRangeHolder.saveStyles(styleRanges);
		}
	}

	@Override
	public void setProgressMonitor(final IProgressMonitor monitor) {
	}

	@Override
	public void setDocument(final IDocument document) {
		this.document = document;
	}

	private boolean hasSemanticTokensFull(final ServerCapabilities serverCapabilities) {
		return serverCapabilities.getSemanticTokensProvider() != null
				&& serverCapabilities.getSemanticTokensProvider().getFull().getLeft();
	}

	private CompletableFuture<Void> semanticTokensFull(final List<LanguageServer> languageServers, final int version) {
		return CompletableFuture.allOf(
				languageServers.stream().map(ls -> semanticTokensFull(ls, version)).toArray(CompletableFuture[]::new));
	}

	private boolean isRequestCancelledException(final Throwable throwable) {
		if (throwable instanceof CompletionException) {
			Throwable cause = ((CompletionException) throwable).getCause();
			if (cause instanceof ResponseErrorException) {
				ResponseError responseError = ((ResponseErrorException) cause).getResponseError();
				return responseError != null
						&& responseError.getCode() == ResponseErrorCode.RequestCancelled.getValue();
			}
		}
		return false;
	}

	private @Nullable SemanticTokensLegend getSemanticTokensLegend(final LanguageServerWrapper wrapper) {
		ServerCapabilities serverCapabilities = wrapper.getServerCapabilities();
		if (serverCapabilities != null) {
			SemanticTokensWithRegistrationOptions semanticTokensProvider = serverCapabilities
					.getSemanticTokensProvider();
			if (semanticTokensProvider != null) {
				return semanticTokensProvider.getLegend();
			}
		}
		return null;
	}

	// public for testing
	public @Nullable SemanticTokensLegend getSemanticTokensLegend(@NonNull final LanguageServer languageSever) {
		return LanguageServiceAccessor.resolveLanguageServerWrapper(languageSever)
				.map(this::getSemanticTokensLegend).orElse(null);
	}

	/** The presentation is invalidated if applyTextPresentation has never been called (e.g. there is
	 * no syntactic reconciler as in our unit tests) or the syntactic reconciler has already been applied
	 * for the given document. Otherwise the style rages will be applied when applyTextPresentation is
	 * called as part of the syntactic reconciliation.
	 */
	private boolean invalidateTextPresentation(final int version) {
		return documentVersionAtLastAppliedTextPresentation == 0
				|| documentVersionAtLastAppliedTextPresentation == version;
	}

	private CompletableFuture<Void> semanticTokensFull(final LanguageServer languageServer, final int version) {
		SemanticTokensParams semanticTokensParams = getSemanticTokensParams();
		return languageServer.getTextDocumentService().semanticTokensFull(semanticTokensParams)
				.thenAccept(semanticTokens -> {
					if (getDocumentVersion() == version) {
						saveStyle(semanticTokens, getSemanticTokensLegend(languageServer));
						StyledText textWidget = viewer.getTextWidget();
						textWidget.getDisplay().asyncExec(() -> {
							if (!textWidget.isDisposed() && invalidateTextPresentation(version)) {
								viewer.invalidateTextPresentation();
							}
						});
					}
				}).exceptionally(e -> {
					if (!isRequestCancelledException(e)) {
						LanguageServerPlugin.logError(e);
					}
					return null;
				});
	}

	private int getDocumentVersion() {
		IDocument theDocument = document;
		if (theDocument != null) {
			Iterator<@NonNull LSPDocumentInfo> iterator = LanguageServiceAccessor
					.getLSPDocumentInfosFor(theDocument, this::hasSemanticTokensFull).iterator();
			if (iterator.hasNext()) {
				@Nullable LSPDocumentInfo documentInfo = iterator.next();
				if (documentInfo != null) {
					return documentInfo.getVersion();
				}
			}
		}
		return -1;
	}

	private void cancelSemanticTokensFull() {
		if (semanticTokensFullFuture != null) {
			semanticTokensFullFuture.cancel(true);
		}
	}

	private void fullReconcile() {
		if (disabled) {
			return;
		}
		IDocument theDocument = document;
		cancelSemanticTokensFull();
		if (theDocument != null) {
			try {
				int version = getDocumentVersion();
				semanticTokensFullFuture = LanguageServiceAccessor.getLanguageServers(theDocument, this::hasSemanticTokensFull)//
						.thenAccept(ls -> semanticTokensFull(ls, version));
				semanticTokensFullFuture.get(FULL_RECONCILE_TIMEOUT_S, TimeUnit.SECONDS);
			} catch (ExecutionException e) {
				LanguageServerPlugin.logError(e);
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				Thread.currentThread().interrupt();
			} catch (TimeoutException e) {
				LanguageServerPlugin.logWarning("Could not get semantic tokens due to timeout after " + FULL_RECONCILE_TIMEOUT_S + " seconds", e); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
	}

	@Override
	public void initialReconcile() {
		fullReconcile();
	}

	@Override
	public void reconcile(final DirtyRegion dirtyRegion, final IRegion subRegion) {
		fullReconcile();
	}

	@Override
	public void reconcile(final IRegion partition) {
		fullReconcile();
	}

	@Override
	public void applyTextPresentation(final TextPresentation textPresentation) {
		documentVersionAtLastAppliedTextPresentation = getDocumentVersion();
		IRegion extent = textPresentation.getExtent();
		if (extent != null) {
			textPresentation.replaceStyleRanges(styleRangeHolder.overlappingRanges(extent));
		}
	}
}
