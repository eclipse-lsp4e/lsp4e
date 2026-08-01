/*******************************************************************************
 * Copyright (c) 2026 Daniel Schmid and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Daniel Schmid - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt.internal;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4e.LanguageServerPlugin;

/**
 * This class is used for mapping positions within a text block in Java source
 * code and the content (the text it corresponds to).
 * <p>
 * This works by traversing the source and content together until the position
 * is reached and then returning the current position in the other array.
 */
public final class TextBlockSourceContentMapper {
	/**
	 * The source code segment of the text block (part of the .java file) including
	 * the leading and trailing """
	 */
	private final char[] source;
	private int currentSourceIndex;
	/**
	 * The index in {@link #source} where content of the text block ends (i.e. where
	 * the ending """ starts)
	 */
	private final int sourceEnd;

	/**
	 * {@link #source} with leading incidential whitespace removed
	 */
	private final char[] sourceWithoutLeadingWhitespace;
	private int currentSourceWithoutLeadingWhitespaceIndex;

	/**
	 * The logical content/string of the text block
	 */
	private final char[] content;
	private int currentContentIndex;

	/**
	 * Used only by {@link #skipTrailingSpaces()}
	 */
	private int lastFoundFutureNonWhitespaceInSourceLine;

	private final int doNotSkipTrailingSpacesAt;

	private TextBlockSourceContentMapper(char[] source, char[] content, IRegion contentRegion,
			int doNotSkipTrailingSpacesAt) {
		this.source = source;
		this.content = content;
		sourceWithoutLeadingWhitespace = String.valueOf(source, contentRegion.getOffset(), contentRegion.getLength())
				.stripIndent().toCharArray();

		currentSourceIndex = contentRegion.getOffset();
		sourceEnd = contentRegion.getLength();
		currentContentIndex = 0;
		currentSourceWithoutLeadingWhitespaceIndex = 0;
		this.doNotSkipTrailingSpacesAt = doNotSkipTrailingSpacesAt;
	}

	private static @Nullable TextBlockSourceContentMapper newInstance(char[] source, char[] content,
			int doNotSkipTrailingSpacesAt) {
		IRegion contentRegion = getContentRegion(source);
		if (contentRegion == null) {
			return null;
		}
		return new TextBlockSourceContentMapper(source, content, contentRegion, doNotSkipTrailingSpacesAt);
	}

	private static @Nullable IRegion getContentRegion(char[] source) {
		int start;
		char lastChar;
		for (start = 0; start < source.length;) {
			CharacterAndNextIndex current = getCharacterAtAndNextIndex(source, start);
			start = current.next();
			lastChar = current.c();
			if (lastChar == '\n') {
				break;
			}
		}

		int end = source.length - 3;
		if (start < end) {
			return new Region(start, end - start);
		}
		return null;
	}

	public static int mapSourceToContent(char[] source, char[] content, int sourceIndex,
			int doNotSkipTrailingSpacesAt) {
		return mapIndexBetweenSourceAndContent(source, content, sourceIndex, true, doNotSkipTrailingSpacesAt);
	}

	public static int mapContentToSource(char[] source, char[] content, int contentIndex,
			int doNotSkipTrailingSpacesAt) {
		return mapIndexBetweenSourceAndContent(source, content, contentIndex, false, doNotSkipTrailingSpacesAt);
	}

	private static int mapIndexBetweenSourceAndContent(char[] source, char[] content, int goalIndex,
			boolean sourceToContent, int doNotSkipTrailingSpacesAt) {

		if (goalIndex > (sourceToContent ? source.length : content.length)) {
			return -1;
		}

		TextBlockSourceContentMapper mapper = newInstance(source, content, doNotSkipTrailingSpacesAt);
		if (mapper == null) {
			return -1;
		}
		mapper.skipIncidentialWhitespaceAtStartOfLine(); // first line after initial """

		while (mapper.currentSourceIndex < mapper.sourceEnd && mapper.currentContentIndex < mapper.content.length
				&& (sourceToContent && mapper.currentSourceIndex < goalIndex
						|| !sourceToContent && mapper.currentContentIndex < goalIndex)) {
			mapper.step();
		}
		return sourceToContent ? mapper.currentContentIndex : mapper.currentSourceIndex;
	}

