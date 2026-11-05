/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.inlinecompletion;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.CandidateNormalizer;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.EditKind;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostText;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextComputer;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextLayout;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextLayout.Kind;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextLayout.MiningSpec;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionCandidate;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionSession;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.KeyPolicy;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.TrackedRange;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionList;
import org.eclipse.lsp4j.InlineCompletionTriggerKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.StringValue;
import org.eclipse.lsp4j.StringValueKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests of the inline completion model, which decides what ghost text is shown for which
 * document, caret and server items.
 */
public class GhostTextModelTest {

	/** A mining whose last row ends with the given number of characters of moved text. */
	static MiningSpec movedSpec(Kind kind, int offset, int length, String firstRow, List<String> rows, int moved) {
		return new MiningSpec(kind, offset, length, firstRow, rows, moved, rows.get(rows.size() - 1).length() - moved);
	}

	private static final UnaryOperator<String> NO_SNIPPETS = UnaryOperator.identity();

	private static InlineCompletionCandidate candidate(int start, int end, String text) {
		return new InlineCompletionCandidate(null, start, end, text, null, text, null);
	}

	private static InlineCompletionCandidate candidate(int start, int end, String text, String filterText) {
		return new InlineCompletionCandidate(null, start, end, text, null, filterText, null);
	}

	private static @Nullable GhostText ghost(String content, int caret, int start, int end, InlineCompletionCandidate c) {
		return GhostTextComputer.compute(new Document(content), caret, new TrackedRange(start, end), c);
	}

	private static InlineCompletionItem item(String text, @Nullable Range range) {
		final var item = new InlineCompletionItem();
		item.setInsertText(text);
		item.setRange(range);
		return item;
	}

	private static Range range(int startLine, int startChar, int endLine, int endChar) {
		return new Range(new Position(startLine, startChar), new Position(endLine, endChar));
	}

	private static @Nullable InlineCompletionCandidate normalize(String content, int requestOffset, InlineCompletionItem item) {
		return CandidateNormalizer.toCandidate(item, new Document(content), requestOffset, null, NO_SNIPPETS);
	}

	@Nested
	class Computer {

		@Test
		void insertionAtTheCaret() {
			final GhostText g = ghost("hello", 5, 5, 5, candidate(5, 5, " world"));
			assertNotNull(g);
			assertEquals(5, g.anchor());
			assertEquals(" world", g.text());
		}

		@Test
		void typedTextIsRemovedFromTheGhost() {
			// the user typed " w" since the request at offset 5; the tracked range grew over it
			final GhostText g = ghost("hello w", 7, 5, 7, candidate(5, 5, " world"));
			assertNotNull(g);
			assertEquals("orld", g.text());
			assertEquals(5, g.replaceStart());
		}

		@Test
		void typedTextNotMatchingHidesTheGhost() {
			assertNull(ghost("hello x", 7, 5, 7, candidate(5, 5, " world")));
		}

		@Test
		void fullyTypedGhostIsNotShown() {
			assertNull(ghost("hello world", 11, 5, 11, candidate(5, 5, " world")));
		}

		@Test
		void replacementRangeStartingBeforeTheCaret() {
			// the LSP specification's example: "console." with an item replacing "." by ".log()"
			final GhostText g = ghost("console.", 8, 7, 8, candidate(7, 8, ".log()"));
			assertNotNull(g);
			assertEquals("log()", g.text());
			assertEquals(8, g.anchor());
		}

		@Test
		void backspaceInsideTheRangeGrowsTheGhostBack() {
			final InlineCompletionCandidate c = candidate(0, 2, "console");
			assertEquals("nsole", ghost("co", 2, 0, 2, c).text());
			assertEquals("onsole", ghost("c", 1, 0, 1, c).text());
		}

		@Test
		void textAfterTheCaretInsideTheRangeThatEndsTheSuggestion() {
			// "print(|)" with an auto-closed parenthesis inside the range
			final GhostText g = ghost("print()", 6, 6, 7, candidate(6, 7, "\"hi\")"));
			assertNotNull(g);
			assertEquals("\"hi\"", g.text());
			assertEquals(6, g.anchor());
			assertEquals(7, g.replaceEnd());
		}

