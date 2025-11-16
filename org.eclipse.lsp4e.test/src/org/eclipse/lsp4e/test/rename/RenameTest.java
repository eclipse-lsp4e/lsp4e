/*******************************************************************************
 * Copyright (c) 2018, 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - Added some suites
 *  Pierre-Yves B. <pyvesdev@gmail.com> - Bug 525411 - [rename] input field should be filled with symbol to rename
 *  Martin Lippert (Pivotal) - Bug 561373 - added async enablement for late language servers
 *******************************************************************************/
package org.eclipse.lsp4e.test.rename;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.rename.LSPRenameProcessor;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.Test;

public class RenameTest extends AbstractTestWithProject {

	@Test
	public void testRenameHandlerEnablement() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		final var editor = (ITextEditor) TestUtils.openEditor(file);
		editor.selectAndReveal(1, 0);
		ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
		Command command = commandService.getCommand(IWorkbenchCommandConstants.FILE_RENAME);

		waitForAndAssertCondition(2_000, command::isEnabled);
		assertTrue(command.isHandled());
	}

	@Test
	public void testAsyncRenameHandlerEnablement() throws Exception {
		final int delay = 4_000;
		// this fixed value is not really an optimal solution, since it depends on the following things
		// to happen within that time frame. Should maybe re-work this in the future towards a more
		// precise way of steering the execution from the test here
		MockLanguageServer.INSTANCE.setTimeToProceedQueries(delay);

		try {
			IFile file = TestUtils.createUniqueTestFile(project, "old");
			final var editor = (ITextEditor) TestUtils.openEditor(file);

			ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
			Command command = commandService.getCommand(IWorkbenchCommandConstants.FILE_RENAME);
			assertFalse(command.isEnabled());

			editor.selectAndReveal(1, 0);

			waitForAndAssertCondition(2 * delay, command::isEnabled);
			assertTrue(command.isHandled());
		} finally {
			// Put back so shutdown doesn't time out
			MockLanguageServer.INSTANCE.setTimeToProceedQueries(0);
		}
	}

	@Test
	public void testRenameRefactoring() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(createSimpleMockRenameEdit(LSPEclipseUtils.toUri(file)));
		IDocument document = LSPEclipseUtils.getDocument(file);
		assertNotNull(document);
		final var processor = new LSPRenameProcessor(document, 0);
		processor.setNewName("new");
		try {
			final var processorBasedRefactoring = new ProcessorBasedRefactoring(processor);
			processorBasedRefactoring.checkAllConditions(new NullProgressMonitor());
			processorBasedRefactoring.createChange(new NullProgressMonitor()).perform(new NullProgressMonitor());
		} catch (CoreException e) {
			e.printStackTrace();
		}
		assertEquals("new", document.get());
	}

	@Test
	public void testPrepareRenameRefactoring() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(createSimpleMockRenameEdit(LSPEclipseUtils.toUri(file)));
		IDocument document = LSPEclipseUtils.getDocument(file);
		assertNotNull(document);
		final var processor = new LSPRenameProcessor(document, 0);
		processor.setNewName("new");
		try {
			final var processorBasedRefactoring = new ProcessorBasedRefactoring(processor);
			processorBasedRefactoring.checkAllConditions(new NullProgressMonitor());
			processorBasedRefactoring.createChange(new NullProgressMonitor()).perform(new NullProgressMonitor());
		} catch (CoreException e) {
			e.printStackTrace();
		}
		assertEquals("new", document.get());
	}

	@Test
	public void testPrepareRenameRefactoringError() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(createSimpleMockRenameEdit(LSPEclipseUtils.toUri(file)));
		MockLanguageServer.INSTANCE.getTextDocumentService().setPrepareRenameResult(null);
		IDocument document = LSPEclipseUtils.getDocument(file);
		assertNotNull(document);
		final var processor = new LSPRenameProcessor(document, 0);
		processor.setNewName("new");

		try {
			final var processorBasedRefactoring = new ProcessorBasedRefactoring(processor);
			RefactoringStatus status = processorBasedRefactoring.checkAllConditions(new NullProgressMonitor());
			assertEquals(RefactoringStatus.FATAL, status.getSeverity());
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testRenameRefactoringExternalFile() throws Exception {
		File file = TestUtils.createTempFile("testPerformOperationExternalFile", ".lspt");
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(createSimpleMockRenameEdit(file.toURI()));
		IFileStore store = EFS.getStore(file.toURI());
		ITextFileBufferManager manager = ITextFileBufferManager.DEFAULT;
		try {
			manager.connectFileStore(store, new NullProgressMonitor());
			IDocument document = ((ITextFileBuffer)manager.getFileStoreFileBuffer(store)).getDocument();
			document.set("old");
			final var processor = new LSPRenameProcessor(document, 0);
			processor.setNewName("new");
			try {
				final var processorBasedRefactoring = new ProcessorBasedRefactoring(processor);
				processorBasedRefactoring.checkAllConditions(new NullProgressMonitor());
				processorBasedRefactoring.createChange(new NullProgressMonitor()).perform(new NullProgressMonitor());
			} catch (CoreException e) {
				e.printStackTrace();
			}
			assertEquals("new", document.get());
		} finally {
			manager.disconnectFileStore(store, new NullProgressMonitor());
		}
	}

	@Test
	public void testRenameChangeAlsoExternalFile() throws Exception {
		IFile workspaceFile = TestUtils.createUniqueTestFile(project, "old");
		File externalFile = TestUtils.createTempFile("testRenameChangeAlsoExternalFile", ".lspt");
		Files.write(externalFile.toPath(), "old".getBytes());
		final var edits = new HashMap<String, List<TextEdit>>(2, 1.f);
		edits.put(LSPEclipseUtils.toUri(workspaceFile).toString(), List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new")));
		edits.put(LSPEclipseUtils.toUri(externalFile).toString(), List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new")));
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(new WorkspaceEdit(edits));
		IDocument document = LSPEclipseUtils.getDocument(workspaceFile);
		assertNotNull(document);
		final var processor = new LSPRenameProcessor(document, 0);
		processor.setNewName("new");
		try {
			final var processorBasedRefactoring = new ProcessorBasedRefactoring(processor);
			processorBasedRefactoring.checkAllConditions(new NullProgressMonitor());
			processorBasedRefactoring.createChange(new NullProgressMonitor()).perform(new NullProgressMonitor());
		} catch (CoreException e) {
			e.printStackTrace();
		}
		assertEquals("new", document.get());
		assertEquals("new", new String(Files.readAllBytes(externalFile.toPath())));
	}

	@Test
	public void testRenameHandlerExecution() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(createSimpleMockRenameEdit(LSPEclipseUtils.toUri(file)));
		final var editor = (ITextEditor) TestUtils.openEditor(file);
		editor.selectAndReveal(1, 0);
		ICommandService commandService = PlatformUI.getWorkbench().getService(ICommandService.class);
		IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
		Command command = commandService.getCommand(IWorkbenchCommandConstants.FILE_RENAME);
		assertTrue(command.isEnabled() && command.isHandled());
		final var e = new Event();
		e.widget = editor.getAdapter(Control.class);
		Shell ideShell = editor.getSite().getShell();
		Display display = ideShell.getDisplay();
		e.display = display;
		final var renameDialogOkPressed = new AtomicBoolean();
		Listener pressOKonRenameDialogPaint = event -> {
			if (renameDialogOkPressed.get()) {
				return;
			}
			if(event.widget instanceof Composite composite) {
				Shell shell = composite.getShell();
				if(shell != ideShell && "Rename".equals(shell.getText())) {
					event.widget.getDisplay().asyncExec(() -> pressOk(shell));
					renameDialogOkPressed.set(true);
				}
			}
		};
		try {
			display.addFilter(SWT.Paint, pressOKonRenameDialogPaint);
			ExecutionEvent executionEvent = handlerService.createExecutionEvent(command, e);
			command.executeWithChecks(executionEvent);
			waitForAndAssertCondition("Rename dialog not shown", 3_000, display, renameDialogOkPressed::get);
			IDocument document = LSPEclipseUtils.getDocument(editor);
			waitForAndAssertCondition("document not modified, rename not applied", 3_000, display,
					() -> "new".equals(document.get()));
		} finally {
			ideShell.getDisplay().removeFilter(SWT.Paint, pressOKonRenameDialogPaint);
		}
	}

	@Test
	public void testPlaceholderUsingPlaceholderFromPrepareRenameResult() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(createSimpleMockRenameEdit(LSPEclipseUtils.toUri(file)));
		IDocument document = LSPEclipseUtils.getDocument(file);
		assertNotNull(document);
		final var processor = new LSPRenameProcessor(document, 0);

		try {
			processor.checkInitialConditions(new NullProgressMonitor());
		} catch (CoreException e) {
			e.printStackTrace();
		}
		assertEquals("placeholder", processor.getPlaceholder());
	}

	@Test
	public void testPlaceholderUsingRangeFromPrepareRenameResult() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		MockLanguageServer.INSTANCE.getTextDocumentService().setRenameEdit(createSimpleMockRenameEdit(LSPEclipseUtils.toUri(file)));
		final var range = new Range(new Position(0, 1), new Position(0, 3)); // Two last letters of "old".
		MockLanguageServer.INSTANCE.getTextDocumentService().setPrepareRenameResult(Either.forLeft(range));
		IDocument document = LSPEclipseUtils.getDocument(file);
		assertNotNull(document);
		final var processor = new LSPRenameProcessor(document, 0);

		try {
			processor.checkInitialConditions(new NullProgressMonitor());
		} catch (CoreException e) {
			e.printStackTrace();
		}
		assertEquals("ld", processor.getPlaceholder());
	}

	private void pressOk(Shell dialogShell) {
		try {
			final var dialog = (Dialog)dialogShell.getData();
			Method okPressedMethod = Dialog.class.getDeclaredMethod("okPressed");
			okPressedMethod.setAccessible(true);
			okPressedMethod.invoke(dialog);
		} catch (Exception ex) {
			throw new Error(ex);
		}
	}

	private static WorkspaceEdit createSimpleMockRenameEdit(URI fileUri) {
		final var res = new WorkspaceEdit();
		final var f = new File(fileUri);
		res.setChanges(Collections.singletonMap(LSPEclipseUtils.toUri(f).toString(),
				List.of(new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new"))));
		return res;
	}
}
