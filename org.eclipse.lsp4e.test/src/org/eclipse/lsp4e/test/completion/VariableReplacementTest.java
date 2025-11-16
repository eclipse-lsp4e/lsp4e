/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.ui.PartInitException;
import org.junit.jupiter.api.Test;

public class VariableReplacementTest extends AbstractCompletionTest {

	@Test
	public void testDuplicateVariable() throws PartInitException, CoreException {
		CompletionItem completionItem = createCompletionItem("${1:foo} and ${1:foo}", CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE
				.setCompletionList(new CompletionList(true, List.of(completionItem)));
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,""));
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSCompletionProposal) proposals[0]).apply(viewer.getDocument());
		assertEquals("foo and foo", viewer.getDocument().get());
		// TODO check link edit groups
	}

	@Test
	public void testVariableReplacement() throws PartInitException, CoreException {
		CompletionItem completionItem = createCompletionItem(
				"${1:$TM_FILENAME_BASE} ${2:$TM_FILENAME} ${3:$TM_FILEPATH} ${4:$TM_DIRECTORY} ${5:$TM_LINE_INDEX} ${6:$TM_LINE_NUMBER} ${7:$TM_CURRENT_LINE} ${8:$TM_SELECTED_TEXT}",
				CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE
				.setCompletionList(new CompletionList(true, List.of(completionItem)));
		final var content = "line1\nline2\nline3";
		IFile testFile = TestUtils.createUniqueTestFile(project, content);
		ITextViewer viewer = TestUtils.openTextViewer(testFile);
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		viewer.setSelectedRange(2, 3); //ne1
		((LSCompletionProposal) proposals[0]).apply(viewer, '\0', 0, 0);

		String fileNameBase = testFile.getFullPath().removeFileExtension().lastSegment();
		String filePath = testFile.getRawLocation().toOSString();
		String fileDir = project.getLocation().toOSString();
		assertEquals(String.format("%s %s %s %s %d %d %s %s%s" , fileNameBase, testFile.getName(), filePath, fileDir,
				0, 1, "line1", "ne1", content.substring(1) ), viewer.getDocument().get());
		// TODO check link edit groups
	}

	@Test
	public void testVariableNameWithoutBraces() throws PartInitException, CoreException {
		CompletionItem completionItem = createCompletionItem(
				"$TM_FILENAME_BASE",
				CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE
				.setCompletionList(new CompletionList(true, List.of(completionItem)));
		final var content = "line1\nline2\nline3";
		IFile testFile = TestUtils.createUniqueTestFile(project, content);
		ITextViewer viewer = TestUtils.openTextViewer(testFile);
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSCompletionProposal) proposals[0]).apply(viewer.getDocument());

		String fileNameBase = testFile.getFullPath().removeFileExtension().lastSegment();
		assertEquals(fileNameBase + "ine1\nline2\nline3", viewer.getDocument().get());
		// TODO check link edit groups
	}

	@Test
	public void testVariableNameWithBraces() throws PartInitException, CoreException {
		CompletionItem completionItem = createCompletionItem(
				"${TM_FILENAME_BASE}",
				CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE
				.setCompletionList(new CompletionList(true, List.of(completionItem)));
		final var content = "line1\nline2\nline3";
		IFile testFile = TestUtils.createUniqueTestFile(project, content);
		ITextViewer viewer = TestUtils.openTextViewer(testFile);
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSCompletionProposal) proposals[0]).apply(viewer.getDocument());

		String fileNameBase = testFile.getFullPath().removeFileExtension().lastSegment();
		assertEquals(fileNameBase + "ine1\nline2\nline3", viewer.getDocument().get());
		// TODO check link edit groups
	}
}