		@Test
		void textAfterTheCaretInsideTheRangeThatStartsTheSuggestion() {
			// "|    x": the range covers existing indentation that the suggestion keeps
			final GhostText g = ghost("    x", 0, 0, 4, candidate(0, 4, "    return x"));
			assertNotNull(g);
			assertEquals("return x", g.text());
			assertEquals(4, g.anchor());
		}

		@Test
		void textAfterTheCaretThatTheSuggestionReplacesIsNotShown() {
			assertNull(ghost("foo(bar)", 4, 4, 8, candidate(4, 8, "baz)")));
		}

		@Test
		void filterTextDecidesVisibility() {
			// typed "foo(" is not a prefix of the filter text "foo"
			assertNull(ghost("foo(", 4, 0, 4, candidate(0, 0, "foo()", "foo")));
			// typed "bar" matches the filter text, but the text does not start with it
			assertNull(ghost("bar", 3, 0, 3, candidate(0, 0, "foo", "barfoo")));
			assertEquals("o()", ghost("fo", 2, 0, 2, candidate(0, 0, "foo()", "foo")).text());
		}

		@Test
		void matchingIsCaseSensitive() {
			assertNull(ghost("Hel", 3, 0, 3, candidate(0, 0, "hello")));
		}

		@Test
		void caretOutsideTheRangeHidesTheGhost() {
			assertNull(ghost("abc def", 1, 4, 4, candidate(4, 4, "x")));
			assertNull(ghost("abc def", 6, 4, 5, candidate(4, 5, "dx")));
		}

		@Test
		void surrogatePairs() {
			final GhostText g = ghost("a😀", 3, 1, 3, candidate(1, 1, "😀b😁"));
			assertNotNull(g);
			assertEquals("b😁", g.text());
		}

		@Test
		void selectedCompletionMustBeExtended() {
			final InlineCompletionCandidate c = candidate(7, 8, ".log()");
			assertTrue(GhostTextComputer.extendsSelectedCompletion(c, 7, 8, ".log"));
			assertFalse(GhostTextComputer.extendsSelectedCompletion(c, 7, 8, ".error"));
			assertFalse(GhostTextComputer.extendsSelectedCompletion(c, 8, 8, ".log"));
		}
	}

	@Nested
	class Normalizer {

		@Test
		void nullAndEmptyResponses() {
			final var doc = new Document("x");
			assertTrue(CandidateNormalizer.normalize(null, doc, 1, null, NO_SNIPPETS).isEmpty());
			assertTrue(CandidateNormalizer.normalize(Either.forLeft(List.of()), doc, 1, null, NO_SNIPPETS).isEmpty());
			assertTrue(CandidateNormalizer.normalize(Either.forRight(new InlineCompletionList(List.of())), doc, 1, null, NO_SNIPPETS).isEmpty());
		}

		@Test
		void arrayAndListResponsesAreEquivalent() {
			final var doc = new Document("x");
			final List<InlineCompletionItem> items = List.of(item("yz", null));
			assertEquals(CandidateNormalizer.normalize(Either.forLeft(items), doc, 1, null, NO_SNIPPETS),
					CandidateNormalizer.normalize(Either.forRight(new InlineCompletionList(items)), doc, 1, null, NO_SNIPPETS));
		}

		@Test
		void itemsWithoutTextAreDropped() {
			final var doc = new Document("x");
			final var items = new ArrayList<InlineCompletionItem>();
			items.add(null);
			items.add(new InlineCompletionItem());
			items.add(item("", null));
			final var noValue = new InlineCompletionItem();
			final var nullValue = new StringValue();
			nullValue.setKind(StringValueKind.SNIPPET);
			noValue.setInsertText(nullValue);
			items.add(noValue);
			assertTrue(CandidateNormalizer.normalize(Either.forLeft(items), doc, 1, null, NO_SNIPPETS).isEmpty());
		}

		@Test
		void itemsNotChangingTheDocumentAreDropped() {
			assertNull(normalize("foo", 3, item("foo", range(0, 0, 0, 3))));
		}

		@Test
		void missingRangeIsAnInsertionAtTheRequestOffset() {
			final InlineCompletionCandidate c = normalize("abc", 2, item("x", null));
			assertNotNull(c);
			assertEquals(2, c.start());
			assertEquals(2, c.end());
		}

