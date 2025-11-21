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
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.operations.rename.LSPMoveParticipant;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockWorkspaceService;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.ltk.core.refactoring.participants.MoveArguments;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LSPMoveParticipantTest extends AbstractTestWithProject {

	static class TestableMoveParticipant extends LSPMoveParticipant {
		private MoveArguments args;

		void setArgs(MoveArguments args) {
			this.args = args;
		}

		@Override
		public boolean initialize(Object element) {
			return super.initialize(element);
		}

		@Override
		public MoveArguments getArguments() {
			return args;
		}
	}

	@BeforeEach
	void setupCaps() {
		MockLanguageServer.reset(() -> {
			ServerCapabilities caps = MockLanguageServer.defaultServerCapabilities();
			var ws = new WorkspaceServerCapabilities();
			var fileOps = new FileOperationsServerCapabilities();
			fileOps.setWillRename(new FileOperationOptions());
			ws.setFileOperations(fileOps);
			caps.setWorkspace(ws);
			return caps;
		});
	}

	@Test
	void computesNewUriFromDestinationResource() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(file); // start LS
		assertTrue(LanguageServers.forProject(project).anyMatching());

		IFolder dest = project.getFolder("dest");
		if (!dest.exists()) {
			dest.create(true, true, null);
		}

		URI oldUri = LSPEclipseUtils.toUri(file);
		assertNotNull(oldUri);
		URI expectedNewUri = LSPEclipseUtils.toUri(dest.getRawLocation() != null //
				? dest.getRawLocation().append(file.getName())
				: dest.getLocation().append(file.getName()));

		var participant = new TestableMoveParticipant();
		participant.setArgs(new MoveArguments(dest, false));
		assertTrue(participant.initialize(file));
		participant.createPreChange(new NullProgressMonitor());

		MockWorkspaceService ws = MockLanguageServer.INSTANCE.getWorkspaceService();
		assertNotNull(ws.getLastWillRename());
		assertEquals(1, ws.getLastWillRename().getFiles().size());
		assertEquals(oldUri.toString(), ws.getLastWillRename().getFiles().get(0).getOldUri());
		assertEquals(expectedNewUri.toString(), ws.getLastWillRename().getFiles().get(0).getNewUri());
	}

	@Test
	void computesNewUriFromDestinationPath() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "content");
		TestUtils.openTextViewer(file); // start LS
		assertTrue(LanguageServers.forProject(project).anyMatching());

		IFolder dest = project.getFolder("destPath");
		if (!dest.exists()) {
			dest.create(true, true, null);
		}

		URI oldUri = LSPEclipseUtils.toUri(file);
		assertNotNull(oldUri);
		IPath destPath = dest.getRawLocation() != null ? dest.getRawLocation() : dest.getLocation();
		assertNotNull(destPath);
		URI expectedNewUri = LSPEclipseUtils.toUri(destPath.append(file.getName()));

		var participant = new TestableMoveParticipant();
		participant.setArgs(new MoveArguments(destPath, false));
		assertTrue(participant.initialize(file));
		participant.createPreChange(new NullProgressMonitor());

		MockWorkspaceService ws = MockLanguageServer.INSTANCE.getWorkspaceService();
		assertNotNull(ws.getLastWillRename());
		assertEquals(1, ws.getLastWillRename().getFiles().size());
		assertEquals(oldUri.toString(), ws.getLastWillRename().getFiles().get(0).getOldUri());
		assertEquals(expectedNewUri.toString(), ws.getLastWillRename().getFiles().get(0).getNewUri());
	}
}
