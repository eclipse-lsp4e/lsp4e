/*******************************************************************************
 * Copyright (c) 2016, 2018 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Martin Lippert (Pivotal Inc.) - fixed instability
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDE;
import org.junit.jupiter.api.Test;

public class DocumentDidSaveTest extends AbstractTestWithProject {

	@Test
	public void testSave() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		// make sure that timestamp after save will differ from creation time (no better idea at the moment)
		testFile.setLocalTimeStamp(0);

		// Force LS to initialize and open file
		IDocument document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServers.forDocument(document).anyMatching();
		final var didSaveExpectation = new CompletableFuture<DidSaveTextDocumentParams>();
		MockLanguageServer.INSTANCE.setDidSaveCallback(didSaveExpectation);

		// simulate change in file
		viewer.getDocument().replace(0, 0, "Hello");
		editor.doSave(new NullProgressMonitor());

		waitForAndAssertCondition(2_000, () -> {
			DidSaveTextDocumentParams lastChange = didSaveExpectation.get(10, TimeUnit.MILLISECONDS);
			assertNotNull(lastChange.getTextDocument());
			assertEquals(LSPEclipseUtils.toUri(testFile).toString(), lastChange.getTextDocument().getUri());
			return true;
		});
	}

	@Test
	public void testSaveExternalFile() throws Exception {
		File file = TestUtils.createTempFile("testSaveExternalFile", ".lspt");
		IEditorPart editor = IDE.openEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toURI()));
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		// make sure that timestamp after save will differ from creation time (no better idea at the moment)
//			testFile.setLocalTimeStamp(0);

		// Force LS to initialize and open file
		LanguageServers.forDocument(LSPEclipseUtils.getDocument(editor.getEditorInput())).anyMatching();
		final var didSaveExpectation = new CompletableFuture<DidSaveTextDocumentParams>();
		MockLanguageServer.INSTANCE.setDidSaveCallback(didSaveExpectation);

		// simulate change in file
		viewer.getDocument().replace(0, 0, "Hello");
		editor.doSave(new NullProgressMonitor());

		waitForAndAssertCondition(2_000, () -> {
			DidSaveTextDocumentParams lastChange = didSaveExpectation.get(10, TimeUnit.MILLISECONDS);
			assertNotNull(lastChange.getTextDocument());
			assertEquals(LSPEclipseUtils.toUri(file).toString(), lastChange.getTextDocument().getUri());
			return true;
		});
	}
}
