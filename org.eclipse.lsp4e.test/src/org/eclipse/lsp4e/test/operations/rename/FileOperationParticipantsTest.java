/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.operations.rename;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.operations.rename.LSPFileOperationParticipantSupport;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockWorkspaceService;
import org.eclipse.lsp4j.CreateFilesParams;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.FileCreate;
import org.eclipse.lsp4j.FileDelete;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPattern;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileOperationParticipantsTest extends AbstractTestWithProject {

	@BeforeEach
	void setupCaps() {
		MockLanguageServer.reset(() -> {
			ServerCapabilities caps = MockLanguageServer.defaultServerCapabilities();
			var ws = new WorkspaceServerCapabilities();
			var fileOps = new FileOperationsServerCapabilities();
			fileOps.setWillCreate(new FileOperationOptions());
			fileOps.setWillRename(new FileOperationOptions());
			fileOps.setWillDelete(new FileOperationOptions());
			ws.setFileOperations(fileOps);
			caps.setWorkspace(ws);
			return caps;
		});
	}

	@Test
	void testFilterGlobMatching() throws Exception {
		// Reconfigure with a forward-slash glob filter
		MockLanguageServer.reset(() -> {
			ServerCapabilities caps = MockLanguageServer.defaultServerCapabilities();
			var ws = new WorkspaceServerCapabilities();
			var fileOps = new FileOperationsServerCapabilities();
			var pattern = new FileOperationPattern("**/*.lspt");
			var filter = new FileOperationFilter(pattern, "file");
			var opts = new FileOperationOptions(List.of(filter));
			fileOps.setWillRename(opts);
			ws.setFileOperations(fileOps);
			caps.setWorkspace(ws);
			return caps;
		});

		IFile file = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(file);
		TestUtils.waitForAndAssertCondition(5_000, () -> LanguageServers.forProject(project).anyMatching());
		var executor = LSPFileOperationParticipantSupport.createFileOperationExecutor(file,
				FileOperationsServerCapabilities::getWillRename);
		assertTrue(executor.anyMatching());
	}

	@Test
	void testWillRename() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(file);
		URI uri = LSPEclipseUtils.toUri(file);
		assertNotNull(uri);

		// Ensure an LS is available
		assertTrue(LanguageServers.forProject(project).anyMatching());

		var executor = LSPFileOperationParticipantSupport.createFileOperationExecutor(file,
				FileOperationsServerCapabilities::getWillRename);
		assertTrue(executor.anyMatching());

		var params = new RenameFilesParams();
		URI newUri = LSPEclipseUtils.toUri(project.getFile("renamed-" + file.getName()));
		params.getFiles().add(new FileRename(uri.toString(), newUri.toString()));

		// Exercise helper to trigger server call
		LSPFileOperationParticipantSupport.computePreChange("rename", params, executor,
				(ws, p) -> ws.willRenameFiles(p));

		MockWorkspaceService ws = MockLanguageServer.INSTANCE.getWorkspaceService();
		assertNotNull(ws.getLastWillRename());
		assertEquals(1, ws.getLastWillRename().getFiles().size());
		assertEquals(uri.toString(), ws.getLastWillRename().getFiles().get(0).getOldUri());
		assertEquals(newUri.toString(), ws.getLastWillRename().getFiles().get(0).getNewUri());
	}

	@Test
	void testWillCreate() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(file);
		URI uri = LSPEclipseUtils.toUri(file);
		assertNotNull(uri);

		var executor = LSPFileOperationParticipantSupport.createFileOperationExecutor(file,
				FileOperationsServerCapabilities::getWillCreate);
		assertTrue(executor.anyMatching());

		var params = new CreateFilesParams();
		params.getFiles().add(new FileCreate(uri.toString()));

		LSPFileOperationParticipantSupport.computePreChange("create", params, executor,
				(ws, p) -> ws.willCreateFiles(p));

		MockWorkspaceService ws = MockLanguageServer.INSTANCE.getWorkspaceService();
		assertNotNull(ws.getLastWillCreate());
		assertEquals(1, ws.getLastWillCreate().getFiles().size());
		assertEquals(uri.toString(), ws.getLastWillCreate().getFiles().get(0).getUri());
	}

	@Test
	void testWillDelete() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(file);
		URI uri = LSPEclipseUtils.toUri(file);
		assertNotNull(uri);

		var executor = LSPFileOperationParticipantSupport.createFileOperationExecutor(file,
				FileOperationsServerCapabilities::getWillDelete);
		assertTrue(executor.anyMatching());

		var params = new DeleteFilesParams();
		params.getFiles().add(new FileDelete(uri.toString()));

		LSPFileOperationParticipantSupport.computePreChange("delete", params, executor,
				(ws, p) -> ws.willDeleteFiles(p));

		MockWorkspaceService ws = MockLanguageServer.INSTANCE.getWorkspaceService();
		assertNotNull(ws.getLastWillDelete());
		assertEquals(1, ws.getLastWillDelete().getFiles().size());
		assertEquals(uri.toString(), ws.getLastWillDelete().getFiles().get(0).getUri());
	}
}
