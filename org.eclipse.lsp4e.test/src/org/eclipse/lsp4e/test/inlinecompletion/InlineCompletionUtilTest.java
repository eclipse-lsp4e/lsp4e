/*******************************************************************************
 * Copyright (c) 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.inlinecompletion;

import static org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionUtil.getReplaceRangeLength;
import static org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionUtil.getWordLength;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionExtensions;
import org.junit.jupiter.api.Test;

public class InlineCompletionUtilTest {

	private static final IWordDetector DEFAULT = InlineCompletionExtensions.DEFAULT_WORD_DETECTOR;

	@Test
	public void testWordLength() {
		assertEquals(3, getWordLength("foo bar", DEFAULT));
		assertEquals(1, getWordLength("foo bar", 3, DEFAULT));
		assertEquals(3, getWordLength("foo   bar", 3, DEFAULT));
		assertEquals(2, getWordLength("my#var", DEFAULT));
		assertEquals(1, getWordLength("+x", DEFAULT));
		assertEquals(0, getWordLength("abc", 3, DEFAULT));
		assertEquals(0, getWordLength("", DEFAULT));
	}

	@Test
	public void testWordLengthOfOperatorsAndStrings() {
		assertEquals(2, getWordLength("x := 1", 2, DEFAULT));
		assertEquals(2, getWordLength("a => b", 2, DEFAULT));
		assertEquals(2, getWordLength("\r\nx", DEFAULT));
		assertEquals(4, getWordLength("'it''s'", DEFAULT));
		assertEquals(5, getWordLength("\"a b\" c", DEFAULT));
	}

	@Test
	public void testWordLengthWithContributedDetector() {
		final var detector = new HashWordDetector();
		assertEquals(6, getWordLength("my#var x", detector));
		assertEquals(2, getWordLength("#1 x", detector));
	}

	@Test
	public void testReplaceRangeLength() {
		assertEquals(3, getReplaceRangeLength("abc def", 0, "x", DEFAULT));
		assertEquals(2, getReplaceRangeLength("   foo", 0, "x", DEFAULT));
		assertEquals(0, getReplaceRangeLength("\nfoo", 0, "x", DEFAULT));
		assertEquals(0, getReplaceRangeLength("  \nfoo", 0, "x", DEFAULT));
		assertEquals(0, getReplaceRangeLength("", 0, "x", DEFAULT));
	}

	@Test
	public void testReplaceRangeLengthOfLineFeed() {
		assertEquals(1, getReplaceRangeLength("\nfoo", 0, "\n", DEFAULT));
		assertEquals(2, getReplaceRangeLength("\r\nfoo", 0, "\n", DEFAULT));
		assertEquals(0, getReplaceRangeLength(" \nfoo", 0, "\n", DEFAULT));
	}

	@Test
	public void testQuotedStringsAreMatchedAtTheOffsetOnly() {
		assertEquals(1, getWordLength("'a \"b\"", DEFAULT), "not closed: the string further on does not count");
		assertEquals(6, getWordLength("\"a\\nb\" \"c\"", DEFAULT), "an escape other than a quote");
		assertEquals(5, getWordLength("\"a\\\\\" b", DEFAULT), "an escaped backslash does not escape the quote");
	}

	@Test
	public void testQuotedStringsStayOnOneLine() {
		assertEquals(1, getWordLength("'t do\nthis isn't", DEFAULT), "an apostrophe, not a string");
		assertEquals(1, getWordLength("\"a\r\nb\"", DEFAULT));
	}

	@Test
	public void testLongQuotedStringDoesNotOverflowTheStack() {
		final String text = '"' + "a\\\" ".repeat(100_000) + '"';
		assertEquals(text.length(), getWordLength(text, DEFAULT));
	}

	@Test
	public void testSurrogatePairIsNotSplit() {
		assertEquals(2, getWordLength("😀x", DEFAULT));
	}
}
