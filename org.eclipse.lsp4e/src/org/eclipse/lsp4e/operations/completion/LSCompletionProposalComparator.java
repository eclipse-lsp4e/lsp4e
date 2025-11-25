/*******************************************************************************
 * Copyright (c) 2016, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *   Michał Niewrzał (Rogue Wave Software Inc.)
 *   Lucas Bullen (Red Hat Inc.) - Refactored for incomplete completion lists
 *******************************************************************************/
package org.eclipse.lsp4e.operations.completion;

import static org.eclipse.lsp4e.operations.completion.CompletionProposalTools.*;

import java.util.Comparator;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4e.LanguageServerPlugin;

public final class LSCompletionProposalComparator implements Comparator<LSCompletionProposal> {
	@Override
	public int compare(LSCompletionProposal o1, LSCompletionProposal o2) {
		int category1 = o1.getRankCategory();
		int category2 = o2.getRankCategory();

		// Prefer proposals that have a stronger match (categories 1-4),
		// but use the document filter length only for those categories so that
		// completely unmatched proposals (category CATEGORY_NO_MATCH) are not affected.
		if (category1 < CATEGORY_NO_MATCH && category2 < CATEGORY_NO_MATCH) {
			try {
				int docFilterLen1 = o1.getDocumentFilter().length();
				int docFilterLen2 = o2.getDocumentFilter().length();
				if (docFilterLen1 > docFilterLen2) {
					return -1;
				} else if (docFilterLen1 < docFilterLen2) {
					return 1;
				}
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}

		if (category1 < category2) {
			return -1;
		} else if (category1 > category2) {
			return 1;
		}

		if (category1 < CATEGORY_NO_MATCH /* && category2 < CATEGORY_NO_MATCH */) {
			int score1 = o1.getRankScore();
			int score2 = o2.getRankScore();
			if (!(score1 == -1 && score2 == -1)) {
				if (score1 == -1) {
					return 1;
				} else if (score2 == -1) {
					return -1;
				} else if (score1 < score2) {
					return -1;
				} else if (score1 > score2) {
					return 1;
				}
			}
		}

		String c1 = o1.getSortText();
		String c2 = o2.getSortText();
		return c1.compareToIgnoreCase(c2);
	}
}
