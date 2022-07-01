/*******************************************************************************
 * Copyright (c) 2017, 2018 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Martin Lippert (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;


public class RunningLanguageServerTest {

	private IProject project;

	@Rule public AllCleanRule clear = new AllCleanRule();

	@Before
	public void setUp() throws CoreException {
		project =  TestUtils.createProject("StartStopServerTest"+System.currentTimeMillis());
	}

	/**
	 * checks if language servers get started and shutdown correctly if opening and
	 * closing the same file/editor multiple times
	 */
	@Test
	public void testOpenCloseLanguageServer() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		Display display = Display.getCurrent();

		// open and close the editor several times
		for(int i = 0; i < 10; i++) {
			IEditorPart editor = TestUtils.openEditor(testFile);
			LanguageServiceAccessor.getInitializedLanguageServers(testFile, capabilities -> Boolean.TRUE).iterator()
					.next();
			assertTrue("language server is started for iteration #" + i,
					new LSDisplayHelper(() -> MockLanguageServer.INSTANCE.isRunning()).waitForCondition(display, 5000));

			((AbstractTextEditor)editor).close(false);
			assertTrue("language server is closed for iteration #" + i,
					new LSDisplayHelper(() -> !MockLanguageServer.INSTANCE.isRunning()).waitForCondition(display, 5000));
		}
	}

	@Test
	public void testDisabledLanguageServer() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "lspt-disabled", "");
		Display display = Display.getCurrent();

		ContentTypeToLanguageServerDefinition lsDefinition = TestUtils.getDisabledLS();
		lsDefinition.setUserEnabled(false);
		LanguageServiceAccessor.disableLanguageServerContentType(lsDefinition);

		TestUtils.openEditor(testFile);
		List<CompletableFuture<LanguageServer>> initializedLanguageServers = LanguageServiceAccessor
				.getInitializedLanguageServers(testFile, capabilities -> Boolean.TRUE);
		assertNotNull(initializedLanguageServers);
		assertEquals("language server should not be started because it is disabled", 0,
				initializedLanguageServers.size());

		lsDefinition.setUserEnabled(true);
		LanguageServiceAccessor.enableLanguageServerContentType(lsDefinition, TestUtils.getEditors());

		assertTrue("language server should be started",
				new LSDisplayHelper(() -> MockLanguageServer.INSTANCE.isRunning()).waitForCondition(display, 5000));
	}

	@Test
	public void testBug535887DisabledWithMultipleOpenFiles() throws CoreException {
		ContentTypeToLanguageServerDefinition lsDefinition = TestUtils.getDisabledLS();
		lsDefinition.setUserEnabled(true);
		LanguageServiceAccessor.enableLanguageServerContentType(lsDefinition, TestUtils.getEditors());

		IFile testFile1 = TestUtils.createUniqueTestFile(project, "lspt-disabled", "");
		IFile testFile2 = TestUtils.createUniqueTestFile(project, "lspt-disabled", "");
		TestUtils.openEditor(testFile1);
		TestUtils.openEditor(testFile2);
		lsDefinition.setUserEnabled(false);
		LanguageServiceAccessor.disableLanguageServerContentType(lsDefinition);
	}

	@Test
	public void testDelayedStopDoesntCauseFreeze() throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		IWorkbenchPage page = editor.getSite().getPage();
		MockLanguageServer.INSTANCE.setTimeToProceedQueries(10000);
		long before = System.currentTimeMillis();
		page.closeEditor(editor, false);
		assertTrue(System.currentTimeMillis() - before < 1000);
	}

}
