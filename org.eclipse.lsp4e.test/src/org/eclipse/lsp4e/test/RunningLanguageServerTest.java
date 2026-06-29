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

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.tests.mock.MockServerState;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.jupiter.api.Test;

public class RunningLanguageServerTest extends AbstractTestWithProject {

	/**
	 * checks if language servers get started and shutdown correctly if opening and
	 * closing the same file/editor multiple times
	 */
	@Test
	public void testOpenCloseLanguageServer(MockLanguageServerFactory factory) throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");

		// open and close the editor several times
		int iterations = 10;
		for(int i = 0; i < iterations; i++) {
			IEditorPart editor = TestUtils.openEditor(testFile);

			assertFalse(LanguageServiceAccessor.getLSWrappers(testFile, capabilities -> true).isEmpty());
			
			int serverIndex = i;
			waitForAndAssertCondition("MockLanguageServer should be started for iteration #" + i, 5_000,
					() -> factory.getServerCount() == serverIndex +1);

			((AbstractTextEditor)editor).close(false);
			waitForAndAssertCondition("MockLanguageServer should be stopped after iteration #" + i, 5_000,
					() -> factory.getServers().get(serverIndex).getState() != MockServerState.RUNNING );
		}
		assertEquals(iterations, factory.getServerCount());
	}

	@Test
	public void testDisabledLanguageServer(MockLanguageServerFactory factory) throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "lspt-disabled", "");

		ContentTypeToLanguageServerDefinition lsDefinition = TestUtils.getDisabledLS();
		lsDefinition.setUserEnabled(false);
		LanguageServiceAccessor.disableLanguageServerContentType(lsDefinition);

		TestUtils.openEditor(testFile);
		@NonNull List<LanguageServerWrapper> initializedLanguageServers = LanguageServiceAccessor
				.getLSWrappers(testFile, capabilities -> true);
		assertNotNull(initializedLanguageServers);
		assertEquals(0, initializedLanguageServers.size(),
				"language server should not be started because it is disabled");

		lsDefinition.setUserEnabled(true);
		LanguageServiceAccessor.enableLanguageServerContentType(lsDefinition, TestUtils.getEditors());

		waitForAndAssertCondition("language server should be started", 5_000,
				() -> factory.getServerCount() == 1);
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
	public void testDelayedStopDoesntCauseFreeze(MockLanguageServerFactory factory) throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		IWorkbenchPage page = editor.getSite().getPage();
		factory.getServer().setTimeToProceedQueries(1100);
		long before = System.currentTimeMillis();
		page.closeEditor(editor, false);
		assertTrue(System.currentTimeMillis() - before < 1000);
	}
}
