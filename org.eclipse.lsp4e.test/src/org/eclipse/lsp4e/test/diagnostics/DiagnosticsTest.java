/*******************************************************************************
 * Copyright (c) 2017, 2018 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 528333] Performance problem with diagnostics
 *  Sebastian Thomschke (Vegard IT GmbH) - fix erratic test failures
 *******************************************************************************/
package org.eclipse.lsp4e.test.diagnostics;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.stream.Stream;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.test.color.ColorTest;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.BlockingWorkspaceJob;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.MarkerUtilities;
import org.junit.jupiter.api.Test;

public class DiagnosticsTest extends AbstractTestWithProject {

	private LSPDiagnosticsToMarkers diagnosticsToMarkers = new LSPDiagnosticsToMarkers("dummy");

	@Test
	public void testDiagnostics() throws CoreException {
		IFile file = TestUtils.createUniqueTestFile(project, "Diagnostic Other Text");

		int markerLineIndex = 0;
		int markerCharStart = 0;
		int markerCharEnd = 10;
		final var range = new Range(
				new Position(markerLineIndex, markerCharStart),
				new Position(markerLineIndex, markerCharEnd));
		List<Diagnostic> diagnostics = List.of(
				createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1"),
				createDiagnostic("2", "message2", range, DiagnosticSeverity.Warning, "source2"),
				createDiagnostic("3", "message3", range, DiagnosticSeverity.Information, "source3"),
				createDiagnostic("4", "message4", range, DiagnosticSeverity.Hint, "source4"));

		diagnosticsToMarkers.accept(new PublishDiagnosticsParams(file.getLocationURI().toString(), diagnostics));

		waitForAndAssertCondition(10_000, () -> {
			assertEquals(diagnostics.size(), file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false,
					IResource.DEPTH_INFINITE).length);
			return true;
		});

