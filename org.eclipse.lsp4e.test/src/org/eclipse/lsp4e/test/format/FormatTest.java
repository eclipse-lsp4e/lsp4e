/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.format;

import static org.eclipse.lsp4e.test.utils.TestUtils.numberOfChangesIs;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.VersionedEdits;
import org.eclipse.lsp4e.operations.format.LSPFormatter;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.Test;

public class FormatTest extends AbstractTestWithProject {

	@Test
	public void testFormattingInvalidDocument() throws Exception {
		final var formatter = new LSPFormatter();
		ITextSelection selection = TextSelection.emptySelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(new Document(), selection).get();
		assertTrue(edits.isEmpty());
	}

	/**
	 * Verifies that when the language server reports no formatting edits
	 * (empty edit list), {@link LSPFormatter} returns an empty Optional
	 * and leaves the document content unchanged.
	 */
	@Test
	public void testFormattingEmptyEditsYieldEmptyOptional() throws Exception {
		MockLanguageServer.INSTANCE.setFormattingTextEdits(Collections.emptyList());

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();
		assertTrue(edits.isEmpty());
		assertEquals("Formatting Other Text", viewer.getDocument().get());

		TestUtils.closeEditor(editor, false);
	}

	@Test
	public void testFormattingNoChanges() throws Exception {
		MockLanguageServer.INSTANCE.setFormattingTextEdits(Collections.emptyList());

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();

		// No formatting edits produced -> no VersionedEdits returned
		assertTrue(edits.isEmpty());

		final var textEditor = (ITextEditor) editor;
		textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		assertEquals("Formatting Other Text", viewer.getDocument().get());

		TestUtils.closeEditor(editor, false);
	}

	@Test
	public void testFormatting()
			throws Exception {
		final var formattingTextEdits = new ArrayList<TextEdit>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "MyF"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 10), new Position(0, 11)), ""));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 21), new Position(0, 21)), " Second"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();
		assertTrue(edits.isPresent());
		editor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.get().apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});

		final var textEditor = (ITextEditor) editor;
		textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		assertEquals("MyFormattingOther Text Second", viewer.getDocument().get());

		TestUtils.closeEditor(editor, false);
	}
	
	@Test
	public void testSelectiveFormatting() throws Exception {
		String fileContent = "Line 1\nLine 2\n\nText to be formatted.\nLine 5";
		
		final var formattingTextEdits = new ArrayList<TextEdit>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 5), new Position(0, 5)), " changed"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(3, 10), new Position(3, 11)), "\n"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);
		
		IFile file = TestUtils.createUniqueTestFile(project, fileContent);
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		
		viewer.setSelectedRange(viewer.getDocument().getLineOffset(3), viewer.getDocument().getLineLength(3));
		
		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();
		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();
		
		// only the edits in the selection range are expected to be returned
		assertTrue(edits.isPresent());
		assertEquals(1, edits.get().data.size());
		
		editor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.get().apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});

		final var textEditor = (ITextEditor) editor;
		textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		String formattedText = viewer.getDocument().get();
		assertEquals("Line 1\nLine 2\n\nText to be\nformatted.\nLine 5", formattedText);
		
		TestUtils.closeEditor(editor, false);
	}
	
	@Test
	public void testSelectiveFormattingWithEmptySelection() throws Exception {
		String fileContent = "Line 1\nLine 2\n\nText to be formatted.\nLine 5";
		
		final var formattingTextEdits = new ArrayList<TextEdit>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), " changed"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(3, 10), new Position(3, 11)), "\n"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);
		
		IFile file = TestUtils.createUniqueTestFile(project, fileContent);
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		
		viewer.setSelectedRange(viewer.getDocument().getLineOffset(3), 0);
		
		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();
		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();
		
		assertTrue(edits.isPresent());
		assertEquals(formattingTextEdits.size(), edits.get().data.size());
		
		editor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.get().apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});

		final var textEditor = (ITextEditor) editor;
		textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		String formattedText = viewer.getDocument().get();
		assertEquals("Line 1 changed\nLine 2\n\nText to be\nformatted.\nLine 5", formattedText);
		
		TestUtils.closeEditor(editor, false);
	}
	
	private static ServerCapabilities customServerWithoutRangeFormatting() {
		ServerCapabilities capabilities = MockLanguageServer.defaultServerCapabilities();
		capabilities.setDocumentRangeFormattingProvider(false);
		return capabilities;
	} 
	
	@Test
	public void testSelectiveFormattingWithIncapableServer() throws Exception {
		MockLanguageServer.reset(FormatTest::customServerWithoutRangeFormatting);
		
		String fileContent = "Line 1\nLine 2\n\nText to be formatted.\nLine 5";
		
		final var formattingTextEdits = new ArrayList<TextEdit>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 6), new Position(0, 6)), " changed"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(3, 10), new Position(3, 11)), "\n"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);
		
		IFile file = TestUtils.createUniqueTestFile(project, fileContent);
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		
		viewer.setSelectedRange(viewer.getDocument().getLineOffset(3), viewer.getDocument().getLineLength(3));
		
		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();
		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();
		
		assertTrue(edits.isPresent());
		assertEquals(formattingTextEdits.size(), edits.get().data.size());
		
		editor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.get().apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});

		final var textEditor = (ITextEditor) editor;
		textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		String formattedText = viewer.getDocument().get();
		assertEquals("Line 1 changed\nLine 2\n\nText to be\nformatted.\nLine 5", formattedText);
		
		TestUtils.closeEditor(editor, false);
	}

	@Test
	public void testOutdatedFormatting()
			throws CoreException, InterruptedException, ExecutionException, BadLocationException {
		// Use a non-empty edit list so that a VersionedEdits is produced
		MockLanguageServer.INSTANCE.setFormattingTextEdits(List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "X")));

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var formatter = new LSPFormatter();
		ISelection selection = viewer.getSelectionProvider().getSelection();

		Optional<VersionedEdits> edits = formatter.requestFormatting(viewer.getDocument(), (ITextSelection) selection).get();
		assertTrue(edits.isPresent());
		viewer.getDocument().replace(0, 0, "Hello");
		waitForAndAssertCondition(1_000,  numberOfChangesIs(1));

		assertThrows(ConcurrentModificationException.class, () -> edits.get().apply());

		TestUtils.closeEditor(editor, false);
	}
}
