/*******************************************************************************
 * Copyright (c) 2022, 2024 VMware Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Alex Boyko (VMware Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.util.Arrays;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.quickassist.IQuickAssistInvocationContext;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.TextInvocationContext;
import org.eclipse.lsp4e.operations.codeactions.LSPCodeActionQuickAssistProcessor;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;

@SuppressWarnings("restriction")
public class LspJavaQuickAssistProcessor extends LSPCodeActionQuickAssistProcessor implements IQuickAssistProcessor {

	private static final IJavaCompletionProposal[] NO_JAVA_COMPLETION_PROPOSALS = new IJavaCompletionProposal[0];

	private IQuickAssistInvocationContext getContext(IInvocationContext context) {
		return new IQuickAssistInvocationContext() {

			@Override
			public ISourceViewer getSourceViewer() {
				// Should be of instance or a subclass of TextInvocationContext
				return ((TextInvocationContext) context).getSourceViewer();
			}

			@Override
			public int getOffset() {
				return context.getSelectionOffset();
			}

			@Override
			public int getLength() {
				return context.getSelectionLength();
			}
		};
	}

	@Override
	public boolean hasAssists(final IInvocationContext context) throws CoreException {
		return this.canAssist(getContext(context));
	}

	@Override
	public IJavaCompletionProposal @Nullable [] getAssists(final IInvocationContext context,
			final IProblemLocation[] locations) throws CoreException {

		ICompletionProposal[] proposals = computeQuickAssistProposals(getContext(context));
		if (proposals == null)
			return NO_JAVA_COMPLETION_PROPOSALS;
		
		return Arrays.stream(proposals).map(p -> {
			if (p instanceof LSCompletionProposal lscp) {
				return new LSJavaProposal(lscp);
			}
			return new LSJavaCompletionWrappingCompletionProposal(p);
		}).toArray(IJavaCompletionProposal[]::new);
	}

}