		@Test
		void invalidRangesAreDropped() {
			final String content = "abc\ndef";
			// spans two lines
			assertNull(normalize(content, 2, item("x", range(0, 0, 1, 1))));
			// on another line than the request
			assertNull(normalize(content, 2, item("x", range(1, 0, 1, 1))));
			// does not contain the request offset
			assertNull(normalize(content, 1, item("x", range(0, 2, 0, 3))));
			// line out of bounds
			assertNull(normalize(content, 1, item("x", range(5, 0, 5, 1))));
		}

		@Test
		void reversedRangeIsSwapped() {
			final InlineCompletionCandidate c = normalize("abcdef", 3, item("xyz", range(0, 4, 0, 2)));
			assertNotNull(c);
			assertEquals(2, c.start());
			assertEquals(4, c.end());
		}

		@Test
		void characterBeyondTheLineEndIsClampedBeforeTheDelimiter() {
			for (final String delimiter : List.of("\n", "\r\n")) {
				final InlineCompletionCandidate c = normalize("abc" + delimiter + "def", 3, item("xyz", range(0, 1, 0, 99)));
				assertNotNull(c);
				assertEquals(3, c.end(), "range end with delimiter " + delimiter.length());
			}
		}

		@Test
		void rangeSplittingASurrogatePairIsDropped() {
			assertNull(normalize("a😀", 3, item("x", range(0, 2, 0, 3))));
		}

		@Test
		void lineDelimitersAreNormalizedToTheDocument() {
			assertEquals("x\r\ny\r\nz", normalize("a\r\nb", 1, item("x\ny\rz", null)).text());
			assertEquals("x\ny", normalize("a\nb", 1, item("x\r\ny", null)).text());
		}

		@Test
		void theCaretLinesDelimiterWins() {
			assertEquals("x\ry", normalize("a\rb\nc", 1, item("x\ny", null)).text());
			// on the last line, which has no delimiter, the document's default delimiter is used
			assertEquals("x\r\ny", normalize("a\r\nb", 4, item("x\ny", null)).text());
		}

		@Test
		void snippetsArePreviewedExpanded() {
			final var snippetItem = new InlineCompletionItem();
			snippetItem.setInsertText(new StringValue(StringValueKind.SNIPPET, "f(${1:a}, $2)$0"));
			final InlineCompletionCandidate c = CandidateNormalizer.toCandidate(snippetItem, new Document(""), 0, null,
					snippet -> "f(a, )");
			assertNotNull(c);
			assertEquals("f(a, )", c.text());
			assertEquals("f(${1:a}, $2)$0", c.snippet());
			// without filter text, the expanded text filters
			assertEquals("f(a, )", c.filterText());
		}

		@Test
		void unknownStringValueKindIsPlainText() {
			final var other = new InlineCompletionItem();
			other.setInsertText(new StringValue("markdown", "*x*"));
			final InlineCompletionCandidate c = normalize("", 0, other);
			assertNotNull(c);
			assertEquals("*x*", c.text());
			assertNull(c.snippet());
		}

		@Test
		void emptyFilterTextFallsBackToTheText() {
			final InlineCompletionItem i = item("abc", null);
			i.setFilterText("");
			assertEquals("abc", normalize("", 0, i).filterText());
			i.setFilterText("ab");
			assertEquals("ab", normalize("", 0, i).filterText());
		}

		@Test
		void duplicatesAreSkippedAndTheNumberIsLimited() {
			final var list = new ArrayList<InlineCompletionCandidate>();
			CandidateNormalizer.addDistinct(list, List.of(candidate(0, 0, "a"), candidate(0, 0, "a"), candidate(0, 1, "a")));
			assertEquals(2, list.size());
			final var many = new ArrayList<InlineCompletionCandidate>();
			for (int i = 0; i < 50; i++) {
				many.add(candidate(0, 0, "x" + i));
			}
			CandidateNormalizer.addDistinct(list, many);
			assertEquals(CandidateNormalizer.MAX_CANDIDATES, list.size());
			assertEquals("x0", list.get(2).text());
		}
	}

	@Nested
	class Ranges {

