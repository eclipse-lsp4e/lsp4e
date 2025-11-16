/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.lsp4e.operations.completion.CompletionProposalTools;
import org.junit.jupiter.api.Test;

/**
 * Test cases for {@link CompletionProposalTools#getScoreOfFilterMatch(String, String)}
 */
public class ScoreOfFilterMatchTest {
	private static final Random RANDOM = new Random();

	private static String generateRandomString(final int maxLength) {
		final var length = RANDOM.nextInt(maxLength + 1);
		if (length == 0)
			return "";
		var chars = "abcdefghijklmnopqrstuvwxyzäöüß";
		chars = "_" + chars + chars.toUpperCase();
		final var sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
		}
		return sb.toString();
	}

	@Test
	public void testCaseInsensitivity() {
		final var documentFilter = "Example";
		final var completionFilter = "eXaMpLe";
		final int expectedScore = 0;
		final int actualScore = CompletionProposalTools.getScoreOfFilterMatch(documentFilter, completionFilter);
		assertEquals(expectedScore, actualScore, "The score should be 0 for case-insensitive matches.");
	}

	@Test
	public void testEmptyCompletionFilter() {
		final var documentFilter = "example";
		final var completionFilter = "";
		final int expectedScore = -1;
		final int actualScore = CompletionProposalTools.getScoreOfFilterMatch(documentFilter, completionFilter);
		assertEquals(expectedScore, actualScore, "The score should be -1 for an empty completionFilter.");
	}

	@Test
	public void testEmptyDocumentFilter() {
		final var documentFilter = "";
		final var completionFilter = "example";
		final int expectedScore = 0;
		final int actualScore = CompletionProposalTools.getScoreOfFilterMatch(documentFilter, completionFilter);
		assertEquals(expectedScore, actualScore, "The score should be 0 for an empty documentFilter.");
	}

	/**
	 * Test case for https://github.com/eclipse-lsp4e/lsp4e/issues/1132
	 */
	@Test
	public void testRandomFilters() {
		final int testCases = 50_000; // Number of random test cases to try
		final int timeoutMS = 3; // Timeout threshold for detecting infinite loops

		final var executor = Executors.newSingleThreadExecutor();

		for (int i = 0; i < testCases; i++) {
			final var commonPrefix = generateRandomString(2); // String with length of 0-2 chars
			final var documentFilter = commonPrefix + generateRandomString(5);
			final var completionFilter = commonPrefix + generateRandomString(15);

			final var future = executor
					.submit(() -> CompletionProposalTools.getScoreOfFilterMatch(documentFilter, completionFilter));

			try {
				future.get(timeoutMS, TimeUnit.SECONDS);
			} catch (final TimeoutException e) {
				future.cancel(true); // Interrupt stuck execution
				fail("Possible infinite loop detected in getScoreOfFilterMatch with inputs: " + "documentFilter='"
						+ documentFilter + "', completionFilter='" + completionFilter + "'");
			} catch (Exception e) {
				fail("Unexpected exception: " + e.getMessage());
			}
		}

		executor.shutdown();
	}

	@Test
	public void testExactMatch() {
		final var documentFilter = "example";
		final var completionFilter = "example";
		final int expectedScore = 0;
		final int actualScore = CompletionProposalTools.getScoreOfFilterMatch(documentFilter, completionFilter);
		assertEquals(expectedScore, actualScore, "The score should be 0 for exact matches.");
	}

	@Test
	public void testNoMatch() {
		final var documentFilter = "foo";
		final var completionFilter = "example";
		final int expectedScore = -1;
		final int actualScore = CompletionProposalTools.getScoreOfFilterMatch(documentFilter, completionFilter);
		assertEquals(expectedScore, actualScore, "The score should be -1 when there's no match.");
	}

	@Test
	public void testPrefixMatch() {
		final var documentFilter = "ex";
		final var completionFilter = "example";
		final int expectedScore = 0;
		final int actualScore = CompletionProposalTools.getScoreOfFilterMatch(documentFilter, completionFilter);
		assertEquals(expectedScore, actualScore, "The score should be 0 when documentFilter is a prefix.");
	}

	@Test
	public void testScatteredMatch() {
		final var documentFilter = "eap";
		final var completionFilter = "example";
		final int expectedScore = 6;
		final int actualScore = CompletionProposalTools.getScoreOfFilterMatch(documentFilter, completionFilter);
		assertEquals(expectedScore, actualScore, "The score should account for scattered characters.");
	}
}
