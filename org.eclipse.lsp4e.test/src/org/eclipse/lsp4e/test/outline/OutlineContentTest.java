/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.lsp4e.test.outline;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForCondition;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.outline.CNFOutlinePage;
import org.eclipse.lsp4e.outline.EditorToOutlineAdapterFactory;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.junit.jupiter.api.Test;

public class OutlineContentTest extends AbstractTestWithProject {

	@Test
	public void testExternalFile() throws CoreException, IOException {
		var testFile = TestUtils.createTempFile("test" + System.currentTimeMillis(), ".lspt");

		try (FileWriter fileWriter =  new FileWriter(testFile)) {
			fileWriter.write("content\n does\n not\n matter\n but needs to cover the ranges described below");
		}

		final var symbolCow = new DocumentSymbol("cow", SymbolKind.Constant,
				new Range(new Position(0, 0), new Position(0, 2)),
				new Range(new Position(0, 0), new Position(0, 2)));

		MockLanguageServer.INSTANCE.setDocumentSymbols(symbolCow);

		final var editor = (ITextEditor) TestUtils.openExternalFileInEditor(testFile);

		final var outlinePage = (CNFOutlinePage) new EditorToOutlineAdapterFactory().getAdapter(editor, IContentOutlinePage.class);
		final var shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
		shell.setLayout(new FillLayout());
		outlinePage.createControl(shell);
		shell.open();
		final var tree = (Tree) outlinePage.getControl();

		// wait for tree to render
		waitForAndAssertCondition(5_000, tree.getDisplay(), //
				() -> List.of(symbolCow) //
						.equals(Arrays.stream(tree.getItems())
								.map(e -> ((DocumentSymbolWithURI) e.getData()).symbol)
								.toList()) //
		);

		shell.close();
	}

	@Test
	public void testExternalFileOpenedOnFileStore() throws CoreException, IOException {
		var testFile = TestUtils.createTempFile("test" + System.currentTimeMillis(), ".lspt");

		try (FileWriter fileWriter =  new FileWriter(testFile)) {
			fileWriter.write("content\n does\n not\n matter\n but needs to cover the ranges described below");
		}

		final var symbolCow = new DocumentSymbol("cow", SymbolKind.Constant,
				new Range(new Position(0, 0), new Position(0, 2)),
				new Range(new Position(0, 0), new Position(0, 2)));

		MockLanguageServer.INSTANCE.setDocumentSymbols(symbolCow);

		final var editor = (ITextEditor) TestUtils.openExternalFileOnFileStore(testFile);

		final var outlinePage = (CNFOutlinePage) new EditorToOutlineAdapterFactory().getAdapter(editor, IContentOutlinePage.class);
		final var shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
		shell.setLayout(new FillLayout());
		outlinePage.createControl(shell);
		shell.open();
		final var tree = (Tree) outlinePage.getControl();

		// wait for tree to render
		waitForAndAssertCondition(5_000, tree.getDisplay(), //
				() -> List.of(symbolCow) //
						.equals(Arrays.stream(tree.getItems())
								.map(e -> ((DocumentSymbolWithURI) e.getData()).symbol)
								.toList()) //
		);

		shell.close();
	}

	@Test
	public void testOutlineSorting() throws CoreException {
		IFile testFile = TestUtils.createUniqueTestFile(project, "content\n does\n not\n matter\n but needs to cover the ranges described below");
		final var symbolCow = new DocumentSymbol("cow", SymbolKind.Constant,
				new Range(new Position(0, 0), new Position(0, 2)),
				new Range(new Position(0, 0), new Position(0, 2)));
		final var symbolFox = new DocumentSymbol("fox", SymbolKind.Constant,
				new Range(new Position(1, 0), new Position(1, 2)),
				new Range(new Position(1, 0), new Position(1, 2)));
		final var symbolCat = new DocumentSymbol("cat", SymbolKind.Constant,
				new Range(new Position(2, 0), new Position(2, 2)),
				new Range(new Position(2, 0), new Position(2, 2)));

		MockLanguageServer.INSTANCE.setDocumentSymbols(symbolCow, symbolFox, symbolCat);

		// ensure outline sorting is disabled
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
		prefs.putBoolean(CNFOutlinePage.SORT_OUTLINE_PREFERENCE, false);

		final var editor = (ITextEditor) TestUtils.openEditor(testFile);
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile, request -> true).iterator().next();

		final var outlinePage = new CNFOutlinePage(wrapper, editor);
		final var shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
		shell.setLayout(new FillLayout());
		outlinePage.createControl(shell);
		shell.open();
		final var tree = (Tree) outlinePage.getControl();