		@ParameterizedTest(name = "[{0},{1}] replace({2},{3},{4}) -> {5}")
		@CsvSource({
				// insertion at the start of an empty range: the range grows over the typed text
				"5,5, 5,0,1, 5:6",
				// insertion at the end: grows (right-sticky)
				"5,8, 8,0,2, 5:10",
				// insertion at the start: start stays (left-sticky)
				"5,8, 5,0,1, 5:9",
				// before the range: shifted
				"5,8, 3,0,2, 7:10", "5,8, 3,2,0, 3:6",
				// after the range: unchanged
				"5,8, 9,0,1, 5:8", "5,8, 8,1,0, 5:8",
				// inside: resized
				"5,8, 6,1,0, 5:7", "5,8, 6,1,3, 5:10",
				// across the end: cut
				"5,8, 7,3,0, 5:7",
				// across the start: lost
				"5,8, 4,2,0, null" })
		void trackedRange(int start, int end, int offset, int deleted, int inserted, String expected) {
			final TrackedRange result = new TrackedRange(start, end).apply(offset, deleted, inserted);
			assertEquals(expected, result == null ? "null" : result.start() + ":" + result.end());
		}
	}

	@Nested
	class Layout {

		private List<MiningSpec> layout(String content, int anchor, String text) throws Exception {
			return layout(content, anchor, text, 4, line -> true, false);
		}

		private List<MiningSpec> layout(String content, int anchor, String text, int tabWidth,
				IntPredicate visible, boolean firstRowOnly) throws Exception {
			final IDocument doc = new Document(content);
			return GhostTextLayout.layout(doc, new GhostText(anchor, text, anchor, anchor, candidate(anchor, anchor, text)),
					tabWidth, visible, firstRowOnly, null);
		}

		private static void assertSpec(MiningSpec spec, Kind kind, int offset, int length, String firstRow, List<String> rows) {
			assertEquals(kind, spec.kind());
			assertEquals(offset, spec.offset(), "offset");
			assertEquals(length, spec.length(), "length");
			assertEquals(firstRow, spec.firstRow());
			assertEquals(rows, spec.rows());
		}

		@ParameterizedTest(name = "{0}")
		@CsvSource({
				"start of a line,      foo,       0",
				"middle of a line,     foo,       1",
				"end of a line,        'ab\\ncd',  2",
				"empty line,           'a\\n\\nb', 2",
				"whitespace-only line, '    \\nb', 4" })
		void singleLineGhostIsOneMiningAtTheCaret(String where, String content, int anchor) throws Exception {
			final List<MiningSpec> specs = layout(content.translateEscapes(), anchor, "x");
			assertEquals(1, specs.size(), where);
			assertSpec(specs.get(0), Kind.CONTENT, anchor, 1, "x", List.of());
		}

		@Test
		void atTheEndOfACrlfLineTheMiningCoversTheCarriageReturn() throws Exception {
			final List<MiningSpec> crlf = layout("ab\r\ncd", 2, "x");
			assertSpec(crlf.get(0), Kind.CONTENT, 2, 1, "x", List.of());
			assertEquals('\r', new Document("ab\r\ncd").getChar(crlf.get(0).offset()));
		}

		@Test
		void multiLineGhostContinuesAboveTheNextLine() throws Exception {
			final List<MiningSpec> specs = layout("a\nb", 1, "x\ny\nz");
			assertEquals(2, specs.size());
			assertSpec(specs.get(0), Kind.CONTENT, 1, 1, "x", List.of());
			assertSpec(specs.get(1), Kind.HEADER, 2, 1, "", List.of("y", "z"));
		}

		@Test
		void endOfDocumentIsOneMining() throws Exception {
			assertEquals(List.of(new MiningSpec(Kind.END_OF_DOCUMENT, 3, 0, "x", List.of())), layout("abc", 3, "x"));
			final List<MiningSpec> specs = layout("abc", 3, "x\ny\nz");
			assertEquals(1, specs.size());
			assertSpec(specs.get(0), Kind.END_OF_DOCUMENT, 3, 0, "x", List.of("y", "z"));
			// the caret on the final empty line
			assertEquals(List.of(new MiningSpec(Kind.END_OF_DOCUMENT, 2, 0, "x", List.of("y"))), layout("a\n", 2, "x\ny"));
		}