	private void step() {
		// TODO test escapes properly
		
		CharacterAndNextIndex currentSource = getCharacterAtAndNextIndex(source, currentSourceIndex);

		if (currentSource.isAbortedByEscapedLineBreak()) {
			currentSourceIndex = currentSource.next();
			currentSourceWithoutLeadingWhitespaceIndex = getCharacterAtAndNextIndex(sourceWithoutLeadingWhitespace,
					currentSourceWithoutLeadingWhitespaceIndex).next();
			skipIncidentialWhitespaceAtStartOfLine();
			return;
		}

		if (Character.isWhitespace(currentSource.c()) && !Character.isWhitespace(content[currentContentIndex])) {
			logWhitespaceMismatch();

			// increment source until non-whitespace found
			// This is a fallback trying to recover if something went out of sync
			currentSourceIndex = findNextNonWhitespace(source, currentSourceIndex);
			currentSourceWithoutLeadingWhitespaceIndex = findNextNonWhitespace(sourceWithoutLeadingWhitespace,
					currentSourceWithoutLeadingWhitespaceIndex);
		} else if (!Character.isWhitespace(currentSource.c()) && Character.isWhitespace(content[currentContentIndex])) {
			logWhitespaceMismatch();

			// increment content until non-whitespace found
			// This is a fallback trying to recover if something went out of sync
			moveToNextNonWhitespaceInContent();
		} else {
			// move forward by one character
			currentSourceIndex = currentSource.next();
			currentContentIndex++;
			currentSourceWithoutLeadingWhitespaceIndex = getCharacterAtAndNextIndex(sourceWithoutLeadingWhitespace,
					currentSourceWithoutLeadingWhitespaceIndex).next();
			// line break at source --> skipIncidentialWhitespaceAtStartOfLine
			if (currentSource.isActualLineBreakInSourceArray()) {
				skipIncidentialWhitespaceAtStartOfLine();
			} else {
				skipTrailingSpaces();
			}
		}
		// check for condition
	}

	private void logWhitespaceMismatch() {
		LanguageServerPlugin.logWarning(
				"""
						A whitespace mismatch occured when mapping positions between a text block source and content at source index at source index %d and content index %d
						Source:
						%s
						Content:
						%s
						"""
						.formatted(currentSourceIndex, currentContentIndex, String.valueOf(source),
								String.valueOf(content)));
	}

	private void skipTrailingSpaces() {
		if (currentSourceIndex < lastFoundFutureNonWhitespaceInSourceLine) {
			return;
		}
		// will be run by step()
		for (int i = currentSourceIndex; i < sourceEnd; i++) {
			// TODO unicode escapes
			if (!Character.isWhitespace(source[i])) {
				// Prevent unnecessary computation with consecutive calls when a line contains a
				// lot of whitespace is followed by a non-whitespace
				lastFoundFutureNonWhitespaceInSourceLine = i;
				return;
			}
			if (i == doNotSkipTrailingSpacesAt) {
				return;
			}
			if (source[i] == '\r' || source[i] == '\n') {
				currentSourceIndex = i;
				return;
			}
		}
		currentSourceIndex = sourceEnd;
	}

	private void moveToNextNonWhitespaceInContent() {
		while (currentContentIndex < content.length && Character.isWhitespace(content[currentContentIndex])) {
			currentContentIndex++;
		}
	}

	private static int findNextNonWhitespace(char[] arr, int currentIndex) {
		CharacterAndNextIndex current;
		int nextIndex = currentIndex;
		do {
			currentIndex = nextIndex;
			current = getCharacterAtAndNextIndex(arr, currentIndex);
			nextIndex = current.next();
		} while (Character.isWhitespace(current.c()) && current.next() < arr.length);
		return currentIndex;
	}

	private void skipIncidentialWhitespaceAtStartOfLine() {
		currentSourceIndex = findStartOfLineAfterIncidentialWhitespace(source, currentSourceIndex,
				sourceWithoutLeadingWhitespace, currentSourceWithoutLeadingWhitespaceIndex);
	}

	private static int findStartOfLineAfterIncidentialWhitespace(char[] source, int currentSourceIndex,
			char[] sourceWithoutLeadingWhitespace, int currentSourceWithoutLeadingWhitespaceIndex) {
		int sourceWhitespace = countLeadingWhitespaceAtLine(source, currentSourceIndex);
		int strippedWhitespace = countLeadingWhitespaceAtLine(sourceWithoutLeadingWhitespace,
				currentSourceWithoutLeadingWhitespaceIndex);
		if (strippedWhitespace < sourceWhitespace) {
			return skipNCharacters(sourceWhitespace - strippedWhitespace, source, currentSourceIndex);
		}
		return currentSourceIndex;
	}