		// wait for tree to render
		waitForAndAssertCondition(5_000, tree.getDisplay(), //
				() -> List.of(symbolCow, symbolFox, symbolCat) //
						.equals(Arrays.stream(tree.getItems())
								.map(e -> ((DocumentSymbolWithURI) e.getData()).symbol)
								.toList()) //
		);

		// enable outline sorting
		prefs.putBoolean(CNFOutlinePage.SORT_OUTLINE_PREFERENCE, true);

		// wait for tree being sorted
		waitForAndAssertCondition(5_000, tree.getDisplay(), //
				() -> List.of(symbolCat, symbolCow, symbolFox) //
						.equals(Arrays.stream(tree.getItems())
								.map(e -> ((DocumentSymbolWithURI) e.getData()).symbol)
								.toList()) //
		);

		shell.close();
	}

	@Test
	public void testNodeRemainExpandedUponSelection() throws CoreException {
		IFile testFile = TestUtils.createUniqueTestFile(project, "a(b())");
		MockLanguageServer.INSTANCE.setDocumentSymbols(
				new DocumentSymbol("a", SymbolKind.Constant, new Range(new Position(0, 0), new Position(0, 6)),
						new Range(new Position(0, 0), new Position(0, 1)), "",
						List.of(new DocumentSymbol("b", SymbolKind.Constant,
								new Range(new Position(0, 2), new Position(0, 5)),
								new Range(new Position(0, 2), new Position(0, 3))))));
		final var editor = (ITextEditor) TestUtils.openEditor(testFile);
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile, request -> true).iterator().next();

		final var outlinePage = new CNFOutlinePage(wrapper, editor);
		final var shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
		shell.setLayout(new FillLayout());
		outlinePage.createControl(shell);
		shell.open();
		Tree tree = (Tree) outlinePage.getControl();
		DisplayHelper.sleep(tree.getDisplay(), 500);

		editor.getSelectionProvider().setSelection(new TextSelection(4, 0));
		waitForAndAssertCondition(2_000, tree.getDisplay(), //
				() -> itemBselectedAndVisibile(tree) //
		);

		editor.getSelectionProvider().setSelection(new TextSelection(3, 0));

		// ensure that selection remains intact for 2 seconds at least (i.e. not lost)
		assertFalse(waitForCondition(2_000, tree.getDisplay(), //
				() -> !itemBselectedAndVisibile(tree) //
		));

		shell.close();
	}

	@Test
	public void testNodeRemainExpandedUponModification() throws CoreException, BadLocationException {
		IFile testFile = TestUtils.createUniqueTestFile(project, "a(b())");
		MockLanguageServer.INSTANCE.setDocumentSymbols(
				new DocumentSymbol("a", SymbolKind.Constant, new Range(new Position(0, 0), new Position(0, 6)),
						new Range(new Position(0, 0), new Position(0, 1)), "",
						List.of(new DocumentSymbol("b", SymbolKind.Constant,
								new Range(new Position(0, 2), new Position(0, 5)),
								new Range(new Position(0, 2), new Position(0, 3))))));
		final var editor = (ITextEditor) TestUtils.openEditor(testFile);
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(testFile, request -> true).iterator().next();

		final var outlinePage = new CNFOutlinePage(wrapper, editor);
		final var shell = new Shell(editor.getEditorSite().getWorkbenchWindow().getShell());
		shell.setLayout(new FillLayout());
		outlinePage.createControl(shell);
		shell.open();
		final var tree = (Tree) outlinePage.getControl();
		DisplayHelper.sleep(tree.getDisplay(), 500);

		editor.getSelectionProvider().setSelection(new TextSelection(4, 0));
		waitForAndAssertCondition(2_000, tree.getDisplay(), //
				() -> tree.getItems().length > 0 && tree.getItem(0).getExpanded() //
		);

		IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
		editor.selectAndReveal(document.getLength(), 0);
		document.replace(document.getLength(), 0, "   ");

		// ensure that tree remains expanded (for at least 2 seconds)
		assertFalse(waitForCondition(2_000, tree.getDisplay(), //
				() -> !tree.getItem(0).getExpanded() //
		));

		shell.close();
	}

	private boolean itemBselectedAndVisibile(Tree tree) {
		if (tree.getSelection().length == 0) {
			return false;
		}
		TreeItem selection = tree.getSelection()[0];
		return selection != null && selection.getText().equals("b") && selection.getParentItem().getExpanded();
	}
}