		IMarker[] markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false,
				IResource.DEPTH_INFINITE);
		for (Diagnostic diagnostic : diagnostics) {
			var marker = Stream.of(markers).filter(m -> {
				try {
					return m.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC) == diagnostic;
				} catch (CoreException ex) {
					throw new RuntimeException(ex);
				}}).findFirst();

			assertTrue(marker.isPresent(), "No marker found for " + diagnostic);

			assertEquals(markerCharStart, MarkerUtilities.getCharStart(marker.get()));
			assertEquals(markerCharEnd, MarkerUtilities.getCharEnd(marker.get()));
			assertEquals(markerLineIndex + 1, MarkerUtilities.getLineNumber(marker.get()));
			assertEquals(diagnostic.getMessage() + " [" + diagnostic.getCode().get() + "]",
					MarkerUtilities.getMessage(marker.get()));
		}

		diagnosticsToMarkers.accept(new PublishDiagnosticsParams(file.getLocationURI().toString(), Collections.emptyList()));

		waitForAndAssertCondition(10_000, () -> {
			assertEquals(0, file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false,
					IResource.DEPTH_INFINITE).length);
			return true;
		});
	}

	@Test
	public void testDiagnosticsForMarkerUpdateAfterProjectClose() throws CoreException, InterruptedException {
		IFile file = TestUtils.createUniqueTestFile(project, "Diagnostic Marker Update After Project Close");
		int markerLineIndex = 0;
		int markerCharStart = 0;
		int markerCharEnd = 10;
		final var range = new Range(
				new Position(markerLineIndex, markerCharStart),
				new Position(markerLineIndex, markerCharEnd));
		List<Diagnostic> diagnostics = List.of(
				createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1"),
				createDiagnostic("2", "message2", range, DiagnosticSeverity.Warning, "source2"),
				createDiagnostic("3", "message3", range, DiagnosticSeverity.Information, "source3"),
				createDiagnostic("4", "message4", range, DiagnosticSeverity.Hint, "source4"));
		final var diagnosticsParamsForFileDeletedInTheMeantime = new PublishDiagnosticsParams(file.getLocationURI().toString(), diagnostics);
		Job.getJobManager().suspend();
		diagnosticsToMarkers.accept(diagnosticsParamsForFileDeletedInTheMeantime);
		waitForAndAssertCondition(1_000, () -> {
			Job[] allMarkerJobs = Job.getJobManager().find(LanguageServerPlugin.FAMILY_UPDATE_MARKERS);
			assertThat("Marker Job not scheduled", allMarkerJobs.length, is(1));
			return true;
		});
		Job[] allMarkerJobs = Job.getJobManager().find(LanguageServerPlugin.FAMILY_UPDATE_MARKERS);
		Job markerJob = allMarkerJobs[0];
		project.close(null);
		waitForAndAssertCondition(1_000, () -> {
			assertEquals(project.isAccessible(), false);
			return true;
		});
		Job.getJobManager().resume();
		markerJob.join();
		assertThat(markerJob.getResult().isOK(), is(true));
	}


	// TODO this test is probably wrong but I don't know how to write it correctly. How can you simulate the patterns of concurrency?
	@Test
	public void testDiagnosticsForMarkerUpdateAfterDeletedFile() throws CoreException, InterruptedException {
		IFile file = TestUtils.createUniqueTestFile(project, "Diagnostic Marker Update After File Deletion");
		int markerLineIndex = 0;
		int markerCharStart = 0;
		int markerCharEnd = 10;
		final var range = new Range(
				new Position(markerLineIndex, markerCharStart),
				new Position(markerLineIndex, markerCharEnd));
		List<Diagnostic> diagnostics = List.of(
				createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1"));
		final var diagnosticsParamsForFileDeletedInTheMeantime = new PublishDiagnosticsParams(file.getLocationURI().toString(), diagnostics);
		final var blockingWorkspaceJob = new BlockingWorkspaceJob("blockingJob");
		blockingWorkspaceJob.setRule(file.getWorkspace().getRuleFactory().markerRule(file));
		blockingWorkspaceJob.schedule();
		waitForAndAssertCondition(1_000, () -> {
			assertEquals(blockingWorkspaceJob.getState(), Job.RUNNING);
			return true;
		});
		diagnosticsToMarkers.accept(diagnosticsParamsForFileDeletedInTheMeantime);
		Job[] allMarkerJobs = Job.getJobManager().find(LanguageServerPlugin.FAMILY_UPDATE_MARKERS);
		assertThat(allMarkerJobs.length, is(1));
		Job markerJob = allMarkerJobs[0];
		waitForAndAssertCondition(2_000, () -> {
			file.delete(true, null);
			assertEquals(file.exists(), false);
			return true;
		});
		blockingWorkspaceJob.progress();
		markerJob.join();
		assertThat(markerJob.getResult().isOK(), is(true));
	}

	@Test
	public void testFileBuffersNotLeaked() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "Diagnostic Other Text");

		final var range = new Range(new Position(0, 0), new Position(0, 10));
		final var diagnostics = new ArrayList<Diagnostic>();
		diagnostics.add(createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1"));

		IDocument existingDocument = LSPEclipseUtils.getExistingDocument(file);

		assertNull(existingDocument);

		diagnosticsToMarkers.accept(new PublishDiagnosticsParams(file.getLocationURI().toString(), diagnostics));

		waitForAndAssertCondition(10_000, () -> {
			IDocument shouldHaveBeenClosed = LSPEclipseUtils.getExistingDocument(file);
			assertNull(shouldHaveBeenClosed);
			return true;
		});
	}

	@Test
	public void testDiagnosticsRangeAfterDocument() throws CoreException {
		final var content = "Diagnostic Other Text";
		IFile file = TestUtils.createUniqueTestFile(project, content);

		final var range = new Range(new Position(1, 0), new Position(1, 5));
		List<Diagnostic> diagnostics = List
				.of(createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1"));

		diagnosticsToMarkers.accept(new PublishDiagnosticsParams(file.getLocationURI().toString(), diagnostics));

		waitForAndAssertCondition(10_000, () -> {
			assertEquals(diagnostics.size(), file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false,
					IResource.DEPTH_INFINITE).length);
			return true;
		});

		IMarker[] markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false,
				IResource.DEPTH_INFINITE);
		for (int i = 0; i < diagnostics.size(); i++) {
			Diagnostic diagnostic = diagnostics.get(i);
			IMarker marker = markers[i];

			assertEquals(diagnostic.getMessage() + " [" + diagnostic.getCode().get() + "]",
					MarkerUtilities.getMessage(marker));
			assertEquals(content.length(), MarkerUtilities.getCharStart(marker));
			assertEquals(content.length(), MarkerUtilities.getCharEnd(marker));
			assertEquals(1, MarkerUtilities.getLineNumber(marker));
			assertEquals(marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC), diagnostic);
		}
	}

	@Test
	public void testDiagnosticsFromVariousLS() throws Exception {
		final var content = "Diagnostic Other Text";
		IFile file = TestUtils.createUniqueTestFileMultiLS(project, content);
		final var range = new Range(new Position(1, 0), new Position(1, 0));
		MockLanguageServer.INSTANCE.setDiagnostics(List.of(
				createDiagnostic("1", "message1", range, DiagnosticSeverity.Error, "source1")));
		IMarker[] markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO);
		assertEquals(0, markers.length, "no marker should be shown at file initialization");
		TestUtils.openEditor(file);

		waitForAndAssertCondition(10_000, () -> {
			assertEquals(2, file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, true, IResource.DEPTH_ZERO).length, 
					"there should be 1 marker for each language server");
			return true;
		});
	}

	@Test
	public void testDiagnosticRedrawingCalls() throws CoreException {
		IFile file = TestUtils.createUniqueTestFile(project, "Diagnostic Other Text\nDiagnostic Other Text");

		final var range1 = new Range(new Position(0, 0), new Position(0, 10));
		final var range2 = new Range(new Position(1, 0), new Position(1, 10));
		final Diagnostic pos1Info1 = createDiagnostic("1", "message1", range1, DiagnosticSeverity.Error, "source1");
		final Diagnostic pos1Info2 = createDiagnostic("2", "message2", range1, DiagnosticSeverity.Error, "source2");
		final Diagnostic pos2Info2 = createDiagnostic("2", "message2", range2, DiagnosticSeverity.Error, "source2");
		// Add first
		confirmResourceChanges(file, pos1Info1, 1);
		// Info changed
		confirmResourceChanges(file, pos1Info2, 1);
		// Location changed
		confirmResourceChanges(file, pos2Info2, 1);
		// Location and Info changed
		confirmResourceChanges(file, pos1Info1, 1);
		// Nothing has changed (0 resource changes)
		confirmResourceChanges(file, pos1Info1, 0);
	}

	@Test
	public void testDiagnosticsOnExternalFile() throws Exception {
		MockLanguageServer.INSTANCE.setDiagnostics(List.of(new Diagnostic(new Range(new Position(0, 0), new Position(0, 1)), "This is a warning", DiagnosticSeverity.Warning, null)));
		File file = TestUtils.createTempFile("testDiagnosticsOnExternalFile", ".lspt");
		Font font = null;
		try {
			try (var out = new FileOutputStream(file);) {
				out.write('a');
			}
			ITextViewer viewer = LSPEclipseUtils.getTextViewer(IDE.openEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toURI())));
			StyledText widget = viewer.getTextWidget();
			final var biggerFont = new FontData(); // bigger font to keep color intact in some pixel (not altered by anti-aliasing)
			biggerFont.setHeight(40);
			biggerFont.setLocale(widget.getFont().getFontData()[0].getLocale());
			biggerFont.setName(widget.getFont().getFontData()[0].getName());
			biggerFont.setStyle(widget.getFont().getFontData()[0].getStyle());
			font = new Font(widget.getDisplay(), biggerFont);
			widget.setFont(font);
			RGB warningColor = new RGB(244, 200, 45); // from org.eclipse.ui.editors/plugin.xml/extension[@point='org.eclipse.ui.editors.markerAnnotationSpecification']/specification[@annotationType="org.eclipse.ui.workbench.texteditor.warning"]@colorPreferenceValue
			waitForAndAssertCondition(10_000, widget.getDisplay(), () -> ColorTest.containsColor(widget, warningColor, 10));
		} finally {
			if (font != null) {
				font.dispose();
			}
		}
	}

	private static final class MarkerRedrawCountListener implements IResourceChangeListener, IntSupplier {
		private int resourceChanges = 0;

		@Override
		public int getAsInt() {
			return resourceChanges;
		}

		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			// Confirm that it is a marker change
			IResourceDelta delta = event.getDelta();
			if (delta == null)
				return;
			IResourceDelta[] childDeltas = delta.getAffectedChildren();
			if (childDeltas.length == 0)
				return;
			childDeltas = childDeltas[0].getAffectedChildren();
			if (childDeltas.length == 0 || childDeltas[0].getMarkerDeltas().length == 0)
				return;
			resourceChanges++;
		}
	}

	private void confirmResourceChanges(IFile file, Diagnostic diagnostic, int expectedChanges) {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final var redrawCountListener = new MarkerRedrawCountListener();
		workspace.addResourceChangeListener(redrawCountListener);
		try {
			diagnosticsToMarkers.accept(new PublishDiagnosticsParams(file.getLocationURI().toString(),
					List.of(diagnostic)));
			waitForAndAssertCondition(10_000, () -> {
				IMarker[] markers = file.findMarkers(LSPDiagnosticsToMarkers.LS_DIAGNOSTIC_MARKER_TYPE, false,
						IResource.DEPTH_INFINITE);
				for (IMarker marker : markers) {
					if (marker.getAttribute(LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC).equals(diagnostic)) {
						return true;
					}
				}
				return false;
			});
			waitForAndAssertCondition(10_000, () -> {
				assertEquals(expectedChanges, redrawCountListener.getAsInt());
				return true;
			});
		} finally {
			workspace.removeResourceChangeListener(redrawCountListener);
		}
	}

	private Diagnostic createDiagnostic(String code, String message, Range range, DiagnosticSeverity severity,
			String source) {
		final var diagnostic = new Diagnostic();
		diagnostic.setCode(code);
		diagnostic.setMessage(message);
		diagnostic.setRange(range);
		diagnostic.setSeverity(severity);
		diagnostic.setSource(source);
		return diagnostic;
	}

}
