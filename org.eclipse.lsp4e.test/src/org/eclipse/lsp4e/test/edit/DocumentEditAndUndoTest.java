/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Victor Rubezhny (Red Hat Inc.) - initial implementation
 *   Sebastian Thomschke (Vegard IT GmbH) - refactor and fix erratic test failures
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IEditorPart;
import org.junit.jupiter.api.Test;

import com.google.common.collect.Iterators;

public class DocumentEditAndUndoTest extends AbstractTestWithProject {

	@Test
	public void testDocumentEditAndUndo() throws Exception {
		MockLanguageServer.INSTANCE.setLinkedEditingRanges(new LinkedEditingRanges(List.of( //
				new Range(new Position(0, 1), new Position(0, 2)), //
				new Range(new Position(0, 5), new Position(0, 6))),
				"[:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02ff\\u0370-\\u037d\\u037f-\\u1fff\\u200c\\u200d\\u2070-\\u218f\\u2c00-\\u2fef\\u3001-\\udfff\\uf900-\\ufdcf\\ufdf0-\\ufffd\\u10000-\\uEFFFF][:A-Z_a-z\\u00C0-\\u00D6\\u00D8-\\u00F6\\u00F8-\\u02ff\\u0370-\\u037d\\u037f-\\u1fff\\u200c\\u200d\\u2070-\\u218f\\u2c00-\\u2fef\\u3001-\\udfff\\uf900-\\ufdcf\\ufdf0-\\ufffd\\u10000-\\uEFFFF\\-\\.0-9\\u00b7\\u0300-\\u036f\\u203f-\\u2040]*"));

		final var initialContent = "<a></a>";
		IFile testFile = TestUtils.createUniqueTestFile(project, initialContent);
		IEditorPart editor = TestUtils.openEditor(testFile);
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		final var doc = viewer.getDocument();
		assertEquals(initialContent, doc.get());

		// Force LS to initialize and open file
		assertTrue(LanguageServers.forDocument(doc).anyMatching());

		final var textWidget = viewer.getTextWidget();

		// Initialize Linked Editing by setting up the text cursor position
		textWidget.setCaretOffset(2);

		// Wait for Linked Editing to be established
		waitForAndAssertCondition("Linked Editing not established", 3_000, () -> {
			return Iterators
					.tryFind(((ISourceViewer) viewer).getAnnotationModel().getAnnotationIterator(),
							anno -> anno.getType().startsWith("org.eclipse.ui.internal.workbench.texteditor.link"))
					.isPresent();
		});

		// perform text editing operation
		final var display = textWidget.getDisplay();
		display.asyncExec(new Runnable() {

			@Override
			public void run() {
				type(textWidget, 'b');
				type(textWidget, 'c');
			}

			private void type(Control control, char c) {
				control.forceFocus();
				Event keyEvent= new Event();
				keyEvent.widget= control;
				keyEvent.type= SWT.KeyDown;
				keyEvent.character= c;
				keyEvent.keyCode= c;
				display.post(keyEvent);
				keyEvent.type= SWT.KeyUp;
				display.post(keyEvent);
			}
		});

		waitForAndAssertCondition(3_000, () -> {
			assertEquals("<abc></abc>", doc.get(), "Document content isn't correctly changed");
			return true;
		});

		// perform undo operation
		final var textOperationTarget = editor.getAdapter(ITextOperationTarget.class);
		BusyIndicator.showWhile(display, () -> textOperationTarget.doOperation(ITextOperationTarget.UNDO));

		waitForAndAssertCondition(3_000, () -> {
			assertEquals(initialContent, doc.get(), "Document content isn't correctly reverted");
			return true;
		});
	}
}
