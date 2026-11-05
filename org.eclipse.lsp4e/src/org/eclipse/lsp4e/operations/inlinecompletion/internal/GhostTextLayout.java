/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * Decides which code minings render a {@link GhostText}, where they go and what they draw.
 * <ul>
 * <li>The first line of the ghost text is drawn at its anchor by a line content mining, which must
 * cover one character: the character after the anchor, or the {@code \r} of a CRLF delimiter.</li>
 * <li>Further lines are drawn by a line header mining at the start of the next visible line.</li>
 * <li>At the end of the document, where there is neither a character to cover nor a next line, one
 * mining of zero length draws everything.</li>
 * </ul>
 * <p>
 * Code minings can only add text, so a multi-line suggestion in the middle of a line is previewed
 * before the rest of the line, although accepting it moves that rest after its last line.
 * <p>
 * Labels have tabs expanded (minings are drawn without tab stops), control characters replaced by
 * visible symbols, empty lines replaced by a space (empty trailing lines would otherwise not be drawn)
 * and are truncated to keep huge suggestions cheap to draw.
 */
public final class GhostTextLayout {

	public enum Kind {
		/** Draws {@link MiningSpec#firstRow()} at the anchor. */
		CONTENT,
		/** Draws {@link MiningSpec#rows()} above the line starting at the mining offset. */
		HEADER,
		/** Draws {@link MiningSpec#firstRow()} at the end of the document and {@link MiningSpec#rows()} below. */
		END_OF_DOCUMENT
	}

	/**
	 * A code mining to create.
	 *
	 * @param kind what the mining draws
	 * @param offset the offset of the mining position
	 * @param length the length of the mining position
	 * @param firstRow the text drawn at the anchor, empty if none
	 * @param rows the visual lines drawn below the anchor's line
	 */
	public record MiningSpec(Kind kind, int offset, int length, String firstRow, List<String> rows) {
	}

	/** The maximum number of characters of the first line that are drawn. */
	public static final int MAX_FIRST_ROW_LENGTH = 2000;
	/** The maximum number of further lines that are drawn. */
	public static final int MAX_ROWS = 30;

	private static final Pattern LINE_DELIMITER = Pattern.compile("\r\n|\r|\n"); //$NON-NLS-1$

	private GhostTextLayout() {
	}

	/**
	 * @param document the document
	 * @param ghost the ghost text to lay out
	 * @param tabWidth the editor's tab width, in characters
	 * @param isLineVisible tells whether a model line is visible (not folded away)
	 * @param firstRowOnly whether to draw only the first line, e.g. while the completion popup covers
	 *            the lines below
	 * @return the minings to create, in document order
	 */
	public static List<MiningSpec> layout(IDocument document, GhostText ghost, int tabWidth, IntPredicate isLineVisible,
			boolean firstRowOnly) throws BadLocationException {
		final String[] lines = LINE_DELIMITER.split(ghost.text(), -1);
		final int anchor = ghost.anchor();
		final int anchorLine = document.getLineOfOffset(anchor);
		final IRegion anchorLineInfo = document.getLineInformation(anchorLine);
		final String prefix = expandTabs(document.get(anchorLineInfo.getOffset(), anchor - anchorLineInfo.getOffset()), 0, tabWidth);

		String firstRow = sanitize(expandTabs(lines[0], prefix.length(), tabWidth));
		if (firstRow.length() > MAX_FIRST_ROW_LENGTH) {
			firstRow = firstRow.substring(0, MAX_FIRST_ROW_LENGTH) + '\u2026';
		}

		// the lines below the anchor's line
		final var rows = new ArrayList<String>();
		if (!firstRowOnly && lines.length > 1) {
			addRows(lines, tabWidth, rows);
			rows.replaceAll(row -> row.isEmpty() ? " " : row); //$NON-NLS-1$
		}

		final var result = new ArrayList<MiningSpec>(2);
		final int documentLength = document.getLength();
		if (anchor == documentLength) {
			result.add(new MiningSpec(Kind.END_OF_DOCUMENT, documentLength, 0, firstRow, rows));
			return result;
		}
		if (!firstRow.isEmpty()) {
			result.add(new MiningSpec(Kind.CONTENT, anchor, 1, firstRow, List.of()));
		}
		if (!rows.isEmpty()) {
			int line = anchorLine + 1;
			while (line < document.getNumberOfLines() && !isLineVisible.test(line)) {
				line++;
			}
			final int lineOffset = line < document.getNumberOfLines() ? document.getLineOffset(line) : documentLength;
			if (lineOffset == documentLength) {
				// no visible line below, or only the empty last line: draw below the end of the document
				result.add(new MiningSpec(Kind.END_OF_DOCUMENT, documentLength, 0, "", rows)); //$NON-NLS-1$
			} else {
				result.add(new MiningSpec(Kind.HEADER, lineOffset, 1, "", rows)); //$NON-NLS-1$
			}
		}
		return result;
	}

	/**
	 * Adds the lines of the ghost text after the first to the given rows, the last one summing up those
	 * past {@link #MAX_ROWS}.
	 */
	private static void addRows(String[] lines, int tabWidth, List<String> rows) {
		final int count = lines.length - 1;
		final int shown = count > MAX_ROWS ? MAX_ROWS - 1 : count;
		for (int i = 1; i <= shown; i++) {
			rows.add(sanitize(expandTabs(lines[i], 0, tabWidth)));
		}
		if (shown < count) {
			rows.add(NLS.bind(Messages.InlineCompletion_moreLines, count - shown));
		}
	}

	/** Returns the visual column reached after the given text, starting at column 0. */
	public static int visualColumn(String text, int tabWidth) {
		int column = 0;
		for (int i = 0; i < text.length(); i++) {
			column = text.charAt(i) == '\t' ? nextTabStop(column, tabWidth) : column + 1;
		}
		return column;
	}

	/** Replaces tabs with spaces up to the next tab stop, for text starting at the given visual column. */
	public static String expandTabs(String text, int startColumn, int tabWidth) {
		if (text.indexOf('\t') < 0) {
			return text;
		}
		final var result = new StringBuilder(text.length() + 8);
		int column = startColumn;
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (c == '\t') {
				final int next = nextTabStop(column, tabWidth);
				result.repeat(' ', next - column);
				column = next;
			} else {
				result.append(c);
				column++;
			}
		}
		return result.toString();
	}

	private static int nextTabStop(int column, int tabWidth) {
		return tabWidth <= 0 ? column + 1 : (column / tabWidth + 1) * tabWidth;
	}

	/**
	 * Replaces control characters with their visible Unicode control pictures and bidirectional
	 * formatting characters with U+FFFD, so that the preview cannot hide or reorder what is inserted.
	 */
	public static String sanitize(String text) {
		StringBuilder result = null;
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			final char replacement;
			if (c < 0x20) {
				replacement = (char) (0x2400 + c);
			} else if (c == 0x7F) {
				replacement = '\u2421';
			} else if ((c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069')) {
				replacement = '\uFFFD';
			} else {
				if (result != null) {
					result.append(c);
				}
				continue;
			}
			if (result == null) {
				result = new StringBuilder(text.length()).append(text, 0, i);
			}
			result.append(replacement);
		}
		return result != null ? result.toString() : text;
	}
}
