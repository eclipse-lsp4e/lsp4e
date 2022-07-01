/*******************************************************************************
 * Copyright (c) 2022 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Evolution AG) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.LSDisplayHelper;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class DocumentWillSaveWaitUntilTest {

	@Rule public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project =  TestUtils.createProject(getClass().getName() + System.currentTimeMillis());
	}

	private List<TextEdit> createSingleTextEditAtFileStart(String newText) {
		TextEdit textEdit = new TextEdit();
		textEdit.setRange(new Range(new Position(0, 0), new Position(0, newText.length())));
		textEdit.setNewText(newText);
		return Collections.singletonList(textEdit);
	}

	@Test
	public void testSave() throws Exception {
		String oldText = "Hello";
		String newText = "hello";


		MockLanguageServer.INSTANCE.setWillSaveWaitUntil(createSingleTextEditAtFileStart(newText));

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		// Force LS to initialize and open file
		LanguageServiceAccessor.getLanguageServers(LSPEclipseUtils.getDocument(testFile), capabilites -> Boolean.TRUE);

		// simulate change in file
		viewer.getDocument().replace(0, 0, oldText);
		editor.doSave(new NullProgressMonitor());

		// wait for will save wait until to apply the text edit
		Assert.assertTrue("Text has not been lowercased", new LSDisplayHelper(() -> {
			try {
				return newText.equals(viewer.getDocument().get(0, newText.length()));
			} catch (BadLocationException e) {
				return false;
			}
		}).waitForCondition(Display.getCurrent(), 2000));
	}
}