	private static int skipNCharacters(int n, char[] arr, int currentIndex) {
		for (int i = 0; i < n && currentIndex < arr.length; i++) {
			CharacterAndNextIndex current = getCharacterAtAndNextIndex(arr, currentIndex);
			currentIndex = current.next();
		}
		return currentIndex;
	}

	private static int countLeadingWhitespaceAtLine(char[] arr, int lineStartIndex) {
		int count = 0;
		int i = lineStartIndex;
		while (i < arr.length) {
			CharacterAndNextIndex current = getCharacterAtAndNextIndex(arr, i);
			char c = current.c();
			if (!Character.isWhitespace(c) || c == '\n') {
				return count;
			}
			i = current.next();
			count++;
		}
		return count;
	}

	/**
	 * Gets the content character at a specific index in the content from a source
	 * array as well as the index of the next character.
	 * 
	 * In case of an actual line break (e.g. '\n' but not '\' followed by 'n') in
	 * the source code, this will always return '\n' even if the line break is not
	 * present in the content. This situation is indicated by flags in the returned
	 * value.
	 * 
	 * The next index may be {@code arr.length} when the end of the array is
	 * reached.
	 * 
	 * @param arr
	 *                         The source array
	 * @param currentIndex
	 *                         The index of the character to get
	 * @return
	 */
	private static CharacterAndNextIndex getCharacterAtAndNextIndex(char[] arr, int currentIndex) {
		if (currentIndex >= arr.length) {
			return new CharacterAndNextIndex('\0', arr.length, false, false);
		}

		char currentCharacter = arr[currentIndex];
		int nextIndex = currentIndex + 1;

		if ((currentCharacter != '\\') || (nextIndex >= arr.length)) {
			if (currentCharacter == '\r' && nextIndex < arr.length && arr[nextIndex] == '\n') {
				currentCharacter = '\n';
				nextIndex++;
			}
			return new CharacterAndNextIndex(currentCharacter, nextIndex, false,
					currentCharacter == '\n');
		}

		char nextCharacter = arr[nextIndex];
		switch (nextCharacter) {
		case 'u' -> {
			// TODO need to execute remaining logic as well here since lexing is before
			// parsing - also possible twice after each other
			if (nextIndex + 4 < arr.length) {
				try {
					int codepoint = Integer.parseInt(String.valueOf(arr, nextIndex + 1, 4));
					return new CharacterAndNextIndex((char) codepoint, nextIndex + 5, false, false);
				} catch (NumberFormatException e) {
					return new CharacterAndNextIndex(' ', nextIndex + 5, false, false);
				}
			}
			return new CharacterAndNextIndex('\0', arr.length, false, false);
		}
		case '\n' -> {
			return new CharacterAndNextIndex('\n', nextIndex + 1, true, true);
		}
		case '\r' -> {
			if (nextIndex + 1 < arr.length && arr[nextIndex + 1] == '\n') {
				nextIndex++;
			}
			return new CharacterAndNextIndex('\n', nextIndex + 1, true, true);
		}
		default -> {
			return new CharacterAndNextIndex(String.valueOf(arr, currentIndex, 2).translateEscapes().charAt(0),
					nextIndex + 1, false, false);
		}
		}
	}

	/**
	 * The result of
	 * {@link TextBlockSourceContentMapper#getCharacterAtAndNextIndex(char[], int)}.
	 *
	 * @param c
	 *                                           The character
	 * @param next
	 *                                           The index of the next character
	 * @param isAbortedByEscapedLineBreak
	 *                                           Whether a \n was returned because
	 *                                           of a line break which is not
	 *                                           included in the content (a line in
	 *                                           the source code ending with a
	 *                                           backslash)
	 * @param isActualLineBreakInSourceArray
	 *                                           Whether this is a real line break
	 *                                           within the source code
	 */
	record CharacterAndNextIndex(char c, int next, boolean isAbortedByEscapedLineBreak,
			boolean isActualLineBreakInSourceArray) {
	}
}
