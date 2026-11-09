/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.lsp4e.test.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.graphics.Point;
import org.junit.jupiter.api.Test;

public class InsertReplaceCompletionTest extends AbstractCompletionTest {

	@Test
	public void testInsert(MockLanguageServerFactory factory) throws Exception {
		factory.withConfiguration((idx, server) -> {
			server.setCompletionList(new CompletionList(false, List.of(
					createCompletionItemWithInsertReplace(
							"Inserted",
							CompletionItemKind.Text,
							new Range(new Position(1, 4), new Position(1, 4 + "InsertHere".length())),
							new Range(new Position(0,0),new Position(0,0)))
					)));
		});
		IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineInsertHere");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		int invokeOffset = viewer.getDocument().getLength() - "InsertHere".length();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		final var lsCompletionProposal = (LSCompletionProposal) proposals[0];
		lsCompletionProposal.apply(viewer, '\n', 0, invokeOffset);
		assertEquals("line1\nlineInserted", viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().getLength(), 0), lsCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testEffectiveEdit(MockLanguageServerFactory factory) throws Exception {
		final var insert = new Range(new Position(0, 7), new Position(0, 10));
		final CompletionItem insertReplace = createCompletionItemWithInsertReplace(".log", CompletionItemKind.Method,
				insert, new Range(new Position(0, 7), new Position(0, 11)));
		final var snippet = new CompletionItem("log(x)");
		snippet.setInsertTextFormat(InsertTextFormat.Snippet);
		snippet.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(0, 8), new Position(0, 10)), "log(${1:x})")));
		factory.withConfiguration((idx, server) -> {
			server.setCompletionList(new CompletionList(false, List.of(insertReplace, snippet)));
		});
		IFile file = TestUtils.createUniqueTestFile(project, "console.lo\n");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 10);
		// the insert range is what is applied
		TextEdit edit = proposal(proposals, ".log").getEffectiveEdit(viewer, 10);
		assertEquals(insert, edit.getRange());
		assertEquals(".log", edit.getNewText());
		// a snippet is expanded
		edit = proposal(proposals, "log(x)").getEffectiveEdit(viewer, 10);
		assertEquals("log(x)", edit.getNewText());
		// computing the edit changes neither the item nor the document
		assertEquals(insert, insertReplace.getTextEdit().getRight().getInsert());
		assertEquals("console.lo\n", viewer.getDocument().get());
	}

	private static LSCompletionProposal proposal(ICompletionProposal[] proposals, String label) {
		return Arrays.stream(proposals).map(LSCompletionProposal.class::cast)
				.filter(proposal -> label.equals(proposal.getItem().getLabel())).findFirst().orElseThrow();
	}
}