		@Test
		void lastLineMiddleMultiLine() throws Exception {
			// "b", after the caret, moves after the last line: hidden, the first line is drawn after it
			assertEquals(List.of(new MiningSpec(Kind.END_OF_DOCUMENT, 2, 0, "x", List.of("yb"), 1, 1)), layout("ab", 1, "x\ny"));
		}

		@Test
		void nextLineIsTheEmptyLastLine() throws Exception {
			final List<MiningSpec> specs = layout("a\n", 1, "x\ny");
			assertEquals(2, specs.size());
			assertSpec(specs.get(0), Kind.CONTENT, 1, 1, "x", List.of());
			assertSpec(specs.get(1), Kind.END_OF_DOCUMENT, 2, 0, "", List.of("y"));
		}

		@Test
		void emptyDocument() throws Exception {
			assertSpec(layout("", 0, "x\ny").get(0), Kind.END_OF_DOCUMENT, 0, 0, "x", List.of("y"));
		}

		@Test
		void tabsAreExpandedFromTheCaretColumn() throws Exception {
			// the caret is after a tab (column 4); "a" is at column 4, the tab after it reaches column 8
			final List<MiningSpec> specs = layout("\t\nz", 1, "a\tb\n\tc", 4, line -> true, false);
			assertSpec(specs.get(0), Kind.CONTENT, 1, 1, "a   b", List.of());
			assertSpec(specs.get(1), Kind.HEADER, 2, 1, "", List.of("    c"));
			assertEquals("ab  c", GhostTextLayout.expandTabs("ab\tc", 0, 4));
			assertEquals(6, GhostTextLayout.visualColumn("\tab", 4));
		}

		@Test
		void blankRowsAreKept() throws Exception {
			final List<MiningSpec> specs = layout("a\nb", 1, "x\n\ny\n");
			assertSpec(specs.get(1), Kind.HEADER, 2, 1, "", List.of(" ", "y", " "));
		}

		@Test
		void ghostStartingWithANewlineHasNoInlinePart() throws Exception {
			final List<MiningSpec> specs = layout("a\nb", 1, "\ny");
			assertEquals(1, specs.size());
			assertSpec(specs.get(0), Kind.HEADER, 2, 1, "", List.of("y"));
		}

		@Test
		void hugeSuggestionsAreTruncated() throws Exception {
			final String text = "x".repeat(5000) + "\nline".repeat(40);
			final List<MiningSpec> specs = layout("a\nb", 1, text);
			assertEquals(GhostTextLayout.MAX_FIRST_ROW_LENGTH + 1, specs.get(0).firstRow().length());
			final List<String> rows = specs.get(1).rows();
			assertEquals(GhostTextLayout.MAX_ROWS, rows.size());
			assertEquals("… +11 more lines", rows.get(rows.size() - 1));
		}

		@Test
		void foldedNextLineIsSkipped() throws Exception {
			final List<MiningSpec> specs = layout("a\nb\nc", 1, "x\ny", 4, line -> line != 1, false);
			assertSpec(specs.get(1), Kind.HEADER, 4, 1, "", List.of("y"));
			final List<MiningSpec> allFolded = layout("a\nb\nc", 1, "x\ny", 4, line -> line == 0, false);
			assertSpec(allFolded.get(1), Kind.END_OF_DOCUMENT, 5, 0, "", List.of("y"));
		}

		private @Nullable IRegion moved(String content, int anchor, String text, boolean firstRowOnly) throws Exception {
			final IDocument doc = new Document(content);
			final var ghost = new GhostText(anchor, text, anchor, anchor, candidate(anchor, anchor, text));
			return GhostTextLayout.movedText(doc, ghost, GhostTextLayout.layout(doc, ghost, 4, line -> true, firstRowOnly, null));
		}

		@Test
		void inTheMiddleOfALineTheRestOfTheLineEndsTheLastRow() throws Exception {
			// accepting "x,\n  y, " in "call(|arg)" gives "call(x,\n  y, arg)": "arg)" is hidden and the first
			// line drawn on the line delimiter after it
			assertEquals(List.of(new MiningSpec(Kind.CONTENT, 9, 1, "x,", List.of()),
					movedSpec(Kind.HEADER, 10, 1, "", List.of("  y, arg)"), 4)), layout("call(arg)\nnext", 5, "x,\n  y, "));
			assertEquals(new Region(5, 4), moved("call(arg)\nnext", 5, "x,\n  y, ", false));
		}

