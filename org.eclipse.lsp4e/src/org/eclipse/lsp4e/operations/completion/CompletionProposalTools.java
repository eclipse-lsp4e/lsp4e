/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.completion;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

public final class CompletionProposalTools {

	private CompletionProposalTools() {
		// to avoid instances, requested by sonar
	}

	/**
	 * The portion of the document leading up to the cursor that is being used as a
	 * filter for requesting completion assist
	 *
	 * @param document
	 * @param cursorOffset
	 * @param completionItemFilter
	 * @param completionInsertionOffset
	 * @return The longest prefix to the current cursor position that is found
	 *         within the completion's filter regardless of character spacing
	 * @throws BadLocationException
	 */
	public static String getFilterFromDocument(IDocument document, int cursorOffset, String completionItemFilter,
			int completionInsertionOffset) throws BadLocationException {
		if (completionInsertionOffset >= cursorOffset) {
			return ""; //$NON-NLS-1$
		}
		int prefixToCursorLength = cursorOffset - completionInsertionOffset;
		String prefixToCursor = document.get(completionInsertionOffset, prefixToCursorLength);
		int i;
		for (i = 0; i < prefixToCursorLength; i++) {
			if (!isSubstringFoundOrderedInString(
					prefixToCursor.substring(prefixToCursorLength - i - 1, prefixToCursorLength),
					completionItemFilter)) {
				break;
			}
		}
		return prefixToCursor.substring(prefixToCursorLength - i);
	}

	/**
	 * If each of the character in the subString are within the given string in
	 * order
	 *
	 * @param subString
	 * @param string
	 */
	public static boolean isSubstringFoundOrderedInString(String subString, String string) {
		int lastIndex = 0;
		subString = subString.toLowerCase();
		string = string.toLowerCase();
		for (Character c : subString.toCharArray()) {
			int index = string.indexOf(c, lastIndex);
			if (index < 0) {
				return false;
			} else {
				lastIndex = index + 1;
			}
		}
		return true;
	}

	/**
	 * Uses the document's filter and the completion's filter to decided which
	 * category the match is.<br>
	 * Category 1:<br>
	 * The full completion filter is found within the document filter without a word
	 * characters as it's prefix or suffix<br>
	 * Category 2:<br>
	 * The full completion filter is found within the document filter without a word
	 * characters as it's prefix<br>
	 * Category 3:<br>
	 * The full completion filter is found within the document filter<br>
	 * Category 4:<br>
	 * {@link #isSubstringFoundOrderedInString(String, String)}(documentFilter, completionFilter) ==
	 * true<br>
	 * Category 5:<br>
	 * Catch all case, usually when all the document's filter's characters are not
	 * found within the completion filter
	 *
	 * @param documentFilter
	 * @param completionFilter
	 * @return the category integer
	 */
	public static int getCategoryOfFilterMatch(String documentFilter, String completionFilter) {
		if (documentFilter.isEmpty()) {
			return 5;
		}
		documentFilter = documentFilter.toLowerCase();
		completionFilter = completionFilter.toLowerCase();
		int subIndex = completionFilter.indexOf(documentFilter);
		int topCategory = 5;
		if (subIndex == -1) {
			return isSubstringFoundOrderedInString(documentFilter, completionFilter) ? 4 : 5;
		}
		final int documentFilterLength = documentFilter.length();
		final int completionFilterLength = completionFilter.length();
		while (subIndex != -1) {
			if (subIndex > 0 && Character.isLetterOrDigit(completionFilter.charAt(subIndex - 1))) {
				topCategory = Math.min(topCategory, 3);
			} else if (subIndex + documentFilterLength < completionFilterLength - 1
					&& Character.isLetterOrDigit(completionFilter.charAt(subIndex + documentFilterLength + 1))) {
				topCategory = Math.min(topCategory, 2);
			} else {
				topCategory = 1;
			}
			if (topCategory == 1) {
				break;
			}
			subIndex = completionFilter.indexOf(documentFilter, subIndex + 1);
		}
		return topCategory;
	}

	/**
	 * Uses the document's filter and the completion's filter to decided how
	 * successful the match is and gives it a score.<br>
	 * The score is decided by the number of character that prefix each of the
	 * document's filter's characters locations in the competion's filter excluding
	 * document filter characters that follow other document filter characters.<br>
	 * <br>
	 * ex.<br>
	 * documentFilter: abc<br>
	 * completionFilter: xaxxbc<br>
	 * result: 5<br>
	 * logic:<br>
	 * There is 1 character before the 'a' and there is 4 characters before the
	 * 'b', because the 'c' is directly after the 'b', it's prefix is ignored,<br>
	 * 1+4=5
	 *
	 * @param documentFilter
	 * @param completionFilter
	 * @return score of the match where the lower the number, the better the score
	 *         and -1 mean there was no match
	 */
	public static int getScoreOfFilterMatch(final String documentFilter, final String completionFilter) {
		return getScoreOfFilterMatchHelper(0, documentFilter.toLowerCase(), completionFilter.toLowerCase());
	}

	private static int getScoreOfFilterMatchHelper(final int prefixLength, final String documentFilter,
			final String completionFilter) {
		if (documentFilter.isEmpty()) {
			return 0;
		}

		final char searchChar = documentFilter.charAt(0);
		int i = completionFilter.indexOf(searchChar);
		if (i == -1) {
			return -1;
		}

		final int documentFilterLength = documentFilter.length();
		if (documentFilterLength == 1) {
			return i + prefixLength;
		}

		int bestScore = Integer.MAX_VALUE;

		while (i != -1) {
			final int matchLength = commonPrefixLength(documentFilter, completionFilter.substring(i));
			if (matchLength == documentFilterLength) {
				return i + prefixLength;
			}
			int score = i + getScoreOfFilterMatchHelper(prefixLength + i + matchLength,
					documentFilter.substring(matchLength),
					completionFilter.substring(i + matchLength));
			if (score == i - 1) {
				break;
			}
			bestScore = Math.min(bestScore, score);
			i = completionFilter.indexOf(searchChar, i + 1);
		}
		return prefixLength + bestScore;
	}

	private static int commonPrefixLength(final String first, final String second) {
		int i;
		final var maxCommonLength = Math.min(first.length(), second.length());
		for (i = 0; i < maxCommonLength; i++) {
			if (first.charAt(i) != second.charAt(i))
				break;
		}
		return i;
	}
}
