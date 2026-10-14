/*******************************************************************************
 * Copyright (c) 2017, 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.codeactions;

import static org.eclipse.lsp4e.test.utils.CodeActionTestUtils.assertDiagnostics;
import static org.eclipse.lsp4e.test.utils.CodeActionTestUtils.assertResolution;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.NoErrorLoggedRule;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CodeActionTests extends AbstractTestWithProject {

	public final @RegisterExtension NoErrorLoggedRule noErrorLoggedRule = new NoErrorLoggedRule();

	@Test
	public void testCodeActionsClientCommandForTextEdit(MockLanguageServerFactory factory) throws CoreException {
		factory.withConfiguration((idx, server)-> {
			server.setCodeActions(List.of(Either.forLeft(new Command(
					"fixme",
					"edit",
					List.of(
							new TextEdit(
									new Range(new Position(0, 0), new Position(0, 5)),
									"fixed"))
					)
					)
					));
			server.setDiagnostics(List.of(
					new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		});
		
		IFile f = TestUtils.createUniqueTestFile(project, "error");
		
		final var editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			IMarker m = assertDiagnostics(f, "error", "fixme");
			assertResolution(editor, m, "fixed");
		} finally {
			editor.close(false);
		}
	}

	@Test
	public void testCodeActionsClientCommandForWorkspaceEdit(MockLanguageServerFactory factory) throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");
		factory.withConfiguration((idx, server)-> {
			final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
			final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
			server.setCodeActions(List
					.of(Either.forLeft(new Command(
							"fixme",
							"edit",
							List.of(wEdit))
							)
							));
			server.setDiagnostics(List.of(
					new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		});
		

		final var editor = (AbstractTextEditor)TestUtils.openEditor(f);

		IMarker m = assertDiagnostics(f, "error", "fixme");
		assertResolution(editor, m, "fixed");
	}

	private void checkCompletionContent(final Table completionProposalList) {
		// should be instantaneous, but happens to go asynchronous on CI so let's allow a wait
		waitForAndAssertCondition("No item found", 100, () -> completionProposalList.getItemCount() > 0);
		assertEquals(1, completionProposalList.getItemCount());
		final TableItem quickAssistItem = completionProposalList.getItem(0);
		assertTrue(quickAssistItem.getText().contains("fixme"), "Missing quick assist proposal"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	public void testCodeActionsQuickAssist(MockLanguageServerFactory factory) throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");

		factory.withConfiguration((idx, server)-> {
			final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
			final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
			server.setCodeActions(List
					.of(Either.forLeft(new Command(
							"fixme",
							"edit",
							List.of(wEdit))
							)
							));
		});
		
		final var editor = (AbstractTextEditor)TestUtils.openEditor(f);
		final Set<Shell> beforeShells = Arrays.stream(editor.getSite().getShell().getDisplay().getShells()).filter(Shell::isVisible).collect(Collectors.toSet());
		editor.selectAndReveal(3, 0);
		final var action = (TextOperationAction) editor.getAction(ITextEditorActionConstants.QUICK_ASSIST);
		action.update();
		action.run();
		Shell completionShell= TestUtils.findNewShell(beforeShells, editor.getSite().getShell().getDisplay());
		final Table completionProposalList = TestUtils.findCompletionSelectionControl(completionShell);
		checkCompletionContent(completionProposalList);
	}

	@Test
	public void testSlowCodeActionsQuickAssist(MockLanguageServerFactory factory) throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");

		factory.withConfiguration((idx, server)-> {
			final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
			final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
			server.setCodeActions(List
					.of(Either.forLeft(new Command(
							"fixme",
							"edit",
							List.of(wEdit))
							)
							));
			server.setTimeToProceedQueries(1000);
		});
		
		final var editor = (AbstractTextEditor)TestUtils.openEditor(f);
		final Set<Shell> beforeShells = Arrays.stream(editor.getSite().getShell().getDisplay().getShells()).filter(Shell::isVisible).collect(Collectors.toSet());
		editor.selectAndReveal(3, 0);
		final var action = (TextOperationAction) editor.getAction(ITextEditorActionConstants.QUICK_ASSIST);
		action.update();
		action.run();
		waitForAndAssertCondition(3000, () -> {
			Shell completionShell= TestUtils.findNewShell(beforeShells, editor.getSite().getShell().getDisplay());
			if (completionShell == null) {
				return false;
			}
			final Table completionProposalList = TestUtils.findCompletionSelectionControl(completionShell);
			return completionProposalList.getItemCount() == 1 && "fixme".equals(completionProposalList.getItem(0).getText());
		});
		assertEquals(1, factory.getServer().getTextDocumentService().codeActionRequests);
	}

	@Test
	public void testCodeActionLiteralWorkspaceEdit(MockLanguageServerFactory factory) throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");
		factory.withConfiguration((idx, server)-> {
			final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
			final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
			final var codeAction = new CodeAction("fixme");
			codeAction.setEdit(wEdit);
			server.setCodeActions(List.of(Either.forRight(codeAction)));
			server.setDiagnostics(List.of(
					new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		});

		final var editor = (AbstractTextEditor)TestUtils.openEditor(f);
		IMarker m = assertDiagnostics(f, "error", "fixme");
		assertResolution(editor, m, "fixed");
	}

	@Test
	public void testNoCodeActionOnReadOnlySource(MockLanguageServerFactory factory) throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");
		f.setResourceAttributes(new ResourceAttributes() {
			@Override
			public boolean isReadOnly() {
				return true;
			}
		});

		factory.withConfiguration((idx, server)-> {
			final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
			final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
			final var codeAction = new CodeAction("fixme");
			codeAction.setEdit(wEdit);
			server.setCodeActions(List.of(Either.forRight(codeAction)));
			server.setDiagnostics(List.of(
					new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		});
		TestUtils.openEditor(f);
		assertDiagnostics(f, "error", "fixme", false);
	}

	@Test
	public void testCodeActionLiteralWithClientCommand(MockLanguageServerFactory factory) throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");

		factory.withConfiguration((idx, server)-> {
			final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
			final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
			final var codeAction = new CodeAction("fixme");
			codeAction.setCommand(new Command("editCommand", "mockEditCommand", List.of(wEdit)));
			server.setCodeActions(List.of(Either.forRight(codeAction)));
			server.setDiagnostics(List.of(
					new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		});
		final var editor = (AbstractTextEditor)TestUtils.openEditor(f);
		IMarker m = assertDiagnostics(f, "error", "fixme");
		assertResolution(editor, m, "fixed");
	}

	@Test
	public void testCodeActionWorkspaceEditlWithDifferentURI(MockLanguageServerFactory factory) throws CoreException {
		IFile sourceFile = TestUtils.createUniqueTestFile(project, "error");
		IFile targetFile = TestUtils.createUniqueTestFile(project, "fixme");

		factory.withConfiguration((idx, server)-> {
			// create a diagnostic on the sourceFile with a code action
			// that changes the targetFile
			final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
			final var wEdit = new WorkspaceEdit(Collections.singletonMap(targetFile.getLocationURI().toString(), List.of(tEdit)));
			final var codeAction = new CodeAction("fixme");
			codeAction.setCommand(new Command("editCommand", "mockEditCommand", List.of(wEdit)));
			server.setCodeActions(List.of(Either.forRight(codeAction)));
			server.setDiagnostics(List.of(
					new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		});

		TestUtils.openEditor(sourceFile);
		IMarker m = assertDiagnostics(sourceFile, "error", "fixme");

		// Apply and check the resolution
		assertResolution(targetFile, m, "fixed");

		// Double check that the editor is opened and active for the targetFile
		// as result of the resolution
		IEditorPart activeEditorPart = TestUtils.getActiveEditor();
		assertTrue(activeEditorPart instanceof AbstractTextEditor);
		assertEquals(new FileEditorInput(targetFile), ((AbstractTextEditor)activeEditorPart).getEditorInput());
	}
}
