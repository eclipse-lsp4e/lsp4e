/*******************************************************************************
 * Copyright (c) 2017, 2024 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Martin Lippert (Pivotal Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension;
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;

@SuppressWarnings({ "restriction" })
public class LSJavaCompletionProposalComputer implements IJavaCompletionProposalComputer {

	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;
	private static final long TIMEOUT_LENGTH = 300;

	private final LSContentAssistProcessor lsContentAssistProcessor = new LSContentAssistProcessor(false);
	private @Nullable String javaCompletionSpecificErrorMessage;

	@Override
	public void sessionStarted() {
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		final var viewer = context.getViewer();
		if(viewer == null)
			return List.of();
		CompletableFuture<ICompletionProposal[]> future = CompletableFuture.supplyAsync(() ->
			lsContentAssistProcessor.computeCompletionProposals(viewer, context.getInvocationOffset()));

		try {
			return List.of(asJavaProposals(future));
		} catch (ExecutionException | TimeoutException e) {
			LanguageServerPlugin.logError(e);
			javaCompletionSpecificErrorMessage = createErrorMessage(e);
			return List.of();
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			javaCompletionSpecificErrorMessage = createErrorMessage(e);
			Thread.currentThread().interrupt();
			return List.of();
		}
	}

	private String createErrorMessage(Exception ex) {
		return Messages.javaSpecificCompletionError + " : " + (ex.getMessage() != null ? ex.getMessage() : ex.toString()); //$NON-NLS-1$
	}

	/**
	 * In order for the LS proposals to appear in the right order by JDT, we need to return IJavaCompletionProposal.
	 * The LSPCompletionProposal that LSP4E computes is NOT IJavaCompletionProposal, and as a consequence JDT
	 * will by default sort any non Java proposals by display value, which is why we would get a strange sorting order,
	 * even if our the LS and LSP4E both return a proposal list in the right order.
	 *
	 * This method wraps around the LSCompletionProposal with a IJavaCompletionProposal, and it sets the relevance
	 * number that JDT uses to sort proposals in a desired order.
	 */
	private ICompletionProposal[] asJavaProposals(CompletableFuture<ICompletionProposal[]> future)
			throws InterruptedException, ExecutionException, TimeoutException {
		ICompletionProposal[] originalProposals = future.get(TIMEOUT_LENGTH, TIMEOUT_UNIT);

		final var javaProposals = new ICompletionProposal[originalProposals.length];
		for (int i = 0; i < originalProposals.length; i++) {
			if (originalProposals[i] instanceof ICompletionProposalExtension2) {
				javaProposals[i] = new LSJavaProposalExtension2(originalProposals[i]);
			} else if (originalProposals[i] instanceof ICompletionProposalExtension) {
				javaProposals[i] = new LSJavaProposalExtension(originalProposals[i]);
			} else {
				javaProposals[i] = new LSJavaProposal(originalProposals[i]);
			}
		}
		return javaProposals;
	}

	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		final var viewer = context.getViewer();
		if(viewer == null)
			return List.of();
		IContextInformation[] contextInformation = lsContentAssistProcessor.computeContextInformation(viewer, context.getInvocationOffset());
		return contextInformation == null ? List.of() : List.of(contextInformation);
	}

	@Override
	public @Nullable String getErrorMessage() {
		return javaCompletionSpecificErrorMessage != null ? javaCompletionSpecificErrorMessage : lsContentAssistProcessor.getErrorMessage();
	}

	@Override
	public void sessionEnded() {
	}

}
