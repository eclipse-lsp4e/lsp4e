/*******************************************************************************
 * Copyright (c) 2018, 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

public class CompletionOrderingTests extends AbstractCompletionTest {

	@Test
	public void testItemOrdering(MockLanguageServerFactory factory) throws Exception {
		confirmCompletionResults(new String[] { "AA", "AB", "BA", "BB", "CB", "CC" }, "B", 1,
				new String[] { "BA", "BB", "AB", "CB" }, factory);
	}

	public static final Arguments[] testOrderByCategory = {
			Arguments.argumentSet("Category 1 before Category 2 (testa)",
					new String[] { "testa", "test.a", "a.test.a", "a.testa", "test" },
					new String[] { "test", "testa", "a.testa", "test.a", "a.test.a" }),
			Arguments.argumentSet("Category 2 before Category 3 (atest)", new String[] { "testa", "atest", "a.testa" },
					new String[] { "testa", "a.testa", "atest" }),
			Arguments.argumentSet("Category 3 before Category 4 (tZesZt)", new String[] { "atesta", "tZesZt", "atest" },
					new String[] { "atest", "atesta", "tZesZt" }),
			Arguments.argumentSet("Category 4 before Category 5 (qwerty)",
					new String[] { "qwerty", "tZesZt", "t.e.s.t" }, new String[] { "tZesZt", "t.e.s.t", "qwerty" }) };
	
	@ParameterizedTest
	@FieldSource
	public void testOrderByCategory(String[] completions, String[] orderedResults, MockLanguageServerFactory factory) throws Exception {
		confirmCompletionResults(completions, "test", 4, orderedResults, factory);
	}
	
	public static final Arguments[] testOrderByRank = {
			Arguments.argumentSet("Category 1",
					new String[] { "prefix.test", "alongprefix.test", "test", "test.test", "pretest.test" },
					new String[] { "test", "test.test", "pretest.test", "prefix.test", "alongprefix.test" }),
			Arguments.argumentSet("Category 2",
					new String[] { "testa", "alongprefix.testa", "testatest", "prefix.testa" },
					new String[] { "testa", "prefix.testa", "alongprefix.testa", "testatest" }),
			Arguments.argumentSet("Category 3", new String[] { "atesta", "teteteststst", "long.prefixtest.suffix" },
					new String[] { "atesta", "teteteststst", "long.prefixtest.suffix" }),
			Arguments.argumentSet("Category 4", new String[] { "tlongbreakbetweenest", "tZesZt", "t.e.s.t", "tes.tst" },
					new String[] { "tes.tst", "tZesZt", "t.e.s.t", "tlongbreakbetweenest" }) };
	
	@ParameterizedTest
	@FieldSource
	public void testOrderByRank(String[] completions, String[] orderedResults, MockLanguageServerFactory factory) throws Exception {
		confirmCompletionResults(completions, "test", 4, orderedResults, factory);
	}

	public static final Arguments[] testOrderWithCapitalization = {
			Arguments.argumentSet("Category 1",
					new String[] { "prefiX.Test", "alongprefix.test", "tEsT", "teSt.teST", "preTEst.test" },
					new String[] { "tEsT", "teSt.teST", "preTEst.test", "prefiX.Test",
					"alongprefix.test" }),
			Arguments.argumentSet("Category 2",
					new String[] { "teSTa", "alonGPrefix.TESTA", "tEStatest", "prefix.testa" },
					new String[] { "teSTa", "prefix.testa", "alonGPrefix.TESTA", "tEStatest" }),
			Arguments.argumentSet("Category 3",
					new String[] { "ATesta", "teTETesTSTst", "long.prefixtest.suffix" },
					new String[] { "ATesta", "teTETesTSTst", "long.prefixtest.suffix" }),
			Arguments.argumentSet("Category 4",
					new String[] { "TlongbreakbetweenEST", "TZesZT", "t.e.s.t", "teS.tst" },
					new String[] { "teS.tst", "TZesZT", "t.e.s.t", "TlongbreakbetweenEST" }),
	};
	
	@ParameterizedTest
	@FieldSource
	public void testOrderWithCapitalization(String[] completions, String[] orderedResults, MockLanguageServerFactory factory) throws Exception {
		confirmCompletionResults(completions, "test", 4, orderedResults, factory);
	}

	@Test
	public void testOrderWithLongInsert(MockLanguageServerFactory factory) throws Exception {
		var items = new ArrayList<CompletionItem>();
		var item = new CompletionItem("server.address");
		item.setFilterText("server.address");
		item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(1, 12), new Position(5, 7)),
						"  address: $1\n" +
						"spring:\n" +
						"  application:\n" +
						"    name: f\n")));
		items.add(item);

		item = new CompletionItem("management.server.address");
		item.setFilterText("management.server.address");
		item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(5, 0), new Position(5, 7)),
						"management:\n" +
						"  server:\n" +
						"    address: $1\n")));
		items.add(item);

		item = new CompletionItem("→ spring.jta.atomikos.datasource.xa-data-source-class-name");
		item.setFilterText("spring.jta.atomikos.datasource.xa-data-source-class-name");
		item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(5, 0), new Position(0, 12)),item.getFilterText())));
		items.add(item);

		final var orderedResults = new String[] { "server.address", "management.server.address",
				"→ spring.jta.atomikos.datasource.xa-data-source-class-name" };

		confirmCompletionResults(items,
						"server:\n" +
						"  port: 555\n" +
						"spring:\n" +
						"  application:\n" +
						"    name: f\n" +
						"address",
				62, orderedResults, factory);
	}

	@Test
	public void testSortTextIsComparedLexicographically(MockLanguageServerFactory factory) throws Exception {
		final var completions = new ArrayList<CompletionItem>();

		final var item15 = createCompletionItem("15", CompletionItemKind.Class);
		item15.setSortText("15");
		completions.add(item15);

		final var item5 = createCompletionItem("5", CompletionItemKind.Class);
		item5.setSortText("5");
		completions.add(item5);

		confirmCompletionResults(completions, "", 0, new String[] { "15", "5" }, factory);
	}

	@Test
	public void testMovingOffset() throws Exception {
		final var range = new Range(new Position(0, 0), new Position(0, 4));
		IFile testFile = TestUtils.createUniqueTestFile(project, "test");
		IDocument document = TestUtils.openTextViewer(testFile).getDocument();
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile,
						capabilities -> capabilities.getCompletionProvider() != null
						|| capabilities.getSignatureHelpProvider() != null)
				.get(0);

		CompletionItem completionItem = createCompletionItem("test", CompletionItemKind.Class, range);
		var completionProposal = new LSCompletionProposal(document, 0,
				completionItem, wrapper);
		// Blank input ''
		assertEquals("", completionProposal.getDocumentFilter());
		assertEquals(0, completionProposal.getRankScore());
		assertEquals(5, completionProposal.getRankCategory());
		// Typed test 'test'
		assertEquals("test", completionProposal.getDocumentFilter(4));
		assertEquals(0, completionProposal.getRankScore());
		assertEquals(1, completionProposal.getRankCategory());
		// Moved cursor back 't'
		assertEquals("t", completionProposal.getDocumentFilter(1));
		assertEquals(0, completionProposal.getRankScore());
		assertEquals(2, completionProposal.getRankCategory());

		document.set("prefix:pnd");
		completionItem = createCompletionItem("append", CompletionItemKind.Class);
		completionProposal = new LSCompletionProposal(document, 7, completionItem, wrapper);
		// Blank input 'prefix:'
		assertEquals("", completionProposal.getDocumentFilter());
		assertEquals(0, completionProposal.getRankScore());
		assertEquals(5, completionProposal.getRankCategory());
		// Typed test 'prefix:pnd'
		assertEquals("pnd", completionProposal.getDocumentFilter(10));
		assertEquals(5, completionProposal.getRankScore());
		assertEquals(4, completionProposal.getRankCategory());
		// Moved cursor back 'prefix:p'
		assertEquals("p", completionProposal.getDocumentFilter(8));
		assertEquals(1, completionProposal.getRankScore());
		assertEquals(3, completionProposal.getRankCategory());
	}

	@Test
	public void testPerformance(MockLanguageServerFactory factory) throws Exception {
		final var batchSizes = new int[] { 10, 100, 1000, 10000 };
		final var resultAverages = new int[batchSizes.length];
		final int repeat = 5;
		final ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "abcdefgh"));

		for (int i = 0; i < batchSizes.length; i++) {
			long resultSum = 0;
			for (int j = 0; j < repeat; j++) {
				resultSum += timeToDisplayCompletionList(viewer, batchSizes[i], factory.getServer());
			}
			resultAverages[i] = (int) (resultSum / repeat);
		}
		double pearsonCorrelation = isLinearCorelation(batchSizes, resultAverages);
		assertTrue(pearsonCorrelation > 0.99);
	}

	private double isLinearCorelation(int[] batchSizes, int[] resultAverages) {
		int n = batchSizes.length;

		long batchSum = 0;
		long resulthSum = 0;
		long batchSumSquared = 0;
		long resulthSumSquared = 0;
		long productSum = 0;
		for (int i = 0; i < n; i++) {
			batchSum += batchSizes[i];
			resulthSum += resultAverages[i];
			batchSumSquared += Math.pow(batchSizes[i], 2);
			resulthSumSquared += Math.pow(resultAverages[i], 2);
			productSum += batchSizes[i] * resultAverages[i];
		}
		double numerator = productSum - (batchSum * resulthSum / n);
		double denominator = Math.sqrt(
				(batchSumSquared - Math.pow(batchSum, 2) / n) * (resulthSumSquared - Math.pow(resulthSum, 2) / n));
		return denominator == 0 ? 0 : numerator / denominator;
	}

	private long timeToDisplayCompletionList(ITextViewer viewer, int size, MockLanguageServer mockLanguageServer) {
		final var range = new Range(new Position(0, 0), new Position(0, 8));
		final var items = new ArrayList<CompletionItem>();
		for (int i = 0; i < size; i++) {
			items.add(createCompletionItem(randomString(), CompletionItemKind.Class, range));
		}
		mockLanguageServer.setCompletionList(new CompletionList(false, items));

		long startTimeControl = System.currentTimeMillis();
		contentAssistProcessor.computeCompletionProposals(viewer, 0);
		long endTimeControl = System.currentTimeMillis();
		return endTimeControl - startTimeControl;
	}

	private static final String CHARACTERS = "abcdefghABCDEFGH.-_";

	private String randomString() {
		int count = 50;
		final var builder = new StringBuilder();
		while (count-- != 0) {
			int character = (int) (Math.random() * CHARACTERS.length());
			builder.append(CHARACTERS.charAt(character));
		}
		return builder.toString();
	}
}
