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
package org.eclipse.lsp4e.test.folding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionAnnotationModel;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.test.utils.AbstractTest;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.FoldingPreferencePage;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.junit.jupiter.api.Test;

public class FoldingCommandsTest extends AbstractTest {

	private static final int MAX_WAIT_MS = 5_000;

	private static final String CONTENT = """
		/**
		 * SPDX-License-Identifier: EPL-2.0
		 */
		import
		import
		import
		/**
		 * Some comment
		 */
		visible
		""";

	@Test
	public void foldAndUnfoldAllCommands() throws Exception {
		// Ensure no auto-folding interferes with the command behavior
		configureAutoFolding(false);

		// Provide folding ranges from the Mock LS: license header and imports
		final var foldingRangeLicense = new FoldingRange(0, 2);
		foldingRangeLicense.setKind(FoldingRangeKind.Comment);
		final var foldingRangeImport = new FoldingRange(3, 5);
		foldingRangeImport.setKind(FoldingRangeKind.Imports);
		MockLanguageServer.INSTANCE.setFoldingRanges(List.of(foldingRangeLicense, foldingRangeImport));

		// Open editor and wait until folding annotations are present
		final var editor = TestUtils.openEditor(TestUtils.createUniqueTestFile(null, CONTENT));
		final ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		assertTrue(viewer instanceof ProjectionViewer);

		final var pViewer = (ProjectionViewer) viewer;
		TestUtils.waitForAndAssertCondition(MAX_WAIT_MS, () -> getAnnotationModel(pViewer) != null);
		final ProjectionAnnotationModel model = getAnnotationModel(pViewer);

		// Ensure folding annotations are populated after projection model exists
		// by toggling the folding-enabled preference to trigger a reconcile.
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue(FoldingPreferencePage.PREF_FOLDING_ENABLED, false);
		store.setValue(FoldingPreferencePage.PREF_FOLDING_ENABLED, true);

		TestUtils.waitForAndAssertCondition(MAX_WAIT_MS, () -> countAnnotations(model) == 2);
		assertEquals(2, countAnnotations(model));

		// Execute "Fold All"
		IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
		handlerService.executeCommand("org.eclipse.lsp4e.folding.collapseAll", null);

		TestUtils.waitForAndAssertCondition(MAX_WAIT_MS, () -> countCollapsed(model) == 2);
		assertEquals(2, countCollapsed(model));

		// Execute "Unfold All"
		handlerService.executeCommand("org.eclipse.lsp4e.folding.expandAll", null);
		TestUtils.waitForAndAssertCondition(MAX_WAIT_MS, () -> countCollapsed(model) == 0);
		assertEquals(0, countCollapsed(model));
	}

	private static ProjectionAnnotationModel getAnnotationModel(ProjectionViewer viewer) {
		return viewer.getProjectionAnnotationModel();
	}

	private static int countAnnotations(ProjectionAnnotationModel model) {
		int count = 0;
		for (var it = model.getAnnotationIterator(); it != null && it.hasNext();) {
			if (it.next() instanceof ProjectionAnnotation) {
				count++;
			}
		}
		return count;
	}

	private static int countCollapsed(ProjectionAnnotationModel model) {
		int count = 0;
		for (var it = model.getAnnotationIterator(); it != null && it.hasNext();) {
			Annotation a = it.next();
			if (a instanceof ProjectionAnnotation pa && pa.isCollapsed()) {
				count++;
			}
		}
		return count;
	}

	private static void configureAutoFolding(boolean enabled) {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue(FoldingPreferencePage.PREF_FOLDING_ENABLED, true);
		store.setValue(FoldingPreferencePage.PREF_AUTOFOLD_COMMENTS, enabled);
		store.setValue(FoldingPreferencePage.PREF_AUTOFOLD_LICENSE_HEADERS_COMMENTS, enabled);
		store.setValue(FoldingPreferencePage.PREF_AUTOFOLD_REGIONS, enabled);
		store.setValue(FoldingPreferencePage.PREF_AUTOFOLD_IMPORT_STATEMENTS, enabled);
	}
}
