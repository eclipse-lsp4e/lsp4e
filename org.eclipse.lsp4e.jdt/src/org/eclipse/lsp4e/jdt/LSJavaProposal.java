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

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.internal.codeassist.RelevanceConstants;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

@SuppressWarnings("restriction")
class LSJavaProposal implements IJavaCompletionProposal {
	
	private static final int LS_DEFAULT_RELEVANCE = 18;
	
	private static final int MAX_BASE_RELEVANCE = 51 * 16; // Looks like JDT's max for exact match is 52
	
	 // Based on org.eclipse.jdt.internal.ui.text.java.RelevanceComputer
	private static final int DEFAULT_RELEVANCE = (RelevanceConstants.R_DEFAULT + LS_DEFAULT_RELEVANCE) * 16;

	private static final int RANGE_WITHIN_CATEGORY = Math.round((MAX_BASE_RELEVANCE - DEFAULT_RELEVANCE) / 4f);

	protected ICompletionProposal delegate;
	private boolean relevanceComputed = false;
	private int relevance = -1;
	
	public LSJavaProposal(ICompletionProposal delegate) {
		this.delegate = delegate;
	}

	@Override
	public void apply(IDocument document) {
		delegate.apply(document);
	}

	@Override
	public @Nullable String getAdditionalProposalInfo() {
		return delegate.getAdditionalProposalInfo();
	}

	@Override
	public @Nullable IContextInformation getContextInformation() {
		return delegate.getContextInformation();
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
	public @Nullable Point getSelection(IDocument document) {
		return delegate.getSelection(document);
	}

	@Override
	public int getRelevance() {
		if (!relevanceComputed) {
			if (delegate instanceof LSCompletionProposal c) {
				// Based on org.eclipse.jdt.internal.ui.text.java.RelevanceComputer
				relevance = computeBaseRelevance(c);
				switch (c.getItem().getKind()) {
				case Class:
					relevance += 3;
					break;
				case Field:
				case Property:	
					relevance += 5;
					break;
				case Method:
					relevance += 4;
					break;
				case Variable:
				case Value:
					relevance += 6;
					break;
				default:
				}
			}
			relevanceComputed = true;
		}
		return relevance;
	}
	
	private int computeBaseRelevance(LSCompletionProposal c) {
		// Incorporate LSP4E category and rank into base relevance.
		int base = MAX_BASE_RELEVANCE - (c.getRankCategory() - 1) * RANGE_WITHIN_CATEGORY;
		int rank = c.getRankScore();
		base -= (rank >= 0 && rank < RANGE_WITHIN_CATEGORY ? rank : RANGE_WITHIN_CATEGORY);
		return base;
	}

}