		@Test
		void textAfterTheCaretThatTheSuggestionEndsWithEndsTheLastRow() throws Exception {
			// "foo(|)" with "\n  a\n)" over the auto-closed ")": shown without it, which ends the last row
			assertEquals(List.of(movedSpec(Kind.HEADER, 6, 1, "", List.of("  a", ")"), 1)),
					layout("foo()\nz", 4, "\n  a\n"));
		}

		@Test
		void ghostStartingWithANewlineInTheMiddleOfALine() throws Exception {
			assertEquals(List.of(movedSpec(Kind.HEADER, 9, 1, "", List.of("  xbar)"), 4)), layout("foo(bar)\nz", 4, "\n  x"));
		}

		@Test
		void inTheMiddleOfTheLastLineTheRestEndsTheRowsBelowIt() throws Exception {
			assertEquals(List.of(movedSpec(Kind.END_OF_DOCUMENT, 9, 0, "x", List.of("yarg)"), 4)),
					layout("call(arg)", 5, "x\ny"));
		}

		@Test
		void theMovedTextEndsBeforeTheLineDelimiter() throws Exception {
			assertEquals(List.of(new MiningSpec(Kind.CONTENT, 2, 1, "x", List.of()),
					new MiningSpec(Kind.HEADER, 4, 1, "", List.of("yb"), 1, 1)), layout("ab\r\ncd", 1, "x\ny"));
			assertEquals(new Region(1, 1), moved("ab\r\ncd", 1, "x\ny", false));
		}

		@Test
		void tabsInTheMovedTextAreExpandedFromWhereTheLastRowEnds() throws Exception {
			// "  y" ends at column 3: the tab reaches column 4
			assertEquals(List.of("  y b"), layout("a\tb\nz", 1, "x\n  y").get(1).rows());
			assertEquals(2, layout("a\tb\nz", 1, "x\n  y").get(1).moved());
			// each drawn character shows a character of the moved text
			assertArrayEquals(new int[] { 0, 1 }, GhostTextLayout.movedRowSources("\tb", 3, 4));
			assertArrayEquals(new int[] { 0, 0, 0, 1 }, GhostTextLayout.movedRowSources("\tb", 5, 4));
		}

		@Test
		void theMovedTextFollowsTheTruncationRow() throws Exception {
			final List<String> rows = layout("a(b)\nz", 2, "x" + "\nl".repeat(40)).get(1).rows();
			assertEquals(GhostTextLayout.MAX_ROWS, rows.size());
			assertEquals("\u2026 +11 more lines b)", rows.get(rows.size() - 1));
			assertEquals(2, layout("a(b)\nz", 2, "x" + "\nl".repeat(40)).get(1).moved());
		}

		@Test
		void longMovedTextIsTruncated() throws Exception {
			final String row = layout("a" + "b".repeat(3000) + "\nz", 1, "x\ny").get(1).rows().get(0);
			assertEquals("y" + "b".repeat(GhostTextLayout.MAX_FIRST_ROW_LENGTH) + '\u2026', row);
			final int[] sources = GhostTextLayout.movedRowSources("b".repeat(3000), 1, 4);
			assertEquals(GhostTextLayout.MAX_FIRST_ROW_LENGTH + 1, sources.length);
			assertEquals(-1, sources[sources.length - 1], "the ellipsis shows no moved character");
		}

		@Test
		void nothingMovesForSingleLineGhostsAtTheEndOfALineOrWithOnlyTheFirstRow() throws Exception {
			assertNull(moved("foo", 1, "x", false));
			assertNull(moved("ab\ncd", 2, "x\ny", false));
			assertNull(moved("call(arg)\nnext", 5, "x,\n  y, ", true));
			assertNull(moved("abc", 3, "x\ny", false));
		}

		/** Lays out with word wrap at the given number of characters, breaking anywhere. */
		private List<MiningSpec> wrapped(String content, int anchor, String text, int width) throws Exception {
			return GhostTextLayout.layout(new Document(content),
					new GhostText(anchor, text, anchor, anchor, candidate(anchor, anchor, text)), 4, line -> true, false,
					line -> IntStream.iterate(width, i -> i < line.length(), i -> i + width).toArray());
		}

