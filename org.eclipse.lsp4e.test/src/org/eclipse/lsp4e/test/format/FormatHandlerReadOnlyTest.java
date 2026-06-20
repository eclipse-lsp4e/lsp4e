/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation.
 *******************************************************************************/
package org.eclipse.lsp4e.test.format;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.Test;

public class FormatHandlerReadOnlyTest extends AbstractTestWithProject {

	@Test
	public void testFormatOnReadOnlyFileAndMakeWritable() throws Exception {
		// Mock formatting to prepend "//" at the start of each line
		var edits = List.of( //
				new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "//"),
				new TextEdit(new Range(new Position(1, 0), new Position(1, 0)), "//"));
		MockLanguageServer.INSTANCE.setFormattingTextEdits(edits);

		String content = "line1\nline2\n";
		IFile file = TestUtils.createUniqueTestFile(project, content);

		// Make file read-only
		ResourceAttributes attrs = file.getResourceAttributes();
		attrs.setReadOnly(true);
		file.setResourceAttributes(attrs);
		assertTrue(file.getResourceAttributes().isReadOnly());

		// Open editor and select the whole document
		var editor = TestUtils.openEditor(file);
		var textEditor = (ITextEditor) editor;
		IDocument doc = textEditor.getDocumentProvider().getDocument(textEditor.getEditorInput());
		textEditor.getSelectionProvider().setSelection(new TextSelection(0, doc.getLength()));

		// Before executing the format command, set up a poller that clicks "Yes"
		// and records that the dialog was shown
		var display = Display.getDefault();
		var beforeShells = new HashSet<>(Arrays.asList(display.getShells()));
		final var dialogShown = new AtomicBoolean(false);
		display.asyncExec(new Runnable() {
			Button findYesButton(Composite parent) {
				for (Control child : parent.getChildren()) {
					if (child instanceof Button button && button.getText().toLowerCase().contains("yes")) {
						return button;
					}
					if (child instanceof Composite composite) {
						Button result = findYesButton(composite);
						if (result != null) {
							return result;
						}
					}
				}
				return null;
			}

			@Override
			public void run() {
				Shell newShell = TestUtils.findNewShell(beforeShells, display);
				if (newShell == null || !Messages.LSPFormatHandler_ReadOnlyEditor_title.equals(newShell.getText())) {
					display.timerExec(50, this);
					return;
				}
				dialogShown.set(true);
				Button yes = findYesButton(newShell);
				if (yes != null) {
					yes.notifyListeners(SWT.Selection, new Event());
				}
			}
		});

		// Run format command which should prompt and then make the file writable
		IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
		handlerService.executeCommand("org.eclipse.lsp4e.format", null);

		// File was made writable and edits were applied
		TestUtils.waitForAndAssertCondition(5_000, dialogShown::get);
		TestUtils.waitForAndAssertCondition(5_000, () -> !file.getResourceAttributes().isReadOnly());
		TestUtils.waitForAndAssertCondition(5_000, () -> "//line1\n//line2\n".equals(doc.get()));
	}
}
