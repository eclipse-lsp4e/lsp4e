/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.concurrent.CompletableFuture;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.junit.jupiter.api.Test;

/**
 * Tests which documents are connected to language servers without any feature sending a request.
 */
public class DocumentConnectionTest extends AbstractTestWithProject {

	@Test
	public void testFileBufferWithoutEditorDoesNotStartServer(MockLanguageServerFactory factory) throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		manager.connect(testFile.getFullPath(), LocationKind.IFILE, null);
		try {
			assertFalse(TestUtils.waitForCondition(2_000, () -> factory.getServerCount() > 0),
					"Language server was started for a document not shown in an editor");
		} finally {
			manager.disconnect(testFile.getFullPath(), LocationKind.IFILE, null);
		}
	}

	@Test
	public void testEditorConnectsServerWithoutCapabilities(MockLanguageServerFactory factory) throws Exception {
		final var didOpen = new CompletableFuture<DidOpenTextDocumentParams>();
		factory.withCapabilities(() -> {
			final var capabilities = new ServerCapabilities();
			capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
			return capabilities;
		});
		factory.withConfiguration((idx, server) -> server.setDidOpenCallback(didOpen));
		IFile testFile = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile);

		TestUtils.waitForAndAssertCondition(5_000, didOpen::isDone);
		assertEquals(LSPEclipseUtils.toUri(testFile).toString(), didOpen.get().getTextDocument().getUri());
	}

	@Test
	public void testFileBufferConnectsServerDeclaringConnectFileBuffers(MockLanguageServerFactory factory)
			throws Exception {
		final var didOpen = new CompletableFuture<DidOpenTextDocumentParams>();
		factory.withConfiguration((idx, server) -> server.setDidOpenCallback(didOpen));
		IFile testFile = TestUtils.createUniqueTestFile(project, "lsptbuffer", "");
		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		manager.connect(testFile.getFullPath(), LocationKind.IFILE, null);
		try {
			TestUtils.waitForAndAssertCondition(5_000, didOpen::isDone);
			assertEquals(LSPEclipseUtils.toUri(testFile).toString(), didOpen.get().getTextDocument().getUri());
		} finally {
			manager.disconnect(testFile.getFullPath(), LocationKind.IFILE, null);
		}
	}
}
