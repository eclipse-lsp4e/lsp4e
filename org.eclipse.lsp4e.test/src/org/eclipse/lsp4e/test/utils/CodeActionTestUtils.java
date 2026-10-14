/*******************************************************************************
 * Copyright (c) 2017, 2026 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Ahmed Hussain (Cocotec Ltd) - moved out of CodeActionTests
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.AbstractTextEditor;

/**
 * Assertions on LSP diagnostic markers and their code-action resolutions.
 */
public final class CodeActionTestUtils {

	private CodeActionTestUtils() {
	}

	public static IMarker assertDiagnostics(IFile f, String markerMessage, String resolutionLabel) throws CoreException {
		return assertDiagnostics(f, markerMessage, resolutionLabel, true);
	}

	public static IMarker assertDiagnostics(IFile f, String markerMessage, String resolutionLabel, boolean resolutionExpected) throws CoreException {
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

	public static void assertResolution(AbstractTextEditor editor, IMarker m, String newText) {
		IDE.getMarkerHelpRegistry().getResolutions(m)[0].run(m);

		waitForCondition(1_000,
				() -> newText.equals(editor.getDocumentProvider().getDocument(editor.getEditorInput()).get()));
		assertEquals(newText, editor.getDocumentProvider().getDocument(editor.getEditorInput()).get());

		waitForCondition(1_000,
				() -> newText.equals(((StyledText) editor.getAdapter(Control.class)).getText()));
		assertEquals(newText, ((StyledText) editor.getAdapter(Control.class)).getText());
	}

	public static void assertResolution(IFile targetFile, IMarker m, String newText) {
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
