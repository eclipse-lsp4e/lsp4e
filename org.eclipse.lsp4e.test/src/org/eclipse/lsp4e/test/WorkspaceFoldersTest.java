/*******************************************************************************
 * Copyright (c) 2022 Cocotec Ltd and others.
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

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.lsp4e.ConnectDocumentToLanguageServerSetupParticipant;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.test.utils.TestUtils.JobSynchronizer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WorkspaceFoldersTest extends AbstractTestWithProject {

	@BeforeEach
	public void setUp() {
		MockLanguageServer.INSTANCE.getWorkspaceService().getWorkspaceFoldersEvents().clear();
	}

	@Test
	public void testRecycleLSAfterInitialProjectGotDeletedIfWorkspaceFolders() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, c -> true);
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());

		LanguageServerWrapper wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		UI.getActivePage().closeAllEditors(false);
		waitForAndAssertCondition(5_000, () -> !MockLanguageServer.INSTANCE.isRunning());

		project.delete(true, true, new NullProgressMonitor());

		project = TestUtils.createProject("LanguageServiceAccessorTest2" + System.currentTimeMillis());
		IFile testFile2 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile2);
		wrappers = LanguageServiceAccessor.getLSWrappers(testFile2, c -> true);
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());

		LanguageServerWrapper wrapper2 = wrappers.iterator().next();
		assertTrue(wrapper2.isActive());

		// See corresponding LanguageServiceAccessorTest.testCreateNewLSAfterInitialProjectGotDeleted() -
		// if WorkspaceFolders capability present then can recycle the wrapper/server, otherwise a new one gets created
		assertTrue(wrapper1 == wrapper2);
	}

	@Test
	public void testPojectCreate() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, c -> true);
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();

		LanguageServerWrapper wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		UI.getActivePage().closeAllEditors(false);
		waitForAndAssertCondition(5_000, () -> !MockLanguageServer.INSTANCE.isRunning());

		// test that the LS emitted a workspace-folder added event for our project
		final var expected = Paths.get(project.getLocationURI());
		assertTrue(MockLanguageServer.INSTANCE.getWorkspaceService() //
			.getWorkspaceFoldersEvents().stream() //
			.flatMap(event -> event.getEvent().getAdded().stream()) //
			.anyMatch(added -> Paths.get(URI.create(added.getUri())).equals(expected)));
	}

	@Test
	public void testProjectClose() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		LanguageServiceAccessor.getLSWrappers(testFile1, capabilities -> true).iterator().next();
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();
		final var synchronizer = new JobSynchronizer();
		project.close(synchronizer);
		synchronizer.await();

		// test that the LS emitted a workspace-folder removal event for our project
		final var expected = Paths.get(project.getLocationURI());
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.getWorkspaceService() //
				.getWorkspaceFoldersEvents().stream() //
				.flatMap(evt -> evt.getEvent().getRemoved().stream()) //
				.anyMatch(removed -> Paths.get(URI.create(removed.getUri())).equals(expected)));
	}

	@Test
	public void testProjectDelete() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, c -> true);
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();

		LanguageServerWrapper wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		// Grab this before deletion otherwise project.getLocationURI will be null...
		final var expected = Paths.get(project.getLocationURI());
		final var synchronizer = new JobSynchronizer();
		project.delete(true, true, synchronizer);
		synchronizer.await();

		// test that the LS emitted a workspace-folder removal event for our project
		assertTrue(MockLanguageServer.INSTANCE.getWorkspaceService() //
			.getWorkspaceFoldersEvents().stream() //
			.flatMap(event -> event.getEvent().getRemoved().stream()) //
			.anyMatch(removed -> Paths.get(URI.create(removed.getUri())).equals(expected)));
	}

	@Test
	public void testProjectReopen() throws Exception {
		IFile testFile1 = TestUtils.createUniqueTestFile(project, "");

		TestUtils.openEditor(testFile1);
		LanguageServiceAccessor.getLSWrappers(testFile1, capabilities -> true).iterator().next();
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();

		final var synchronizer = new JobSynchronizer();
		project.close(synchronizer);
		synchronizer.await();

		waitForAndAssertCondition(5_000, () -> !project.isOpen());

		final var synchronizer2 = new JobSynchronizer();
		project.open(synchronizer2);
		synchronizer2.await();

		waitForAndAssertCondition(5_000, () -> project.isOpen());

		// test that the LS emitted a workspace-folder added event for our project
		final var expected = Paths.get(project.getLocationURI());
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.getWorkspaceService() //
				.getWorkspaceFoldersEvents().stream() //
				.flatMap(evt -> evt.getEvent().getAdded().stream()) //
				.anyMatch(added -> Paths.get(URI.create(added.getUri())).equals(expected)));
	}

	@Override
	public ServerCapabilities getServerCapabilities() {
		// Enable workspace folders on the mock server (for this test only)
		final ServerCapabilities base = MockLanguageServer.defaultServerCapabilities();

		final var wsc = new WorkspaceServerCapabilities();
		final var wso = new WorkspaceFoldersOptions();
		wso.setSupported(true);
		wso.setChangeNotifications(true);
		wsc.setWorkspaceFolders(wso);
		base.setWorkspace(wsc);
		return base;
	}
}
