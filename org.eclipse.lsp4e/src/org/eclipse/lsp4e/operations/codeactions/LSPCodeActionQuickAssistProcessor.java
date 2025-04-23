/*******************************************************************************
 * Copyright (c) 2019, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Andrew Obuchowicz (Red Hat Inc.)
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.quickassist.IQuickAssistProcessor;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.internal.progress.ProgressInfoItem;

public class LSPCodeActionQuickAssistProcessor implements IQuickAssistProcessor {

	// Data necessary for caching proposals
	private final Object lock = new Object();
	private @Nullable IQuickAssistInvocationContext cachedContext;
	private List<ICompletionProposal> proposals = Collections.synchronizedList(new ArrayList<>());

	private static final ICompletionProposal COMPUTING = new ICompletionProposal() {

		@Override
		public void apply(IDocument document) {
			// Placeholder proposal so nothing to do here
		}

		@Override
		public @Nullable Point getSelection(IDocument document) {
			return null;
		}

		@Override
		public @Nullable String getAdditionalProposalInfo() {
			return null;
		}

		@Override
		public String getDisplayString() {
			return Messages.computing;
		}

		@Override
		public @Nullable Image getImage() {
			return JFaceResources.getImage(ProgressInfoItem.class.getPackageName() + ".PROGRESS_DEFAULT"); //$NON-NLS-1$
		}

		@Override
		public @Nullable IContextInformation getContextInformation() {
			return null;
		}
	};

	@Override
	public String getErrorMessage() {
		return "CodeActions not implemented on this Language Server"; //$NON-NLS-1$
	}

	@Override
	public boolean canFix(Annotation annotation) {
		return true;
	}

	@Override
	public boolean canAssist(IQuickAssistInvocationContext invocationContext) {
		IDocument document = invocationContext.getSourceViewer().getDocument();
		if (document == null) {
			return false;
		}
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document).withCapability(ServerCapabilities::getCodeActionProvider);
		return executor.anyMatching();
	}

	@Override
	public ICompletionProposal @Nullable [] computeQuickAssistProposals(IQuickAssistInvocationContext invocationContext) {
		IDocument document = invocationContext.getSourceViewer().getDocument();
		if (document == null) {
			return null;
		}
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document).withCapability(ServerCapabilities::getCodeActionProvider);
		if (!executor.anyMatching()) {
			return null;
		}

		// If context has changed, i.e. new quick assist invocation rather than old
		// proposals computed and calling this method artificially to show proposals in
		// the UI
		boolean needNewQuery = true;
		synchronized (lock) {
			final var cachedContext = this.cachedContext;
			needNewQuery = cachedContext == null ||
				cachedContext.getClass() != invocationContext.getClass() ||
				cachedContext.getSourceViewer() != invocationContext.getSourceViewer() ||
				cachedContext.getOffset() != invocationContext.getOffset() ||
				cachedContext.getLength() != invocationContext.getLength();
				// should also check whether (same) document content changed
			if (needNewQuery) {
				this.cachedContext = invocationContext;
			}
		}

		if (needNewQuery) {
			// Get the codeActions
			CodeActionParams params = prepareCodeActionParams(document, invocationContext.getOffset(), invocationContext.getLength());
			// Prevent infinite re-entrance by only computing proposals if there aren't any
			proposals.clear();
			// Start all the servers computing actions - each server will append any code actions to the ongoing list of proposals
			// as a side effect of this request
			List<CompletableFuture<@Nullable Void>> futures = executor.computeAll((w, ls) -> ls.getTextDocumentService()
					.codeAction(params)
					.thenAccept(actions -> LanguageServers.streamSafely(actions)
							.filter(LSPCodeActionMarkerResolution::canPerform)
							.forEach(action -> processNewProposal(invocationContext, new CodeActionCompletionProposal(action, w)))));

			CompletableFuture<?> aggregateFutures = CompletableFuture
					.allOf(futures.toArray(CompletableFuture[]::new));

			try {
					// If the result completes quickly without blocking the UI, then return result directly
					aggregateFutures.get(200, TimeUnit.MILLISECONDS);
			} catch (InterruptedException | ExecutionException e) {
				LanguageServerPlugin.logError(e);
			} catch (TimeoutException e) {
				// Server calls didn't complete in time;  those that did will have added their results to <code>this.proposals</code> and can be returned
				// as an intermediate result; as we're returning control to the UI, we need any stragglers to trigger a refresh when they arrive later on
				proposals.add(COMPUTING);
				for (CompletableFuture<@Nullable Void> future : futures) {
					// Refresh will effectively re-enter this method with the same invocationContext and already computed proposals simply to show the proposals in the UI
					future.whenComplete((r, t) -> {
						if (futures.stream().allMatch(CompletableFuture::isDone)) {
							proposals.remove(COMPUTING);
						}
						this.refreshProposals(invocationContext);
					});
				}
			}
		}
		return proposals.toArray(ICompletionProposal[]::new);
	}

	private void processNewProposal(IQuickAssistInvocationContext invocationContext, ICompletionProposal p) {
		// non-ui thread. Context might have changed (quick assist at a different spot) by the time time proposals are computed
		synchronized(lock) {
			if (cachedContext == invocationContext) {
				proposals.add(p);
			}
		}
	}

	/**
	 * Reinvokes the quickAssist in order to refresh the list of completion
	 * proposals
	 *
	 * @param invocationContext
	 *            The invocation context
	 */
	private void refreshProposals(IQuickAssistInvocationContext invocationContext) {
		invocationContext.getSourceViewer().getTextWidget().getDisplay().asyncExec(() -> invocationContext
				.getSourceViewer().getTextOperationTarget().doOperation(ISourceViewer.QUICK_ASSIST));
	}

	private static CodeActionParams prepareCodeActionParams(final IDocument doc, int offset, int length) {
		final var context = new CodeActionContext(Collections.emptyList());
		final var params = new CodeActionParams();
		params.setTextDocument(castNonNull(LSPEclipseUtils.toTextDocumentIdentifier(doc)));
		try {
			params.setRange(LSPEclipseUtils.toRange(offset, offset + (length > 0 ? length : 0), doc));
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		params.setContext(context);
		return params;
	}

}