		private @Nullable IRegion movedWrapped(String content, int anchor, String text, int width) throws Exception {
			final var doc = new Document(content);
			final var ghost = new GhostText(anchor, text, anchor, anchor, candidate(anchor, anchor, text));
			return GhostTextLayout.movedText(doc, ghost, wrapped(content, anchor, text, width));
		}

		@Test
		void withWordWrapLongRowsWrap() throws Exception {
			assertEquals(List.of(new MiningSpec(Kind.CONTENT, 1, 1, "x", List.of()),
					new MiningSpec(Kind.HEADER, 2, 1, "", List.of("y".repeat(10), "y".repeat(10), "yyyyy"))),
					wrapped("a\nb", 1, "x\n" + "y".repeat(25), 10));
		}

		@Test
		void withWordWrapAFirstLineLongerThanTheVisualLineGoesOnBelow() throws Exception {
			// "ab" + 8 characters fill the visual line
			assertEquals(List.of(new MiningSpec(Kind.CONTENT, 2, 1, "x".repeat(8), List.of()),
					new MiningSpec(Kind.HEADER, 3, 1, "", List.of("x".repeat(7)))), wrapped("ab\nz", 2, "x".repeat(15), 10));
			assertNull(movedWrapped("ab\nz", 2, "x".repeat(15), 10));
		}

		@Test
		void withWordWrapASingleLineGhostGoingOnBelowMovesTheRestOfTheLine() throws Exception {
			// "call(" + 5 characters fill the visual line: the other 5 and "arg)" go on below, "arg)" is hidden
			assertEquals(List.of(new MiningSpec(Kind.CONTENT, 9, 1, "xxxxx", List.of()),
					new MiningSpec(Kind.HEADER, 10, 1, "", List.of("xxxxxarg)"), 4, 5)),
					wrapped("call(arg)\nz", 5, "x".repeat(10), 10));
			assertEquals(new Region(5, 4), movedWrapped("call(arg)\nz", 5, "x".repeat(10), 10));
		}

		@Test
		void withWordWrapAGhostThatFitsLeavesTheRestOfTheLineWhereItIs() throws Exception {
			// the line wraps in its own text, after the ghost text
			assertEquals(List.of(new MiningSpec(Kind.CONTENT, 5, 1, "x", List.of())),
					wrapped("call(arg, more, and more)\nz", 5, "x", 10));
			assertNull(movedWrapped("call(arg, more, and more)\nz", 5, "x", 10));
		}

		@Test
		void withWordWrapTheMovedTextCanSpanRows() throws Exception {
			assertEquals(List.of(new MiningSpec(Kind.CONTENT, 18, 1, "x", List.of()),
					new MiningSpec(Kind.HEADER, 19, 1, "", List.of("y" + "a".repeat(9), "aaa)"), 13, 1)),
					wrapped("call(" + "a".repeat(12) + ")\nz", 5, "x\ny", 10));
		}

		@Test
		void withWordWrapAGhostStartingTheNextVisualLineHasNoInlinePart() throws Exception {
			assertEquals(List.of(new MiningSpec(Kind.HEADER, 11, 1, "", List.of("xy"))),
					wrapped("0123456789\nz", 10, "xy", 10));
		}

		@Test
		void withWordWrapOnlyTheFirstRowWhileThePopupIsOpen() throws Exception {
			final var text = "x".repeat(15);
			assertEquals(List.of(new MiningSpec(Kind.CONTENT, 2, 1, text, List.of())),
					GhostTextLayout.layout(new Document("ab\nz"), new GhostText(2, text, 2, 2, candidate(2, 2, text)), 4,
							line -> true, true, line -> new int[] { 10 }));
		}

		@Test
		void controlAndBidiCharactersAreShownVisibly() {
			assertEquals("␇a�b␡", GhostTextLayout.sanitize("\u0007a‮b\u007F"));
			assertEquals("plain", GhostTextLayout.sanitize("plain"));
		}

