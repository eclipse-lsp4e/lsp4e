/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDE;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DocumentDidCloseTest extends AbstractTestWithProject {

	@Test
	public void testClose() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);

		// Force LS to initialize and open file
		IDocument document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServers.forDocument(document).anyMatching();

		final var didCloseExpectation = new CompletableFuture<DidCloseTextDocumentParams>();
		MockLanguageServer.INSTANCE.setDidCloseCallback(didCloseExpectation);

		TestUtils.closeEditor(editor, false);
		DidCloseTextDocumentParams lastChange = didCloseExpectation.get(1000, TimeUnit.MILLISECONDS);
		assertNotNull(lastChange.getTextDocument());
		assertEquals(LSPEclipseUtils.toUri(testFile).toString(), lastChange.getTextDocument().getUri());
	}

	@Test
	public void testCloseExternalFile(@TempDir Path tempDir) throws Exception {
		Path testFile = Files.createFile(tempDir.resolve("testCloseExternalFile.lspt"));
		IEditorPart editor = IDE.openEditorOnFileStore(UI.getActivePage(), EFS.getStore(testFile.toUri()));

		// Force LS to initialize and open file
		LanguageServers.forDocument(LSPEclipseUtils.getDocument(editor.getEditorInput())).anyMatching();

		final var didCloseExpectation = new CompletableFuture<DidCloseTextDocumentParams>();
		MockLanguageServer.INSTANCE.setDidCloseCallback(didCloseExpectation);

		TestUtils.closeEditor(editor, false);

		DidCloseTextDocumentParams lastChange = didCloseExpectation.get(1000, TimeUnit.MILLISECONDS);
		assertNotNull(lastChange.getTextDocument());
		assertEquals(LSPEclipseUtils.toUri(testFile.toFile()).toString(), lastChange.getTextDocument().getUri());
	}
}
