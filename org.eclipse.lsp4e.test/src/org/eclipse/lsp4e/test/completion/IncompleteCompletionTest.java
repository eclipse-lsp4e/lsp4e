/*******************************************************************************
 * Copyright (c) 2016, 2024 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Max Bureck (Fraunhofer FOKUS) - Adjustments to variable replacement test
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Point;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Test;

public class IncompleteCompletionTest extends AbstractCompletionTest {
	/*
	 * This tests the not-so-official way to associate a LS to a file programmatically, and then to retrieve the LS
	 * for the file independently of the content-types. Although doing it programmatically isn't recommended, consuming
	 * file-specific LS already associated is something we want to support.
	 */
	@Test
	public void testAssistForUnknownButConnectedType() throws CoreException {
		final var items = new ArrayList<CompletionItem>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		IFile testFile = TestUtils.createUniqueTestFileOfUnknownType(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		LanguageServerDefinition serverDefinition = LanguageServersRegistry.getInstance().getDefinition("org.eclipse.lsp4e.test.server");
		assertNotNull(serverDefinition);
		LanguageServerWrapper lsWrapper = LanguageServiceAccessor.getLSWrapper(testFile.getProject(), serverDefinition);
		URI fileLocation = testFile.getLocationURI();
		// force connection (that's what LSP4E should be designed to prevent 3rd party from having to use it).
		lsWrapper.connect(null, testFile);

		waitForAndAssertCondition(3_000, () -> lsWrapper.isConnectedTo(fileLocation));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testNoPrefix() throws CoreException {
		final var items = new ArrayList<CompletionItem>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testDeprecatedCompletion() throws Exception {
		BoldStylerProvider boldStyleProvider = null;
		try {
			final var items = new ArrayList<CompletionItem>();
			CompletionItem completionItem = createCompletionItem("FirstClassDeprecated", CompletionItemKind.Class);
			completionItem.setDeprecated(true);
			items.add(completionItem);
			MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

			final var content = "First";
			ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

			ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer,
					content.length());
			assertEquals(1, proposals.length);
			final var proposal = (LSCompletionProposal) proposals[0];

			StyledString simpleStyledStr = proposal.getStyledDisplayString();
			assertEquals("FirstClassDeprecated", simpleStyledStr.getString());
			assertEquals(1, simpleStyledStr.getStyleRanges().length);
			StyleRange styleRange = simpleStyledStr.getStyleRanges()[0];
			assertTrue(styleRange.strikeout);

			boldStyleProvider = new BoldStylerProvider(UI.getActiveShell().getFont());
			StyledString styledStr = proposal.getStyledDisplayString(viewer.getDocument(), 4, boldStyleProvider);
			assertTrue(styledStr.getStyleRanges().length > 1);
			for (StyleRange sr : styledStr.getStyleRanges()) {
				assertTrue(sr.strikeout);
			}
		} finally {
			if (boldStyleProvider != null) {
				boldStyleProvider.dispose();
			}
		}
	}

	@Test
	public void testPrefix() throws CoreException {
		final var items = new ArrayList<CompletionItem>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		items.add(createCompletionItem("SecondClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		// TODO compare items
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testPrefixCaseSensitivity() throws CoreException {
		final var items = new ArrayList<CompletionItem>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "FIRST";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);
		// TODO compare items
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompleteOnFileEnd() throws CoreException { // bug 508842
		final var item = new CompletionItem();
		item.setLabel("1024M");
		item.setKind(CompletionItemKind.Value);
		item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(2, 10), new Position(2, 10)), "1024M")));
		final var completionList = new CompletionList(true, List.of(item));
		MockLanguageServer.INSTANCE.setCompletionList(completionList);

		final var content = "applications:\n" + "- name: hello\n" + "  memory: ";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		assertEquals(1, proposals.length);

		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(content + "1024M", viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().getLength(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompletionWithAdditionalEditsBeforeOffsetInSameLine() throws CoreException {
		final var items = new ArrayList<CompletionItem>();
		final var item = new CompletionItem("additionaEditsCompletion");
		item.setKind(CompletionItemKind.Function);
		item.setInsertText("MainInsertText");

		final var additionalTextEdits = new ArrayList<TextEdit>();

		final var additionaEdit1 = new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "addOnText1");
		final var additionaEdit2 = new TextEdit(new Range(new Position(0, 12), new Position(0, 12)), "addOnText2");
		additionalTextEdits.add(additionaEdit1);
		additionalTextEdits.add(additionaEdit2);

		item.setAdditionalTextEdits(additionalTextEdits);
		items.add(item);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "this <> is <> the main <> content of the file";
		IFile testFile = TestUtils.createUniqueTestFile(project, content);
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 24);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());

		String newContent = viewer.getDocument().get();
		assertEquals("this <addOnText1> is <addOnText2> the main <MainInsertText> content of the file", newContent);
	}

	@Test
	public void testCompletionWithAdditionalEditsBeforeAndAfterOffsetInSameLine() throws CoreException {
		final var items = new ArrayList<CompletionItem>();
		final var item = new CompletionItem("additionaEditsCompletion");
		item.setKind(CompletionItemKind.Function);
		item.setInsertText("MainInsertText");

		final var additionalTextEdits = new ArrayList<TextEdit>();

		final var additionaEdit1 = new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "addOnText1");
		final var additionaEdit2 = new TextEdit(new Range(new Position(0, 24), new Position(0, 24)), "addOnText2");
		additionalTextEdits.add(additionaEdit1);
		additionalTextEdits.add(additionaEdit2);

		item.setAdditionalTextEdits(additionalTextEdits);
		items.add(item);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "this <> is <> the main <> content of the file";
		IFile testFile = TestUtils.createUniqueTestFile(project, content);
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 12);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());

		String newContent = viewer.getDocument().get();
		assertEquals("this <addOnText1> is <MainInsertText> the main <addOnText2> content of the file", newContent);
	}

	@Test
	public void testCompletionWithAdditionalEditsBeforeAndAfterOffsetInVariousLines() throws CoreException {
		final var items = new ArrayList<CompletionItem>();
		final var item = new CompletionItem("additionaEditsCompletion");
		item.setKind(CompletionItemKind.Function);
		item.setInsertText("MainInsertText");

		final var additionalTextEdits = new ArrayList<TextEdit>();

		final var additionaEdit1 = new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "addOnText1");
		final var additionaEdit2 = new TextEdit(new Range(new Position(0, 24), new Position(0, 24)), "addOnText2");
		final var additionaEdit3 = new TextEdit(new Range(new Position(1, 9), new Position(1, 9)), "addOnText3");
		additionalTextEdits.add(additionaEdit1);
		additionalTextEdits.add(additionaEdit2);
		additionalTextEdits.add(additionaEdit3);

		item.setAdditionalTextEdits(additionalTextEdits);
		items.add(item);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "this <> is <> the main <> content of the file\nthis is <> the second line";
		IFile testFile = TestUtils.createUniqueTestFile(project, content);
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 12);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());

		String newContent = viewer.getDocument().get();
		assertEquals("this <addOnText1> is <MainInsertText> the main <addOnText2> content of the file\nthis is <addOnText3> the second line", newContent);
	}

	@Test
	public void testCompletionWithAdditionalEditsBeforeAndAfterOffsetInVariousLinesAndTypedText() throws CoreException {
		final var items = new ArrayList<CompletionItem>();
		final var item = new CompletionItem("additionaEditsCompletion");
		item.setKind(CompletionItemKind.Function);
		item.setInsertText("MainInsertText");

		final var additionalTextEdits = new ArrayList<TextEdit>();

		final var additionaEdit1 = new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "addOnText1");
		final var additionaEdit2 = new TextEdit(new Range(new Position(0, 24), new Position(0, 24)), "addOnText2");
		final var additionaEdit3 = new TextEdit(new Range(new Position(1, 9), new Position(1, 9)), "addOnText3");
		additionalTextEdits.add(additionaEdit1);
		additionalTextEdits.add(additionaEdit2);
		additionalTextEdits.add(additionaEdit3);

		item.setAdditionalTextEdits(additionalTextEdits);
		items.add(item);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "this <> is <Main> the main <> content of the file\nthis is <> the second line";
		IFile testFile = TestUtils.createUniqueTestFile(project, content);
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 12);
		assertEquals(items.size(), proposals.length);
		// TODO compare both structures
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument(), Character.MIN_VALUE, 16);

		String newContent = viewer.getDocument().get();
		assertEquals("this <addOnText1> is <MainInsertText> the main <addOnText2> content of the file\nthis is <addOnText3> the second line", newContent);
	}

	@Test
	public void testSnippetCompletionWithAdditionalEdits() throws CoreException {
		final var item = new CompletionItem("snippet item");
		item.setInsertText("$1 and ${2:foo}");
		item.setKind(CompletionItemKind.Class);
		item.setInsertTextFormat(InsertTextFormat.Snippet);
		final var additionalTextEdits = new ArrayList<TextEdit>();

		final var additionaEdit1 = new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), "addOnText1");
		final var additionaEdit2 = new TextEdit(new Range(new Position(0, 12), new Position(0, 12)), "addOnText2");
		additionalTextEdits.add(additionaEdit1);
		additionalTextEdits.add(additionaEdit2);

		item.setAdditionalTextEdits(additionalTextEdits);

		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, List.of(item)));

		final var content = "this <> is <> the main <> content of the file";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 24);
		assertEquals(1, proposals.length);
		((LSCompletionProposal) proposals[0]).apply(viewer.getDocument());

		String newContent = viewer.getDocument().get();
		assertEquals("this <addOnText1> is <addOnText2> the main < and foo> content of the file", newContent);
		// TODO check link edit groups
	}

	@Test
	public void testApplyCompletionWithPrefix() throws CoreException {
		final var range = new Range(new Position(0, 0), new Position(0, 5));
		List<CompletionItem> items = List
				.of(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "First";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, content.length());
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(true, viewer.getDocument().get().equals("FirstClass"));
		assertEquals(new Point(viewer.getDocument().getLength(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplace() throws CoreException {
		final var range = new Range(new Position(0, 0), new Position(0, 20));
		List<CompletionItem> items = List
				.of(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "FirstNotMatchedLabel";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 5);
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals("FirstClass", viewer.getDocument().get());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplaceAndTypingWithTextEdit() throws CoreException, BadLocationException {
		final var range = new Range(new Position(0, 0), new Position(0, 22));
		List<CompletionItem> items = List
				.of(createCompletionItem("FirstClass", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "FirstNotMatchedLabel";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,content));

		int invokeOffset = 5;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];

		// simulate additional typing (to filter) after invoking completion
		viewer.getDocument().replace(5, 0, "No");

		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals("FirstClass", viewer.getDocument().get());
		assertEquals(new Point("FirstClass".length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testApplyCompletionReplaceAndTyping() throws CoreException, BadLocationException {
		final var item = new CompletionItem("strncasecmp");
		item.setKind(CompletionItemKind.Function);
		item.setInsertText("strncasecmp()");

		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, List.of(item)));

		final var content = "str";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		int invokeOffset = content.length();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];

		// simulate additional typing (to filter) after invoking completion
		viewer.getDocument().replace(content.length(), 0, "nc");

		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals(item.getInsertText(), viewer.getDocument().get());
		assertEquals(new Point(item.getInsertText().length(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompletionReplace() throws CoreException {
		IFile file = TestUtils.createUniqueTestFile(project, "line1\nlineInsertHere");
		ITextViewer viewer = TestUtils.openTextViewer(file);
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, List.of(
			createCompletionItem("Inserted", CompletionItemKind.Text, new Range(new Position(1, 4), new Position(1, 4 + "InsertHere".length())))
		)));

		int invokeOffset = viewer.getDocument().getLength() - "InsertHere".length();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];
		LSIncompleteCompletionProposal.apply(viewer.getDocument());
		assertEquals("line1\nlineInserted", viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().getLength(), 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testItemOrdering() throws Exception {
		final var range = new Range(new Position(0, 0), new Position(0, 1));
		List<CompletionItem> items = List.of( //
				createCompletionItem("AA", CompletionItemKind.Class, range),
				createCompletionItem("AB", CompletionItemKind.Class, range),
				createCompletionItem("BA", CompletionItemKind.Class, range),
				createCompletionItem("BB", CompletionItemKind.Class, range),
				createCompletionItem("CB", CompletionItemKind.Class, range),
				createCompletionItem("CC", CompletionItemKind.Class, range));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		final var content = "B";
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,content));

		int invokeOffset = 1;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(4, proposals.length);
		assertEquals("BA", proposals[0].getDisplayString());
		assertEquals("BB", proposals[1].getDisplayString());
		assertEquals("AB", proposals[2].getDisplayString());
		assertEquals("CB", proposals[3].getDisplayString());

		((LSCompletionProposal) proposals[0]).apply(viewer.getDocument());
		assertEquals("BA", viewer.getDocument().get());
	}

	@Test
	public void testBasicSnippet() throws CoreException {
		CompletionItem completionItem = createCompletionItem("$1 and ${2:foo}", CompletionItemKind.Class, new Range(new Position(0, 0), new Position(0, 1)));
		completionItem.setInsertTextFormat(InsertTextFormat.Snippet);
		MockLanguageServer.INSTANCE
				.setCompletionList(new CompletionList(true, List.of(completionItem)));
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project,""));
		int invokeOffset = 0;
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		assertEquals(1, proposals.length);
		((LSCompletionProposal) proposals[0]).apply(viewer.getDocument());
		assertEquals(" and foo", viewer.getDocument().get());
		// TODO check link edit groups
	}

	@Test
	public void testMultipleLS() throws Exception {
		final var items = new ArrayList<CompletionItem>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		IFile testFile = TestUtils.createUniqueTestFileMultiLS(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(2 * items.size(), proposals.length);
	}

	@Test
	public void testCompletionWithAdditionalTextEditInsertion() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "Some Text Before\n<tag>tagText</tag>\nSome Text After");
		ITextViewer viewer = TestUtils.openTextViewer(file);
		// Create item
		CompletionItem item = createCompletionItem("tagText",
				CompletionItemKind.Text,
				new Range(
						new Position(1, "<tag>".length()),
						new Position(1,  "<tag>".length() + "tag".length())));
		// Set additional TExtEdits on the item
		final var additionalEdits = new ArrayList<TextEdit>(2);
		// Prefix to be inserted to the end of line that precedes the line to be "completed"
		additionalEdits.add(new TextEdit(new Range(
				new Position(1, 0),
				new Position(1, 0)),
				"<prefix>prefixText</prefix>\n"));
		// Postfix to be inserted to the end of line to be "completed"
		additionalEdits.add(new TextEdit(new Range(
				new Position(1, "<tag>tag</tag>".length()),
				new Position(1, "<tag>tag</tag>".length())),
				"\n<postfix>postfixText</postfix>"));
		item.setAdditionalTextEdits(additionalEdits);

		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, List.of(item)));

		String text = viewer.getDocument().get();
		int invokeOffset = text.indexOf("<tag>tag") + "<tag>tag".length();
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, invokeOffset);
		final var LSIncompleteCompletionProposal = (LSCompletionProposal) proposals[0];

		// Apply the proposal using an offset shifted by 4 chars, thus emulating a user typed character after the CA invocation
		int currentOffset = invokeOffset + "Text".length(); // Emulate 1 character typed in after CA invocation
		LSIncompleteCompletionProposal.apply(viewer.getDocument(), Character.MIN_VALUE, currentOffset);

		final var expectedText = "Some Text Before\n"
				+ "<prefix>prefixText</prefix>\n"
				+ "<tag>tagText</tag>\n"
				+ "<postfix>postfixText</postfix>\n"
				+ "Some Text After";
		assertEquals(expectedText, viewer.getDocument().get());
		assertEquals(new Point(viewer.getDocument().get().indexOf("</tag>") , 0),
				LSIncompleteCompletionProposal.getSelection(viewer.getDocument()));
	}

	@Test
	public void testCompletionExternalFile() throws Exception {
		final var items = new ArrayList<CompletionItem>();
		items.add(createCompletionItem("FirstClassExternal", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		File file = TestUtils.createTempFile("testCompletionExternalFile", ".lspt");
		final var editor = (ITextEditor) IDE.openEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toURI()));
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(1, proposals.length);
		proposals[0].apply(viewer.getDocument());
		assertEquals("FirstClassExternal", viewer.getDocument().get());
	}

	@Test
	public void testAdditionalInformation() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IDocument document = TestUtils.openTextViewer(testFile).getDocument();
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile, capabilities -> capabilities.getCompletionProvider() != null
						|| capabilities.getSignatureHelpProvider() != null)
				.get(0);
		final var completionProposal = new LSCompletionProposal(document, 0,
				new CompletionItem("blah"), wrapper);
		completionProposal.getAdditionalProposalInfo(new NullProgressMonitor()); // check no exception is sent
	}

	@Test
	public void testAdditionalInformationWithEmptyDetail() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IDocument document = TestUtils.openTextViewer(testFile).getDocument();
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile, capabilities -> capabilities.getCompletionProvider() != null
						|| capabilities.getSignatureHelpProvider() != null)
				.get(0);
		final var item = new CompletionItem("blah");
		item.setDetail("");
		final var completionProposal = new LSCompletionProposal(document, 0,
				item, wrapper);
		String addInfo = (String) completionProposal.getAdditionalProposalInfo(new NullProgressMonitor()); // check no exception is sent
		assertTrue(addInfo.isEmpty());
	}

	@Test
	public void testAdditionalInformationWithDetail() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IDocument document = TestUtils.openTextViewer(testFile).getDocument();
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile, capabilities -> capabilities.getCompletionProvider() != null
						|| capabilities.getSignatureHelpProvider() != null)
				.get(0);
		final var item = new CompletionItem("blah");
		item.setDetail("detail");
		final var completionProposal = new LSCompletionProposal(document, 0,
				item, wrapper);
		String addInfo = (String) completionProposal.getAdditionalProposalInfo(new NullProgressMonitor()); // check no exception is sent
		assertTrue(addInfo.indexOf("<p>detail</p>") >= 0);
	}

	@Test
	public void testAdditionalInformationWithEmptyDocumentation() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IDocument document = TestUtils.openTextViewer(testFile).getDocument();
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile, capabilities -> capabilities.getCompletionProvider() != null
						|| capabilities.getSignatureHelpProvider() != null)
				.get(0);
		final var item = new CompletionItem("blah");
		item.setDocumentation("");
		final var completionProposal = new LSCompletionProposal(document, 0,
				item, wrapper);
		String addInfo = (String) completionProposal.getAdditionalProposalInfo(new NullProgressMonitor()); // check no exception is sent
		assertTrue(addInfo.isEmpty());
	}

	@Test
	public void testAdditionalInformationWithDocumentation() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IDocument document = TestUtils.openTextViewer(testFile).getDocument();
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile, capabilities -> capabilities.getCompletionProvider() != null
						|| capabilities.getSignatureHelpProvider() != null)
				.get(0);
		final var item = new CompletionItem("blah");
		item.setDocumentation("documentation");
		final var completionProposal = new LSCompletionProposal(document, 0,
				item, wrapper);
		String addInfo = (String) completionProposal.getAdditionalProposalInfo(new NullProgressMonitor()); // check no exception is sent
		assertFalse(addInfo.isEmpty());
	}

	@Test
	public void testIncompleteIndication() throws CoreException {
		final var items = new ArrayList<CompletionItem>();
		items.add(createCompletionItem("FirstClass", CompletionItemKind.Class));
		MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(true, items));

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		// without incomplete indication
		ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(1, proposals.length);

		// with incomplete indication
		final var incompleIndicatingProcessor = new LSContentAssistProcessor(true, true);
		ICompletionProposal[] proposalsWithIncompleteProposal = incompleIndicatingProcessor.computeCompletionProposals(viewer, 0);
		assertEquals(2, proposalsWithIncompleteProposal.length);

		// compare both proposal lists
		assertEquals("FirstClass", proposals[0].getDisplayString());
		assertEquals("FirstClass", proposalsWithIncompleteProposal[0].getDisplayString());
		assertEquals("➕ Continue typing for more proposals...", proposalsWithIncompleteProposal[1].getDisplayString());
	}
}
