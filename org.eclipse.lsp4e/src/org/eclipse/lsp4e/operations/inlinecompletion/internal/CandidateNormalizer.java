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
import java.util.OptionalInt;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.StringValue;
import org.eclipse.lsp4j.StringValueKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

/**
 * Turns {@code textDocument/inlineCompletion} responses into {@link InlineCompletionCandidate}s,
 * dropping items that cannot be shown.
 */
public final class CandidateNormalizer {

	/** The maximum number of candidates kept from all responses of one request. */
	public static final int MAX_CANDIDATES = 20;

	private static final Pattern LINE_DELIMITER = Pattern.compile("\r\n|\r|\n"); //$NON-NLS-1$

	private CandidateNormalizer() {
	}

	/**
	 * Converts a response into candidates, in response order.
	 *
	 * @param response the response, which may be {@code null}
	 * @param document the document, in the state the request was made for
	 * @param requestOffset the offset the request was made at
	 * @param source the server that answered
	 * @param expandSnippet expands snippet text into the plain text it inserts
	 */
	public static List<InlineCompletionCandidate> normalize(
			@Nullable Either<List<InlineCompletionItem>, InlineCompletionList> response, IDocument document,
			int requestOffset, @Nullable LanguageServerWrapper source, UnaryOperator<String> expandSnippet) {
		final var result = new ArrayList<InlineCompletionCandidate>();
		if (response == null) {
			return result;
		}
		final List<InlineCompletionItem> items = response.isLeft() ? response.getLeft() : response.getRight().getItems();
		if (items == null) {
			return result;
		}
		for (final InlineCompletionItem item : items) {
			if (item == null) {
				continue;
			}
			final InlineCompletionCandidate candidate = toCandidate(item, document, requestOffset, source, expandSnippet);
			if (candidate != null) {
				result.add(candidate);
			}
		}
		return result;
	}

	/**
	 * Adds the candidates to the list unless they duplicate one already in it, keeping at most
	 * {@link #MAX_CANDIDATES}.
	 */
	public static void addDistinct(List<InlineCompletionCandidate> list, List<InlineCompletionCandidate> toAdd) {
		for (final InlineCompletionCandidate candidate : toAdd) {
			if (list.size() >= MAX_CANDIDATES) {
				return;
			}
			if (list.stream().noneMatch(candidate::isDuplicateOf)) {
				list.add(candidate);
			}
		}
	}

	/**
	 * Converts one item into a candidate.
	 *
	 * @return the candidate, or {@code null} if the item cannot be shown: it has no text, its range is
	 *         invalid, does not lie on one line or does not contain the request offset, or applying it
	 *         would not change the document
	 */
	public static @Nullable InlineCompletionCandidate toCandidate(InlineCompletionItem item, IDocument document,
			int requestOffset, @Nullable LanguageServerWrapper source, UnaryOperator<String> expandSnippet) {
		try {
			final Either<String, StringValue> insertText = item.getInsertText();
			if (insertText == null) {
				return null;
			}
			String text;
			String snippet = null;
			if (insertText.isLeft()) {
				text = insertText.getLeft();
			} else {
				final StringValue value = insertText.getRight();
				if (value == null || value.getValue() == null) {
					return null;
				}
				if (StringValueKind.SNIPPET.equals(value.getKind())) {
					snippet = value.getValue();
				}
				text = value.getValue();
			}
			if (text == null || text.isEmpty()) {
				return null;
			}

			final int requestLine = document.getLineOfOffset(requestOffset);
			final String delimiter = lineDelimiter(document, requestLine);
			text = normalizeLineDelimiters(text, delimiter);
			if (snippet != null) {
				snippet = normalizeLineDelimiters(snippet, delimiter);
				text = expandSnippet.apply(snippet);
				if (text.isEmpty()) {
					return null;
				}
			}

			int start = requestOffset;
			int end = requestOffset;
			final Range range = item.getRange();
			if (range != null) {
				Position rangeStart = range.getStart();
				Position rangeEnd = range.getEnd();
				if (rangeStart == null || rangeEnd == null) {
					return null;
				}
				if (compare(rangeStart, rangeEnd) > 0) {
					final Position swap = rangeStart;
					rangeStart = rangeEnd;
					rangeEnd = swap;
				}
				if (rangeStart.getLine() != rangeEnd.getLine() || rangeStart.getLine() != requestLine) {
					return null;
				}
				final OptionalInt startOffset = strictOffset(rangeStart, document);
				final OptionalInt endOffset = strictOffset(rangeEnd, document);
				if (startOffset.isEmpty() || endOffset.isEmpty()) {
					return null;
				}
				start = startOffset.getAsInt();
				end = endOffset.getAsInt();
				if (start > requestOffset || end < requestOffset) {
					return null;
				}
			}
			if (text.equals(document.get(start, end - start))) {
				return null;
			}

			final String filterText = item.getFilterText();
			return new InlineCompletionCandidate(source, start, end, text, snippet,
					filterText == null || filterText.isEmpty() ? text : filterText, item.getCommand());
		} catch (BadLocationException e) {
			return null;
		}
	}

	/**
	 * Converts an LSP position into a document offset without ever leaving its line: a character past
	 * the end of the line is clamped to the end of the line, before its delimiter.
	 *
	 * @return the offset, or empty if the line does not exist, the character is negative or the offset
	 *         would split a surrogate pair
	 */
	public static OptionalInt strictOffset(Position position, IDocument document) throws BadLocationException {
		final int line = position.getLine();
		if (line < 0 || line >= document.getNumberOfLines() || position.getCharacter() < 0) {
			return OptionalInt.empty();
		}
		final IRegion lineInfo = document.getLineInformation(line);
		final int offset = lineInfo.getOffset() + Math.min(position.getCharacter(), lineInfo.getLength());
		if (offset > 0 && offset < document.getLength() && Character.isLowSurrogate(document.getChar(offset))
				&& Character.isHighSurrogate(document.getChar(offset - 1))) {
			return OptionalInt.empty();
		}
		return OptionalInt.of(offset);
	}

	/** Returns the delimiter of the given line, or the document's default delimiter for the last line. */
	public static String lineDelimiter(IDocument document, int line) throws BadLocationException {
		final String delimiter = document.getLineDelimiter(line);
		return delimiter != null ? delimiter : TextUtilities.getDefaultLineDelimiter(document);
	}

	/** Replaces every line delimiter in the text with the given one. */
	public static String normalizeLineDelimiters(String text, String delimiter) {
		return LINE_DELIMITER.matcher(text).replaceAll(Matcher.quoteReplacement(delimiter));
	}

	private static int compare(Position a, Position b) {
		return a.getLine() != b.getLine() ? Integer.compare(a.getLine(), b.getLine())
				: Integer.compare(a.getCharacter(), b.getCharacter());
	}
}
