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
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Text computations for accepting inline completions word by word and for
 * replacing the text after the cursor. Words are recognized by the
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

	/**
	 * Returns the modification stamp of the document of an editor.
	 *
	 * @param textEditor
	 *            the editor
	 * @return the modification stamp, or
	 *         {@link org.eclipse.jface.text.IDocumentExtension4#UNKNOWN_MODIFICATION_STAMP}
	 *         if not available
	 */
	public static long getDocumentModificationStamp(ITextEditor textEditor) {
		return DocumentUtil.getDocumentModificationStamp(LSPEclipseUtils.getDocument(textEditor));
	}

	/**
	 * Returns the length of the text after the offset that inserting a completion
	 * replaces: nothing before a line end, except a line end itself if the
	 * completion is a single line feed; all but one of the spaces after the
	 * offset; or the word at the offset.
	 *
	 * @param documentText
	 *            the text of the document
	 * @param offset
	 *            the offset at which the completion is inserted
	 * @param textToInsert
	 *            the completion text
	 * @param wordDetector
	 *            the detector of the document's words
	 * @return the length of the range to replace
	 */
	public static int getReplaceRangeLength(String documentText, int offset, String textToInsert,
			IWordDetector wordDetector) {
		final int spaceCount = countSpaces(documentText, offset);
		final int offsetAfterSpaces = offset + spaceCount;
		final char nextCharacter = offsetAfterSpaces < documentText.length() ? documentText.charAt(offsetAfterSpaces)
				: '\0';
		if (nextCharacter == '\n' || nextCharacter == '\r') {
			final int newLineLength = nextCharacter == '\r' && offsetAfterSpaces + 1 < documentText.length()
					&& documentText.charAt(offsetAfterSpaces + 1) == '\n' ? 2 : 1;
			return spaceCount + textToInsert.length() == 1 && textToInsert.charAt(0) == '\n' ? newLineLength : 0;
		} else if (spaceCount > 0) {
			return spaceCount - 1;
		}
		return getWordLength(documentText, offset, wordDetector);
	}
}
