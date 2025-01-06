/*******************************************************************************
 * Copyright (c) 2022 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Evolution AG) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ui.IEditorPart;
import org.junit.jupiter.api.Test;

public class DocumentWillSaveWaitUntilTest extends AbstractTestWithProject {

	private List<TextEdit> createSingleTextEditAtFileStart(String newText) {
		final var textEdit = new TextEdit();
		textEdit.setRange(new Range(new Position(0, 0), new Position(0, newText.length())));
		textEdit.setNewText(newText);
		return List.of(textEdit);
	}

	@Test
	public void testSave(MockLanguageServerFactory factory) throws Exception {
		final var oldText = "Hello";
		final var newText = "hello";

		factory.withConfiguration((idx, server)-> {
			server.setWillSaveWaitUntil(createSingleTextEditAtFileStart(newText));
		});

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		// Force LS to initialize and open file
		IDocument document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServers.forDocument(document).anyMatching();

		// simulate change in file
		viewer.getDocument().replace(0, 0, oldText);
		editor.doSave(new NullProgressMonitor());

		// wait for will save wait until to apply the text edit
		waitForAndAssertCondition("Text has not been lowercased", 2_000, () -> {
			try {
				return newText.equals(viewer.getDocument().get(0, newText.length()));
			} catch (BadLocationException e) {
				return false;
			}
		});
	}

	@Test
	public void testCancelOnTimeout(MockLanguageServerFactory factory) throws Exception {
		final var oldText = "Hello";
		final String timeoutKey = "org.eclipse.lsp4e.test.server.timeout.willSaveWaitUntil";
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue(timeoutKey, 1);
		try {
			factory.withConfiguration((idx, server) -> {
				server.setWillSaveWaitUntil(createSingleTextEditAtFileStart("hello"));
			});

			IFile testFile = TestUtils.createUniqueTestFile(project, "");
			IEditorPart editor = TestUtils.openEditor(testFile);
			ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

			// Force LS to initialize and open file
			IDocument document = LSPEclipseUtils.getDocument(testFile);
			assertNotNull(document);
			LanguageServers.forDocument(document).anyMatching();

			factory.getServer().setTimeToProceedQueries(3_000);
			viewer.getDocument().replace(0, 0, oldText);
			editor.doSave(new NullProgressMonitor());

			waitForAndAssertCondition("willSaveWaitUntil request has not been cancelled", 2_000,
					() -> !factory.cancellations.isEmpty());
			assertEquals(oldText, viewer.getDocument().get());
		} finally {
			store.setToDefault(timeoutKey);
		}
	}
}
