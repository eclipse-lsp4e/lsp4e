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

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.osgi.util.NLS;

/**
 * Decides which code minings render a {@link GhostText}, where they go and what they draw.
 * <ul>
 * <li>The first line of the ghost text is drawn at its anchor by a line content mining, which must
 * cover one character: the character after the anchor, or the {@code \r} of a CRLF delimiter (after
 * the moved text, see below).</li>
 * <li>Further lines are drawn by a line header mining at the start of the next visible line.</li>
 * <li>At the end of the document, where there is neither a character to cover nor a next line, one
 * mining of zero length draws everything.</li>
 * </ul>
 * <p>
 * A multi-line suggestion inserted before the rest of a line moves that rest after its last line (see
 * {@link #movedText(IDocument, GhostText, List)}). The editor hides the rest where it is, the first
 * line is drawn on the line delimiter after it, and the rest is drawn at the end of the last line in
 * the editor's style rather than the ghost text's (see {@link MiningSpec#moved()}).
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
	 * @param moved the number of characters at the end of the rows that are the moved text, drawn in the
	 *            editor's style
	 * @param movedColumn the column at which the moved text starts, from which its tabs are expanded
	 */
	public record MiningSpec(Kind kind, int offset, int length, String firstRow, List<String> rows, int moved,
			int movedColumn) {

		public MiningSpec(Kind kind, int offset, int length, String firstRow, List<String> rows) {
			this(kind, offset, length, firstRow, rows, 0, 0);
		}
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
		final IRegion rest = restOfLine(document, anchor);
		final String restText = rest == null ? null : document.get(rest.getOffset(), rest.getLength());

		// the lines below the anchor's line, the last one ending with the moved text
		final var rows = new ArrayList<String>();
		int movedLength = 0;
		int movedColumn = 0;
		if (!firstRowOnly && lines.length > 1) {
			addRows(lines, tabWidth, restText != null, rows);
			if (restText != null) {
				final int last = rows.size() - 1;
				movedColumn = rows.get(last).length();
				final String movedRow = movedRow(restText, movedColumn, tabWidth);
				rows.set(last, rows.get(last) + movedRow);
				movedLength = movedRow.length();
			}
			rows.replaceAll(row -> row.isEmpty() ? " " : row); //$NON-NLS-1$
		}

		final var result = new ArrayList<MiningSpec>(2);
		final int documentLength = document.getLength();
		// the moved text is hidden: the first line is drawn where it ends, on the line delimiter
		final int inlineOffset = rest == null || movedLength == 0 ? anchor : rest.getOffset() + rest.getLength();
		if (inlineOffset == documentLength) {
			result.add(new MiningSpec(Kind.END_OF_DOCUMENT, documentLength, 0, firstRow, rows, movedLength, movedColumn));
			return result;
		}
		if (!firstRow.isEmpty()) {
			result.add(new MiningSpec(Kind.CONTENT, inlineOffset, 1, firstRow, List.of()));
		}
		if (!rows.isEmpty()) {
			int line = anchorLine + 1;
			while (line < document.getNumberOfLines() && !isLineVisible.test(line)) {
				line++;
			}
			final int lineOffset = line < document.getNumberOfLines() ? document.getLineOffset(line) : documentLength;
			if (lineOffset == documentLength) {
				// no visible line below, or only the empty last line: draw below the end of the document
				result.add(new MiningSpec(Kind.END_OF_DOCUMENT, documentLength, 0, "", rows, movedLength, movedColumn)); //$NON-NLS-1$
			} else {
				result.add(new MiningSpec(Kind.HEADER, lineOffset, 1, "", rows, movedLength, movedColumn)); //$NON-NLS-1$
			}
		}
		return result;
	}

	/**
	 * Returns the text that accepting the ghost text moves from the anchor's line to the end of its last
	 * line, as the given layout draws it, {@code null} if none: the rest of the anchor's line when the
	 * ghost text takes several rows.
	 */
	public static @Nullable IRegion movedText(IDocument document, GhostText ghost, List<MiningSpec> layout)
			throws BadLocationException {
		return layout.stream().anyMatch(spec -> spec.moved() > 0) ? restOfLine(document, ghost.anchor()) : null;
	}

	private static @Nullable IRegion restOfLine(IDocument document, int offset) throws BadLocationException {
		final IRegion line = document.getLineInformationOfOffset(offset);
		final int lineEnd = line.getOffset() + line.getLength();
		return lineEnd > offset ? new Region(offset, lineEnd - offset) : null;
	}

	/**
	 * Adds the lines of the ghost text after the first to the given rows, the last one summing up those
	 * past {@link #MAX_ROWS}, followed by a space when the moved text follows it.
	 */
	private static void addRows(String[] lines, int tabWidth, boolean movedFollows, List<String> rows) {
		final int count = lines.length - 1;
		final int shown = count > MAX_ROWS ? MAX_ROWS - 1 : count;
		for (int i = 1; i <= shown; i++) {
			rows.add(sanitize(expandTabs(lines[i], 0, tabWidth)));
		}
		if (shown < count) {
			final String summary = NLS.bind(Messages.InlineCompletion_moreLines, count - shown);
			rows.add(movedFollows ? summary + ' ' : summary);
		}
	}

	private static String movedRow(String movedText, int startColumn, int tabWidth) {
		final String row = sanitize(expandTabs(movedText, startColumn, tabWidth));
		return row.length() > MAX_FIRST_ROW_LENGTH ? row.substring(0, MAX_FIRST_ROW_LENGTH) + '\u2026' : row;
	}

	/**
	 * Returns, for each character drawn for the moved text from the given column (see
	 * {@link MiningSpec#moved()}), the index in the moved text of the character it shows, or -1 for the
	 * truncation mark.
	 */
	public static int[] movedRowSources(String movedText, int startColumn, int tabWidth) {
		final var sources = new int[movedRow(movedText, startColumn, tabWidth).length()];
		final int expanded = expandTabs(movedText, startColumn, tabWidth).length();
		final int shown = expanded > MAX_FIRST_ROW_LENGTH ? MAX_FIRST_ROW_LENGTH : expanded;
		int column = startColumn;
		int drawn = 0;
		for (int i = 0; i < movedText.length() && drawn < shown; i++) {
			final int next = movedText.charAt(i) == '\t' ? nextTabStop(column, tabWidth) : column + 1;
			for (; column < next && drawn < shown; column++) {
				sources[drawn++] = i;
			}
		}
		for (; drawn < sources.length; drawn++) {
			sources[drawn] = -1;
		}
		return sources;
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
