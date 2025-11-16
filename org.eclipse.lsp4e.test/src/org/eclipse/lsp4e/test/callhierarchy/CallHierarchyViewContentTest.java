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
package org.eclipse.lsp4e.test.callhierarchy;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.callhierarchy.CallHierarchyContentProvider;
import org.eclipse.lsp4e.callhierarchy.CallHierarchyLabelProvider;
import org.eclipse.lsp4e.callhierarchy.CallHierarchyViewTreeNode;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.views.HierarchyViewInput;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * UI-level test that opens a file, initializes Call Hierarchy and verifies that
 * the view model contains expected nodes. Uses the mock LS.
 */
public class CallHierarchyViewContentTest extends AbstractTestWithProject {

	@Override
	@BeforeEach
	public void setUpProject(TestInfo testInfo) throws Exception {
		super.setUpProject(testInfo);
		// Ensure the mock server advertises callHierarchyProvider
		MockLanguageServer.reset(() -> {
			ServerCapabilities caps = MockLanguageServer.defaultServerCapabilities();
			caps.setCallHierarchyProvider(Boolean.TRUE);
			return caps;
		});
	}

	@Test
	public void testCallHierarchyShowsCalleeAndCaller() throws Exception {
		IProject p = project;
		IFile file = TestUtils.createUniqueTestFile(p, "// mock content for call hierarchy\nfunction f(){}\n");

		// Open the file in Generic Editor to bind the LS
		var editor = TestUtils.openEditor(file);
		IDocument document = LSPEclipseUtils.getDocument(editor.getEditorInput());
		assertTrue(document != null);

		// Create a lightweight view embedding a TreeViewer with the real content
		// provider
		Shell shell = new Shell(editor.getSite().getShell());
		shell.setLayout(new FillLayout());
		TreeViewer viewer = new TreeViewer(shell);
		viewer.setContentProvider(new CallHierarchyContentProvider());
		viewer.setLabelProvider(new DelegatingStyledCellLabelProvider(new CallHierarchyLabelProvider()));
		shell.open();

		// Initialize input similar to CallHierarchyView.initialize
		viewer.setInput(new HierarchyViewInput(document, 0));

		// Wait until the placeholder ("Finding callers ...") is replaced by a node
		waitForAndAssertCondition(5_000, shell.getDisplay(), () -> {
			Tree tree = viewer.getTree();
			if (tree.getItemCount() == 0) return false;
			Object data = tree.getItem(0).getData();
			return data instanceof CallHierarchyViewTreeNode;
		});

		Tree tree = viewer.getTree();
		TreeItem root = tree.getItem(0);
		Object rootData = root.getData();
		assertInstanceOf(CallHierarchyViewTreeNode.class, rootData, "Expected CallHierarchyViewTreeNode root");
		var rootNode = (CallHierarchyViewTreeNode) rootData;
		assertEquals("callee", rootNode.getCallContainer().getName());

		// Expand and wait for children
		viewer.expandToLevel(2);
		waitForAndAssertCondition(5_000, shell.getDisplay(), () -> {
			return root.getItemCount() > 0 && root.getItem(0).getData() instanceof CallHierarchyViewTreeNode;
		});
		Object childData = root.getItem(0).getData();
		assertTrue(childData instanceof CallHierarchyViewTreeNode);
		var childNode = (CallHierarchyViewTreeNode) childData;
		assertEquals("caller", childNode.getCallContainer().getName());

		shell.close();
	}
}