		@Test
		void onlyTheFirstRowWhileThePopupIsOpen() throws Exception {
			final List<MiningSpec> specs = layout("a\nb", 1, "x\ny", 4, line -> true, true);
			assertEquals(1, specs.size());
			assertSpec(specs.get(0), Kind.CONTENT, 1, 1, "x", List.of());
		}
	}

	@Nested
	class Policies {

		@Test
		void editKinds() {
			assertEquals(EditKind.TYPE_AT_CARET, EditKind.classify(3, 3, 0, "a"));
			// paste
			assertEquals(EditKind.TYPE_AT_CARET, EditKind.classify(3, 3, 0, "abc\ndef"));
			assertEquals(EditKind.BACKSPACE_AT_CARET, EditKind.classify(3, 2, 1, ""));
			assertEquals(EditKind.DELETE_AT_CARET, EditKind.classify(3, 3, 1, ""));
			// overwrite mode replaces a character
			assertEquals(EditKind.OTHER, EditKind.classify(3, 3, 1, "a"));
			// away from the caret
			assertEquals(EditKind.OTHER, EditKind.classify(3, 10, 0, "a"));
			assertFalse(EditKind.OTHER.triggersAutomatically());
			assertTrue(EditKind.TYPE_AT_CARET.triggersAutomatically());
		}

		@Test
		void tab() {
			assertTrue(KeyPolicy.tabAccepts(SWT.TAB, 0, true, "foo", "bar", 4));
			// not painted yet: the user has not seen what Tab would insert
			assertFalse(KeyPolicy.tabAccepts(SWT.TAB, 0, false, "foo", "bar", 4));
			// in the indentation with at least a tab's worth of whitespace ahead: Tab indents
			assertFalse(KeyPolicy.tabAccepts(SWT.TAB, 0, true, "", "    return x", 4));
			assertFalse(KeyPolicy.tabAccepts(SWT.TAB, 0, true, "  ", "\treturn x", 4));
			assertTrue(KeyPolicy.tabAccepts(SWT.TAB, 0, true, "", "  return x", 4));
			assertTrue(KeyPolicy.tabAccepts(SWT.TAB, 0, true, "x", "    y", 4));
		}

		@Test
		void modifiedTabIsNeverConsumed() {
			assertFalse(KeyPolicy.tabAccepts(SWT.TAB, SWT.SHIFT, true, "foo", "bar", 4));
			assertFalse(KeyPolicy.tabAccepts(SWT.TAB, SWT.CTRL, true, "foo", "bar", 4));
			assertFalse(KeyPolicy.tabAccepts('a', 0, true, "foo", "bar", 4));
		}
	}

	@Nested
	class Session {

		private final IDocument doc = new Document("hello");

		private InlineCompletionSession session(InlineCompletionCandidate... candidates) {
			final var session = new InlineCompletionSession(InlineCompletionTriggerKind.Invoked, false);
			session.addCandidates(List.of(candidates));
			return session;
		}

		@Test
		void cyclingWrapsAround() {
			final InlineCompletionSession s = session(candidate(5, 5, " a"), candidate(5, 5, " b"), candidate(5, 5, " c"));
			assertEquals(" a", s.current(doc, 5).text());
			assertEquals(" b", s.cycle(doc, 5, true).text());
			assertEquals(" c", s.cycle(doc, 5, true).text());
			assertEquals(" a", s.cycle(doc, 5, true).text());
			assertEquals(" c", s.cycle(doc, 5, false).text());
			assertArrayEquals(new int[] { 3, 3 }, s.position(doc, 5));
		}

		@Test
		void typingThroughKeepsTheCurrentCandidateWhileItMatches() throws Exception {
			final InlineCompletionSession s = session(candidate(5, 5, " world"), candidate(5, 5, " there"));
			doc.replace(5, 0, " t");
			s.documentChanged(5, 0, 2);
			// " world" no longer matches: the next candidate that does becomes current
			assertEquals("here", s.current(doc, 7).text());
			assertArrayEquals(new int[] { 1, 1 }, s.position(doc, 7));
		}

		@Test
		void aChangeBeforeTheRangeStartInvalidatesTheCandidate() throws Exception {
			final InlineCompletionSession s = session(candidate(5, 5, " world"));
			doc.replace(4, 1, "");
			s.documentChanged(4, 1, 0);
			assertNull(s.current(doc, 4));
		}
	}
}
