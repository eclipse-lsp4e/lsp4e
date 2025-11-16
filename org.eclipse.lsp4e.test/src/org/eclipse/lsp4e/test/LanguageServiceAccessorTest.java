/*******************************************************************************
 * Copyright (c) 2016, 2023 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - added test for Run config
 *  Martin Lippert (Pivotal Inc.) - added tests for multi-root folders, wrapper re-use, and more
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.eclipse.lsp4e.LSPEclipseUtils.getDocument;
import static org.eclipse.lsp4e.LSPEclipseUtils.getTextViewer;
import static org.eclipse.lsp4e.LanguageServiceAccessor.getLSPDocumentInfosFor;
import static org.eclipse.lsp4e.LanguageServiceAccessor.getLSWrapper;
import static org.eclipse.lsp4e.LanguageServiceAccessor.getLSWrappers;
import static org.eclipse.lsp4e.LanguageServiceAccessor.hasActiveLanguageServers;
import static org.eclipse.lsp4e.test.utils.TestUtils.createFile;
import static org.eclipse.lsp4e.test.utils.TestUtils.createProject;
import static org.eclipse.lsp4e.test.utils.TestUtils.createTempFile;
import static org.eclipse.lsp4e.test.utils.TestUtils.createUniqueTestFile;
import static org.eclipse.lsp4e.test.utils.TestUtils.createUniqueTestFileMultiLS;
import static org.eclipse.lsp4e.test.utils.TestUtils.openEditor;
import static org.eclipse.lsp4e.test.utils.TestUtils.openTextViewer;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.MappingEnablementTester;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerMultiRootFolders;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.Test;

public class LanguageServiceAccessorTest extends AbstractTestWithProject {

	private static final Predicate<ServerCapabilities> MATCH_ALL = capabilities -> true;

	@Test
	public void testGetLSWrapper() {
		var serverDefinition = LanguageServersRegistry.getInstance().getDefinition("org.eclipse.lsp4e.test.server");
		assertNotNull(serverDefinition);

		var lsWrapper = getLSWrapper(project, serverDefinition);
		assertNotNull(lsWrapper);
	}

	@Test
	public void testGetLSPDocumentInfoForInvalidDocument() {
		var infos = getLSPDocumentInfosFor(new Document(), MATCH_ALL);
		assertTrue(infos.isEmpty());
	}

	@Test
	public void testGetLSPDocumentInfoForInvalidTextEditor() throws CoreException {
		var testFile = createFile(project, "not_associated_with_ls.abc", "");
		var textViewer = openTextViewer(testFile);
		var infos = getLSPDocumentInfosFor(textViewer.getDocument(), MATCH_ALL);
		assertTrue(infos.isEmpty());
	}

	@Test
	public void testGetLanguageServerInvalidFile() throws Exception {
		var testFile = createFile(project, "not_associated_with_ls.abc", "");
		assertFalse(hasActiveLanguageServers(testFile, MATCH_ALL));
	}

	@Test
	public void testLSAsExtension() throws Exception {
		var testFile = createFile(project, "shouldUseExtension.lspt", "");
		// Force LS to initialize and open file
		LanguageServers.forDocument(LSPEclipseUtils.getDocument(testFile)).anyMatching();
		assertTrue(hasActiveLanguageServers(testFile, MATCH_ALL));
	}

	@Test
	public void testLSAsRunConfiguration() throws Exception {
		var testFile = createFile(project, "shouldUseRunConfiguration.lspt2", "");
		// Force LS to initialize and open file
		LanguageServers.forDocument(LSPEclipseUtils.getDocument(testFile)).anyMatching();
		assertTrue(hasActiveLanguageServers(testFile, MATCH_ALL));
	}

	@Test
	public void testLSAsExtensionForDifferentLanguageId() throws Exception {
		var testFile = createFile(project, "shouldUseExtension.lspt-different", "");
		var lsWrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, lsWrappers.size());

		var wrapper = lsWrappers.iterator().next();
		assertNotNull(wrapper);

		var contentType = Platform.getContentTypeManager()
				.getContentType("org.eclipse.lsp4e.test.content-type-different");
		assertEquals("differentLanguageId", wrapper.getLanguageId(new IContentType[] { contentType }));
	}

	@Test
	public void testGetLSWrappersInitializationFailed() throws Exception {
		var testFile = createFile(project, "fileWithFailedServer.lsptWithException", "");
		var wrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, wrappers.size());
	}

	@Test
	public void testReuseSameLSforMultiContentType() throws Exception {
		var testFile1 = createUniqueTestFile(project, "");
		var testFile2 = createUniqueTestFileMultiLS(project, "");

		var file1LanguageServers = getLSWrappers(testFile1, MATCH_ALL);
		assertEquals(1, file1LanguageServers.size());

		var file2LanguageServers = new ArrayList<>();
		for (var future : getLSWrappers(testFile2, MATCH_ALL)) {
			file2LanguageServers.add(future.serverDefinition);
		}
		assertEquals(2, file2LanguageServers.size());

		var file1LS = file1LanguageServers.get(0).serverDefinition;
		assertTrue(file2LanguageServers.contains(file1LS)); // LS from file1 is reused

		assertEquals(2,	LanguageServers.forProject(project).computeAll(ls -> CompletableFuture.completedFuture(null)).size(),
				"Not right amount of language servers bound to project");
	}

	@Test
	public void testGetOnlyRunningLanguageServers() throws Exception {
		var testFile1 = createUniqueTestFile(project, "");
		var testFile2 = createUniqueTestFile(project, "lspt-different", "");

		var editor1 = openEditor(testFile1);
		var editor2 = openEditor(testFile2);

		assertTrue(hasActiveLanguageServers(testFile1, MATCH_ALL));
		assertTrue(hasActiveLanguageServers(testFile2, MATCH_ALL));

		assertTrue(hasActiveLanguageServers(MATCH_ALL));

		editor1.getSite().getPage().closeEditor(editor1, false);
		editor2.getSite().getPage().closeEditor(editor2, false);

		waitForCondition(5_000, () -> !hasActiveLanguageServers(MATCH_ALL));
		assertFalse(hasActiveLanguageServers(MATCH_ALL));

		editor1 = openEditor(testFile1);
		assertTrue(hasActiveLanguageServers(testFile1, MATCH_ALL));

		waitForCondition(5_000, () -> hasActiveLanguageServers(MATCH_ALL));
		assertTrue(hasActiveLanguageServers(MATCH_ALL));
	}

	@Test
	public void testCreateNewLSAfterInitialProjectGotDeleted() throws Exception {
		var testFile1 = createUniqueTestFile(project, "");

		openEditor(testFile1);
		assertTrue(hasActiveLanguageServers(testFile1, MATCH_ALL));
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());

		var wrappers = getLSWrappers(testFile1, MATCH_ALL);
		var wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		UI.getActivePage().closeAllEditors(false);
		waitForAndAssertCondition(5_000, () -> !MockLanguageServer.INSTANCE.isRunning());

		project.delete(true, true, new NullProgressMonitor());

		project = createProject("LanguageServiceAccessorTest2" + System.currentTimeMillis());
		var testFile2 = createUniqueTestFile(project, "");

		openEditor(testFile2);
		assertTrue(hasActiveLanguageServers(testFile2, MATCH_ALL));
		waitForAndAssertCondition(5_000, () -> MockLanguageServer.INSTANCE.isRunning());

		wrappers = getLSWrappers(testFile2, MATCH_ALL);
		var wrapper2 = wrappers.iterator().next();
		assertTrue(wrapper2.isActive());

		assertTrue(wrapper1 != wrapper2);
	}

	/**
	 * TODO this test case is broken. The {@link MockLanguageServerMultiRootFolders#INSTANCE} is never running,
	 * There is no code that calls {@link MockLanguageServerMultiRootFolders#addRemoteProxy} method which would
	 * put the server in the running state.
	 */
	@Test
	public void testReuseMultirootFolderLSAfterInitialProjectGotDeleted() throws Exception {
		var testFile1 = createUniqueTestFile(project, "lsptWithMultiRoot", "");

		openEditor(testFile1);
		assertTrue(hasActiveLanguageServers(testFile1, MATCH_ALL));
		// FIXME waitForCondition(5_000, () -> MockLanguageServerMultiRootFolders.INSTANCE.isRunning());

		var wrappers = getLSWrappers(testFile1, MATCH_ALL);
		var wrapper1 = wrappers.iterator().next();
		assertTrue(wrapper1.isActive());

		UI.getActivePage().closeAllEditors(false);
		waitForAndAssertCondition(5_000, () -> !MockLanguageServerMultiRootFolders.INSTANCE.isRunning());

		project.delete(true, true, new NullProgressMonitor());

		project = createProject("LanguageServiceAccessorTest2" + System.currentTimeMillis());
		var testFile2 = createUniqueTestFile(project, "lsptWithMultiRoot", "");

		openEditor(testFile2);
		assertTrue(hasActiveLanguageServers(testFile2, MATCH_ALL));
		// FIXME waitForAndAssertCondition(5_000, () -> MockLanguageServerMultiRootFolders.INSTANCE.isRunning());

		wrappers = getLSWrappers(testFile2, MATCH_ALL);
		var wrapper2 = wrappers.iterator().next();
		assertTrue(wrapper2.isActive());

		assertSame(wrapper1, wrapper2);
	}

	@Test
	public void testDontRestartUnrelatedLSForFileFromSameProject() throws Exception {
		var testFile1 = createUniqueTestFile(project, "");
		var testFile2 = createUniqueTestFile(project, "lspt-different", "");

		var wrappers1 = getLSWrappers(testFile1, MATCH_ALL);
		assertEquals(1, wrappers1.size());

		var wrapper1 = wrappers1.iterator().next();
		assertTrue(wrapper1.isActive());

		wrapper1.disconnect(testFile1.getLocationURI());
		assertFalse(wrapper1.isActive());

		var wrappers2 = getLSWrappers(testFile2, MATCH_ALL);
		assertEquals(1, wrappers2.size());

		var wrapper2 = wrappers2.iterator().next();
		assertTrue(wrapper2.isActive());

		// make sure the language server for testFile1 (which is unrelated to testFile2 is not started again)
		assertFalse(wrapper1.isActive());

		wrapper2.disconnect(testFile2.getLocationURI());
	}

	@Test
	public void testLastDocumentDisconnectedTimeoutManualStop() throws Exception {
		var testFile = createUniqueTestFile(project, "lsptWithLastDocumentDisconnectedTimeout", "");

		var wrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, wrappers.size());

		var wrapper = wrappers.iterator().next();
		assertTrue(wrapper.isActive());

		wrapper.disconnect(testFile.getLocationURI());
		assertTrue(wrapper.isActive());

		wrapper.stop();
		assertFalse(wrapper.isActive());
	}

	@Test
	public void testLastDocumentDisconnectedTimeoutTimerStop() throws Exception {
		var testFile = createUniqueTestFile(project, "lsptWithLastDocumentDisconnectedTimeout", "");

		var wrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, wrappers.size());

		var wrapper = wrappers.iterator().next();
		assertTrue(wrapper.isActive());

		wrapper.disconnect(testFile.getLocationURI());
		assertTrue(wrapper.isActive());

		waitForAndAssertCondition(5_000, () -> !wrapper.isActive());
	}

	@Test
	public void testLastDocumentDisconnectedTimeoutZero() throws Exception {
		var testFile = createUniqueTestFile(project, "");

		var wrappers = getLSWrappers(testFile, MATCH_ALL);
		assertEquals(1, wrappers.size());

		var wrapper = wrappers.iterator().next();
		assertTrue(wrapper.isActive());

		wrapper.disconnect(testFile.getLocationURI());
		assertFalse(wrapper.isActive());
	}

	@Test
	public void testLanguageServerHierarchy_moreSpecializedFirst() throws Exception {
		// file with a content-type and a parent, each associated to one LS
		var testFile = createUniqueTestFile(project, "lsptchild", "");
		var servers = getLSWrappers(testFile, MATCH_ALL);
		var iterator = servers.iterator();
		assertEquals("org.eclipse.lsp4e.test.server2", iterator.next().serverDefinition.id);
		assertEquals("org.eclipse.lsp4e.test.server", iterator.next().serverDefinition.id);
	}

	@Test
	public void testLanguageServerHierarchy_parentContentTypeUsed() throws Exception {
		// file with a content-type whose parent (only) is associated to one LS
		var testFile = createUniqueTestFile(project, "lsptchildNoLS", "");
		var servers = getLSWrappers(testFile, MATCH_ALL);
		var iterator = servers.iterator();
		assertEquals("org.eclipse.lsp4e.test.server", iterator.next().serverDefinition.id);
		assertFalse(iterator.hasNext(), "Should only be a single LS");
	}

	@Test
	public void testLanguageServerEnablement() throws Exception {
		final var serverId = ContentTypeToLanguageServerDefinitionTest.SERVER_TO_DISABLE;
		final var prefKey = serverId + "/" + "org.eclipse.lsp4e.test.content-type-disabled";
		LanguageServerPlugin.getDefault().getPreferenceStore().setValue(prefKey, Boolean.FALSE.toString());

		var disabledFile = createUniqueTestFile(project, "lspt-disabled", "");
		assertFalse(getLSWrappers(disabledFile, MATCH_ALL).stream().anyMatch(w -> w.serverDefinition.id.equals(serverId)));

		var enabledFile = createUniqueTestFile(project, "lspt-enabled", "");
		assertTrue(getLSWrappers(enabledFile, MATCH_ALL).stream().anyMatch(w -> w.serverDefinition.id.equals(serverId)));

		LanguageServerPlugin.getDefault().getPreferenceStore().setValue(prefKey, Boolean.TRUE.toString());

		assertTrue(getLSWrappers(disabledFile, MATCH_ALL).stream().anyMatch(w -> w.serverDefinition.id.equals(serverId)));
		assertTrue(getLSWrappers(enabledFile, MATCH_ALL).stream().anyMatch(w -> w.serverDefinition.id.equals(serverId)));
	}

	@Test
	public void testLanguageServerEnablementTester() throws Exception {
		final var serverId = ContentTypeToLanguageServerDefinitionTest.SERVER_TO_DISABLE;

		var file = createUniqueTestFile(project, "lspt-tester", "");
		assertTrue(getLSWrappers(file, MATCH_ALL).isEmpty());
		MappingEnablementTester.enabled = true;

		var wrappers = getLSWrappers(file, MATCH_ALL);
		assertEquals(1, wrappers.size());
		assertEquals(serverId, wrappers.iterator().next().serverDefinition.id);
	}

	@Test
	public void testLSforExternalThenLocalFile() throws Exception {
		var wb = UI.getActiveWindow();
		var local = createTempFile("testLSforExternalThenLocalFile", ".lspt");
		var editor = (ITextEditor) IDE.openEditorOnFileStore(wb.getActivePage(), EFS.getStore(local.toURI()));

		Predicate<ServerCapabilities> hasHoverCapabilities = capabilities -> {
			var hoverProvider = capabilities.getHoverProvider();
			return hoverProvider.isLeft() ? hoverProvider.getLeft() : hoverProvider.getRight() != null;
		};

		final IDocument externalDocument = getTextViewer(editor).getDocument();
		assertTrue(hasActiveLanguageServers(externalDocument, hasHoverCapabilities));
		wb.getActivePage().closeAllEditors(false);

		// opening another file should either reuse the LS or spawn another one, but not both
		final IDocument internalDocument = openTextViewer(createUniqueTestFile(project, "")).getDocument();
		assertTrue(hasActiveLanguageServers(internalDocument, hasHoverCapabilities));
	}

	@Test
	public void testSingletonLS() throws Exception {
		var testFile1 = createFile(project, "shouldUseSingletonLS.lsp-singletonLS", "");
		IDocument document1 = getDocument(testFile1);
		assertNotNull(document1);
		var languageServers = getLSWrappers(LSPEclipseUtils.getFile(document1), MATCH_ALL);

		var project2 = createProject("project2");
		var testFile2 = createFile(project2, "shouldUseSingletonLS2.lsp-singletonLS", "");
		IDocument document2 = getDocument(testFile2);
		assertNotNull(document2);
		var languageServers2 = getLSWrappers(LSPEclipseUtils.getFile(document2), MATCH_ALL);
		assertEquals(1, languageServers.size());
		assertEquals(languageServers, languageServers2);
	}
}
