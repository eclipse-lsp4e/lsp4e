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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.operations.rename.LSPDeleteParticipant;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockWorkspaceService;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.DeleteArguments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LSPDeleteParticipantTest extends AbstractTestWithProject {

	static class TestableDeleteParticipant extends LSPDeleteParticipant {
		private DeleteArguments args;

		void setArgs(DeleteArguments args) {
			this.args = args;
		}

		@Override
		public boolean initialize(Object element) {
			return super.initialize(element);
		}

		@Override
		public DeleteArguments getArguments() {
			return args;
		}
	}

	@BeforeEach
	void setupCaps() {
		MockLanguageServer.reset(() -> {
			ServerCapabilities caps = MockLanguageServer.defaultServerCapabilities();
			var ws = new WorkspaceServerCapabilities();
			var fileOps = new FileOperationsServerCapabilities();
			fileOps.setWillDelete(new FileOperationOptions());
			ws.setFileOperations(fileOps);
			caps.setWorkspace(ws);
			return caps;
		});
	}

	@Test
	void fileDeleteSendsWillDelete() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(file); // start LS
		assertTrue(LanguageServers.forProject(project).anyMatching());

		URI uri = LSPEclipseUtils.toUri(file);
		assertNotNull(uri);

		var participant = new TestableDeleteParticipant();
		participant.setArgs(new DeleteArguments());
		assertTrue(participant.initialize(file));
		participant.checkConditions(new NullProgressMonitor(), new CheckConditionsContext());
		participant.createPreChange(new NullProgressMonitor());

		MockWorkspaceService ws = MockLanguageServer.INSTANCE.getWorkspaceService();
		assertNotNull(ws.getLastWillDelete());
		assertEquals(1, ws.getLastWillDelete().getFiles().size());
		assertEquals(uri.toString(), ws.getLastWillDelete().getFiles().get(0).getUri());
	}

	@Test
	void folderDeleteSendsWillDelete() throws Exception {
		// Start LS with a file
		IFile starter = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(starter);
		assertTrue(LanguageServers.forProject(project).anyMatching());

		IFolder folder = project.getFolder("toDeleteFolder");
		if (!folder.exists()) {
			folder.create(true, true, null);
		}
		URI uri = LSPEclipseUtils.toUri(folder);
		assertNotNull(uri);

		var participant = new TestableDeleteParticipant();
		participant.setArgs(new DeleteArguments());
		assertTrue(participant.initialize(folder));
		participant.checkConditions(new NullProgressMonitor(), new CheckConditionsContext());
		participant.createPreChange(new NullProgressMonitor());

		MockWorkspaceService ws = MockLanguageServer.INSTANCE.getWorkspaceService();
		assertNotNull(ws.getLastWillDelete());
		assertEquals(1, ws.getLastWillDelete().getFiles().size());
		assertEquals(uri.toString(), ws.getLastWillDelete().getFiles().get(0).getUri());
	}
}
