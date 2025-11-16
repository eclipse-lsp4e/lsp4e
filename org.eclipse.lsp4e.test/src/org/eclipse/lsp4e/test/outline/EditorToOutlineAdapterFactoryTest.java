/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Markus Ofterdinger (SAP SE) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.outline;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class EditorToOutlineAdapterFactoryTest extends AbstractTestWithProject {

	private static ContentOutline outline;

	@BeforeAll
	public static void setUpBeforeClass() {
		// look for content outline in current workbench, could be null
		IViewPart viewPart = UI.getActivePage().findView("org.eclipse.ui.views.ContentOutline"); //$NON-NLS-1$

		// implicitly checks for null
		if (viewPart instanceof ContentOutline thisOutline) {
			outline = thisOutline;
		}

		assertNotNull(outline);
	}

	@Test
	public void testGetAdapter() throws CoreException {
		IFile testFile = TestUtils.createUniqueTestFile(project, "Hello World !!");
		outline.partClosed(outline);

		MockLanguageServer.INSTANCE.setTimeToProceedQueries(500);
		TestUtils.openEditor(testFile);

		long beginOpenOutline = System.currentTimeMillis();
		outline.partOpened(outline);
		long endOpenOutline = System.currentTimeMillis();
		long durationOpenOutline = endOpenOutline - beginOpenOutline;
		assertTrue(durationOpenOutline <= 50, String.format("Open outline took longer than 50ms: %d", durationOpenOutline));

		DisplayHelper.sleep(Display.getCurrent(), 1000); // leave time for outline to be refreshed when LS is ready.
		String pageClassName = outline.getCurrentPage().getClass().getCanonicalName();
		assertTrue(pageClassName.contains("lsp4e"), "Outline page class is not as expected: " + pageClassName);
	}
}
