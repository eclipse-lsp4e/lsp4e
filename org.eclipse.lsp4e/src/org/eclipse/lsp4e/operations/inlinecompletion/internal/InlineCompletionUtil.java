/*******************************************************************************
 * Copyright (c) 2025, 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.rules.IWordDetector;

/**
 * Text computations for accepting inline completions word by word and line by
 * line. Words are recognized by the
 * {@link IWordDetector} of the document, see
 * {@link InlineCompletionExtensions#getWordDetector(org.eclipse.jface.text.IDocument)}.
 */
public final class InlineCompletionUtil {

	private static final List<String> TWO_CHAR_WORDS = List.of(":=", "=>", "!=", "--", "/*", "*/", "\r\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$
	private static final int TWO_CHAR_LENGTH = 2;
	// on one line, with backslash escapes; possessive, so that long strings do not overflow the stack
	private static final Pattern SINGLE_QUOTED_STRING = Pattern.compile("'[^'\\\\\\r\\n]*+(?:\\\\.[^'\\\\\\r\\n]*+)*+'"); //$NON-NLS-1$
	private static final Pattern DOUBLE_QUOTED_STRING = Pattern.compile("\"[^\"\\\\\\r\\n]*+(?:\\\\.[^\"\\\\\\r\\n]*+)*+\""); //$NON-NLS-1$

	private InlineCompletionUtil() {
	}

	/**
	 * Returns the length of the word at the start of the text.
	 *
	 * @param text
	 *            the text
	 * @param wordDetector
	 *            the detector of the document's words
	 * @return the length of the word, 0 if the text is empty
	 * @see #getWordLength(String, int, IWordDetector)
	 */
	public static int getWordLength(String text, IWordDetector wordDetector) {
		return getWordLength(text, 0, wordDetector);
	}

	/**
	 * Returns the length of the word at the offset: a word of the detector, a run
	 * of spaces, a quoted string on one line, a two-character operator or a single
	 * character.
	 *
	 * @param text
	 *            the text
	 * @param offset
	 *            the offset in the text
	 * @param wordDetector
	 *            the detector of the document's words
	 * @return the length of the word, 0 if the offset is at or after the end of
	 *         the text
	 */
	public static int getWordLength(String text, int offset, IWordDetector wordDetector) {
		if (text.length() <= offset) {
			return 0;
		}
		final int detectedWordLength = getDetectedWordLength(text, offset, wordDetector);
		if (detectedWordLength > 0) {
			return detectedWordLength;
		}
		final char character = text.charAt(offset);
		if (character == ' ') {
			return countSpaces(text, offset);
		} else if (character == '\'' || character == '"') {
			final Matcher matcher = (character == '\'' ? SINGLE_QUOTED_STRING : DOUBLE_QUOTED_STRING).matcher(text)
					.region(offset, text.length());
			if (matcher.lookingAt()) {
				return matcher.end() - offset;
			}
		} else if (offset + 1 < text.length()
				&& (TWO_CHAR_WORDS.contains(text.substring(offset, offset + TWO_CHAR_LENGTH))
						|| Character.isSurrogatePair(character, text.charAt(offset + 1)))) {
			return TWO_CHAR_LENGTH;
		}
		return 1;
	}

	/**
	 * Returns the length of the text's first line, including its line break.
	 *
	 * @param text
	 *            the text
	 * @return the length of the first line, that of the text if it has no line break
	 */
	public static int getLineLength(String text) {
		for (int i = 0; i < text.length(); i++) {
			final char character = text.charAt(i);
			if (character == '\n') {
				return i + 1;
			}
			if (character == '\r') {
				return i + 1 < text.length() && text.charAt(i + 1) == '\n' ? i + 2 : i + 1;
			}
		}
		return text.length();
	}

	private static int getDetectedWordLength(String text, int offset, IWordDetector wordDetector) {
		if (!wordDetector.isWordStart(text.charAt(offset))) {
			return 0;
		}
		int end = offset + 1;
		while (end < text.length() && wordDetector.isWordPart(text.charAt(end))) {
			end++;
		}
		return end - offset;
	}

	private static int countSpaces(String text, int offset) {
		int end = offset;
		while (end < text.length() && text.charAt(end) == ' ') {
			end++;
		}
		return end - offset;
	}
}
