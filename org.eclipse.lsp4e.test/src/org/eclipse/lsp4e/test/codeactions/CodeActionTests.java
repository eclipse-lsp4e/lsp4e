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

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.NoErrorLoggedRule;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.TextOperationAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class CodeActionTests extends AbstractTestWithProject {

	public final @RegisterExtension NoErrorLoggedRule noErrorLoggedRule = new NoErrorLoggedRule();

	@Test
	public void testCodeActionsClientCommandForTextEdit() throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");
		MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forLeft(new Command(
				"fixme",
				"edit",
				List.of(
					new TextEdit(
							new Range(new Position(0, 0), new Position(0, 5)),
							"fixed"))
				)
			)
		));
		MockLanguageServer.INSTANCE.setDiagnostics(List.of(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		final var editor = (AbstractTextEditor)TestUtils.openEditor(f);
		try {
			IMarker m = assertDiagnostics(f, "error", "fixme");
			assertResolution(editor, m, "fixed");
		} finally {
			editor.close(false);
		}
	}

	@Test
	public void testCodeActionsClientCommandForWorkspaceEdit() throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");

		final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
		MockLanguageServer.INSTANCE.setCodeActions(List
				.of(Either.forLeft(new Command(
				"fixme",
				"edit",
				List.of(wEdit))
			)
		));
		MockLanguageServer.INSTANCE.setDiagnostics(List.of(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
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
	public void testCodeActionsQuickAssist() throws CoreException {
		MockLanguageServer.reset();
		IFile f = TestUtils.createUniqueTestFile(project, "error");

		final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
		MockLanguageServer.INSTANCE.setCodeActions(List
				.of(Either.forLeft(new Command(
				"fixme",
				"edit",
				List.of(wEdit))
			)
		));
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
	public void testSlowCodeActionsQuickAssist() throws CoreException {
		MockLanguageServer.reset();
		IFile f = TestUtils.createUniqueTestFile(project, "error");

		final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
		MockLanguageServer.INSTANCE.setCodeActions(List
				.of(Either.forLeft(new Command(
				"fixme",
				"edit",
				List.of(wEdit))
			)
		));
		MockLanguageServer.INSTANCE.setTimeToProceedQueries(1000);
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
		assertEquals(1, MockLanguageServer.INSTANCE.getTextDocumentService().codeActionRequests);
	}

	@Test
	public void testCodeActionLiteralWorkspaceEdit() throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");

		final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
		final var codeAction = new CodeAction("fixme");
		codeAction.setEdit(wEdit);
		MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(codeAction)));
		MockLanguageServer.INSTANCE.setDiagnostics(List.of(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		final var editor = (AbstractTextEditor)TestUtils.openEditor(f);
		IMarker m = assertDiagnostics(f, "error", "fixme");
		assertResolution(editor, m, "fixed");
	}

	@Test
	public void testNoCodeActionOnReadOnlySource() throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");
		f.setResourceAttributes(new ResourceAttributes() {
			@Override
			public boolean isReadOnly() {
				return true;
			}
		});

		final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
		final var codeAction = new CodeAction("fixme");
		codeAction.setEdit(wEdit);
		MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(codeAction)));
		MockLanguageServer.INSTANCE.setDiagnostics(List.of(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		TestUtils.openEditor(f);
		assertDiagnostics(f, "error", "fixme", false);
	}

	@Test
	public void testCodeActionLiteralWithClientCommand() throws CoreException {
		IFile f = TestUtils.createUniqueTestFile(project, "error");

		final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		final var wEdit = new WorkspaceEdit(Collections.singletonMap(f.getLocationURI().toString(), List.of(tEdit)));
		final var codeAction = new CodeAction("fixme");
		codeAction.setCommand(new Command("editCommand", "mockEditCommand", List.of(wEdit)));
		MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(codeAction)));
		MockLanguageServer.INSTANCE.setDiagnostics(List.of(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		final var editor = (AbstractTextEditor)TestUtils.openEditor(f);
		IMarker m = assertDiagnostics(f, "error", "fixme");
		assertResolution(editor, m, "fixed");
	}

	@Test
	public void testCodeActionWorkspaceEditlWithDifferentURI() throws CoreException {
		IFile sourceFile = TestUtils.createUniqueTestFile(project, "error");
		IFile targetFile = TestUtils.createUniqueTestFile(project, "fixme");

		// create a diagnostic on the sourceFile with a code action
		// that changes the targetFile
		final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
		final var wEdit = new WorkspaceEdit(Collections.singletonMap(targetFile.getLocationURI().toString(), List.of(tEdit)));
		final var codeAction = new CodeAction("fixme");
		codeAction.setCommand(new Command("editCommand", "mockEditCommand", List.of(wEdit)));
		MockLanguageServer.INSTANCE.setCodeActions(List.of(Either.forRight(codeAction)));
		MockLanguageServer.INSTANCE.setDiagnostics(List.of(
				new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));

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

	private static IMarker assertDiagnostics(IFile f, String markerMessage, String resolutionLabel) throws CoreException {
		return assertDiagnostics(f, markerMessage, resolutionLabel, true);
	}

	private static IMarker assertDiagnostics(IFile f, String markerMessage, String resolutionLabel, boolean resolutionExpected) throws CoreException {
		waitForAndAssertCondition(2_000, () -> {
			IMarker[] markers = f.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true,
					IResource.DEPTH_ZERO);
			// seems we need the second condition as the attributes aren't loaded immediately
			return markers.length > 0 && markers[0].getAttribute(IMarker.MESSAGE) != null;
		});
		final IMarker m = f.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO)[0];
		assertEquals(markerMessage, m.getAttribute(IMarker.MESSAGE));

		assertEquals(resolutionExpected,
				waitForCondition(2_000, () ->
				IDE.getMarkerHelpRegistry().hasResolutions(m) &&
				// need this 2nd condition because async introduces a dummy resolution that's
				// not the one we want
				IDE.getMarkerHelpRegistry().getResolutions(m).length > 0 &&
				IDE.getMarkerHelpRegistry().getResolutions(m)[0].getLabel().equals(resolutionLabel)),
				resolutionExpected ? "Resolution not found within expected time." : "Unexpected resolution found");
		return m;
	}

	private static void assertResolution(AbstractTextEditor editor, IMarker m, String newText) {
		IDE.getMarkerHelpRegistry().getResolutions(m)[0].run(m);

		waitForCondition(1_000,
				() -> newText.equals(editor.getDocumentProvider().getDocument(editor.getEditorInput()).get()));
		assertEquals(newText, editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());

		waitForCondition(1_000,
				() -> newText.equals(((StyledText) editor.getAdapter(Control.class)).getText()));
		assertEquals(newText, ((StyledText) editor.getAdapter(Control.class)).getText());
	}

	private static void assertResolution(IFile targetFile, IMarker m, String newText) {
		IDE.getMarkerHelpRegistry().getResolutions(m)[0].run(m);

		IEditorPart editorPart = TestUtils.getEditor(targetFile);
		assertTrue(editorPart instanceof AbstractTextEditor);
		final var editor = (AbstractTextEditor)editorPart;

		waitForCondition(1_000,
				() -> newText.equals(editor.getDocumentProvider().getDocument(editor.getEditorInput()).get()));
		assertEquals(newText, editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());

		waitForCondition(1_000,
				() -> newText.equals(((StyledText) editor.getAdapter(Control.class)).getText()));
		assertEquals(newText, ((StyledText) editor.getAdapter(Control.class)).getText());
	}
}
