/*******************************************************************************
 * Copyright (c) 2025 Broadcom Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Alex Boyko (Broadcom Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

class LSJavaCompletionWrappingCompletionProposal implements IJavaCompletionProposal {
	
	private final ICompletionProposal delegate;
	
	public LSJavaCompletionWrappingCompletionProposal(ICompletionProposal delegate) {
		this.delegate = delegate;
	}

	@Override
	public void apply(IDocument document) {
		delegate.apply(document);
	}

	@Override
	public @Nullable Point getSelection(IDocument document) {
		return delegate.getSelection(document);
	}

	@Override
	public @Nullable String getAdditionalProposalInfo() {
		return delegate.getAdditionalProposalInfo();
	}

	@Override
	public String getDisplayString() {
		return delegate.getDisplayString();
	}

	@Override
	public @Nullable Image getImage() {
		return delegate.getImage();
	}

	@Override
	public @Nullable IContextInformation getContextInformation() {
		return delegate.getContextInformation();
	}

	@Override
	public int getRelevance() {
		return -1;
	}

}
