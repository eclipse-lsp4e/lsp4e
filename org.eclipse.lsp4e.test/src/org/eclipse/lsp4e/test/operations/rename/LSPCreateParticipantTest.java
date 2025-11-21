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
import org.eclipse.lsp4e.operations.rename.LSPCreateParticipant;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockWorkspaceService;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.CreateArguments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LSPCreateParticipantTest extends AbstractTestWithProject {

	static class TestableCreateParticipant extends LSPCreateParticipant {
		private CreateArguments args;

		void setArgs(CreateArguments args) {
			this.args = args;
		}

		@Override
		public boolean initialize(Object element) {
			return super.initialize(element);
		}

		@Override
		public CreateArguments getArguments() {
			return args;
		}
	}

	@BeforeEach
	void setupCaps() {
		MockLanguageServer.reset(() -> {
			ServerCapabilities caps = MockLanguageServer.defaultServerCapabilities();
			var ws = new WorkspaceServerCapabilities();
			var fileOps = new FileOperationsServerCapabilities();
			fileOps.setWillCreate(new FileOperationOptions());
			ws.setFileOperations(fileOps);
			caps.setWorkspace(ws);
			return caps;
		});
	}

	@Test
	void fileCreateSendsWillCreate() throws Exception {
		// start LS
		IFile starter = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(starter);
		assertTrue(LanguageServers.forProject(project).anyMatching());

		IFile toCreate = project.getFile("toCreate.lspt");
		URI uri = LSPEclipseUtils.toUri(toCreate);
		assertNotNull(uri);

		var participant = new TestableCreateParticipant();
		participant.setArgs(new CreateArguments());
		assertTrue(participant.initialize(toCreate));
		participant.checkConditions(new NullProgressMonitor(), new CheckConditionsContext());
		participant.createPreChange(new NullProgressMonitor());

		MockWorkspaceService ws = MockLanguageServer.INSTANCE.getWorkspaceService();
		assertNotNull(ws.getLastWillCreate());
		assertEquals(1, ws.getLastWillCreate().getFiles().size());
		assertEquals(uri.toString(), ws.getLastWillCreate().getFiles().get(0).getUri());
	}

	@Test
	void folderCreateSendsWillCreate() throws Exception {
		// start LS
		IFile starter = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(starter);
		assertTrue(LanguageServers.forProject(project).anyMatching());

		IFolder toCreate = project.getFolder("toCreateFolder");
		URI uri = LSPEclipseUtils.toUri(toCreate);
		assertNotNull(uri);

		var participant = new TestableCreateParticipant();
		participant.setArgs(new CreateArguments());
		assertTrue(participant.initialize(toCreate));
		participant.checkConditions(new NullProgressMonitor(), new CheckConditionsContext());
		participant.createPreChange(new NullProgressMonitor());

		MockWorkspaceService ws = MockLanguageServer.INSTANCE.getWorkspaceService();
		assertNotNull(ws.getLastWillCreate());
		assertEquals(1, ws.getLastWillCreate().getFiles().size());
		assertEquals(uri.toString(), ws.getLastWillCreate().getFiles().get(0).getUri());
	}
}
