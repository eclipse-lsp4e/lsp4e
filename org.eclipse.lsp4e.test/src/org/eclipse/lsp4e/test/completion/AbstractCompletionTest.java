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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertReplaceEdit;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractCompletionTest extends AbstractTestWithProject {

	protected LSContentAssistProcessor contentAssistProcessor;

	@BeforeEach
	public void setUp() {
		contentAssistProcessor = new LSContentAssistProcessor(true, false);
	}

	protected CompletionItem createCompletionItem(String label, CompletionItemKind kind) {
		return createCompletionItem(label, kind, new Range(new Position(0, 0), new Position(0, label.length())));
	}

	protected CompletionItem createCompletionItemWithoutTextEdit(String label, CompletionItemKind kind) {
		final var item = new CompletionItem();
		item.setLabel(label);
		item.setKind(kind);
		return item;
	}
	
	protected CompletionItem createCompletionItem(String label, CompletionItemKind kind, Range range) {
		final var item = new CompletionItem();
		item.setLabel(label);
		item.setKind(kind);
		item.setTextEdit(Either.forLeft(new TextEdit(range, label)));
		return item;
	}

	protected CompletionItem createCompletionItemWithInsertReplace(String label, CompletionItemKind kind, Range insertRange, Range replaceRange) {
		final var item = new CompletionItem();
		item.setLabel(label);
		item.setKind(kind);
		final var insertReplaceEdit = new InsertReplaceEdit();
		insertReplaceEdit.setNewText(label);
		insertReplaceEdit.setInsert(insertRange);
		insertReplaceEdit.setReplace(replaceRange);
		item.setTextEdit(Either.forRight(insertReplaceEdit));
		return item;
	}

	protected void confirmCompletionResults(String[] completions, String content, Integer cursorIndexInContent,
			String[] expectedOrder) throws CoreException {
		final var range = new Range(new Position(0, 0), new Position(0, cursorIndexInContent));
		final var items = new ArrayList<CompletionItem>();
		for (String string : completions) {
			items.add(createCompletionItem(string, CompletionItemKind.Class, range));
		}
		confirmCompletionResults(items, content, cursorIndexInContent, expectedOrder);
	}

	protected void confirmCompletionResults(List<CompletionItem> completions, String content,
			Integer cursorIndexInContent, String[] expectedOrder) throws CoreException {

		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, completions));
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer,
				cursorIndexInContent);
		assertEquals(expectedOrder.length, proposals.length);
		for (int i = 0; i < proposals.length; i++) {
			assertEquals(expectedOrder[i], proposals[i].getDisplayString());
		}
	}
}
