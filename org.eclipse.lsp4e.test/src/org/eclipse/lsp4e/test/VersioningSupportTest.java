/*******************************************************************************
 * Copyright (c) 2022-3 Cocotec Ltd and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ahmed Hussain (Cocotec Ltd) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.eclipse.lsp4e.test.utils.TestUtils.numberOfChangesIs;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.VersionedEdits;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.junit.jupiter.api.Test;

public class VersioningSupportTest extends AbstractTestWithProject {

	@Test
	public void testVersionSupportSuccess() throws Exception {
		final var formattingTextEdits = new ArrayList<TextEdit>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "MyF"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 10), new Position(0, 11)), ""));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 21), new Position(0, 21)), " Second"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		IEditorPart editor = TestUtils.openEditor(file);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var doc = viewer.getDocument();
		final var docId = LSPEclipseUtils.toTextDocumentIdentifier(doc);

		final var params = new DocumentFormattingParams();
		params.setTextDocument(docId);
		params.setOptions(new FormattingOptions(4, true));

		var ex = LanguageServers.forDocument(doc).withCapability(ServerCapabilities::getDocumentFormattingProvider);

		long modificationStamp = DocumentUtil.getDocumentModificationStamp(doc);

		var result = ex.computeFirst(ls -> ls.getTextDocumentService().formatting(params).thenApply(edits -> new VersionedEdits(modificationStamp, edits, doc)));

		VersionedEdits edits = result.join().get();
		editor.getSite().getShell().getDisplay().syncExec(() -> {
			try {
				edits.apply();
			} catch (ConcurrentModificationException | BadLocationException e) {
				fail(e.getMessage());
			}
		});

		TestUtils.closeEditor(editor, false);
	}

	@Test
	public void testVersionedEditsFailsOnModification() throws Exception {
		final var formattingTextEdits = new ArrayList<TextEdit>();
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 0), new Position(0, 1)), "MyF"));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 10), new Position(0, 11)), ""));
		formattingTextEdits.add(new TextEdit(new Range(new Position(0, 21), new Position(0, 21)), " Second"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(formattingTextEdits);

		IFile file = TestUtils.createUniqueTestFile(project, "Formatting Other Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		final var doc = viewer.getDocument();
		final var docId = LSPEclipseUtils.toTextDocumentIdentifier(doc);

		final var params = new DocumentFormattingParams();
		params.setTextDocument(docId);
		params.setOptions(new FormattingOptions(4, true));

		var ex = LanguageServers.forDocument(doc).withCapability(ServerCapabilities::getDocumentFormattingProvider);
		long modificationStamp = DocumentUtil.getDocumentModificationStamp(doc);

		var result = ex.computeFirst(ls -> ls.getTextDocumentService().formatting(params).thenApply(edits -> new VersionedEdits(modificationStamp, edits, doc)));

		VersionedEdits edits = result.join().get();
		viewer.getDocument().replace(0, 0, "Hello");
		waitForAndAssertCondition(1_000,  numberOfChangesIs(1));

		assertThrows(ConcurrentModificationException.class, () -> edits.apply());
	}
}
