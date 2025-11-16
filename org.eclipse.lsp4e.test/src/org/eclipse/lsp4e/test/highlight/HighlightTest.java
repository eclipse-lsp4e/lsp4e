/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Sebastian Thomschke (Vegard IT GmbH) - refactoring to fix erratic test failures
 *  Joao Dinis Ferreira (Avaloq Group AG) - Create testHighlightsInMultipleViewersForOneSource
 *******************************************************************************/
package org.eclipse.lsp4e.test.highlight;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.highlight.HighlightReconcilingStrategy;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.ui.IEditorReference;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

public class HighlightTest extends AbstractTestWithProject{

	@Test
	public void testHighlight() throws CoreException {
		checkGenericEditorVersion();

		MockLanguageServer.INSTANCE.setDocumentHighlights(Map.ofEntries( //
				Map.entry(new Position(0, 1), List.of( //
						new DocumentHighlight(new Range(new Position(0, 2), new Position(0, 6)), DocumentHighlightKind.Read),
						new DocumentHighlight(new Range(new Position(0, 7), new Position(0, 12)), DocumentHighlightKind.Write),
						new DocumentHighlight(new Range(new Position(0, 13), new Position(0, 17)), DocumentHighlightKind.Text) //
				))));

		final IFile testFile = TestUtils.createUniqueTestFile(project, "  READ WRITE TEXT");
		final ITextViewer viewer = TestUtils.openTextViewer(testFile);

		if (viewer instanceof ISourceViewer sourceViewer) {
			final var annotationModel = sourceViewer.getAnnotationModel();

			sourceViewer.getTextWidget().setCaretOffset(1); // emulate cursor move

			waitForAndAssertCondition(3_000, () -> {
				assertAnnotationExists(annotationModel, HighlightReconcilingStrategy.READ_ANNOTATION_TYPE, 2, 4);
				assertAnnotationExists(annotationModel, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 7, 5);
				assertAnnotationExists(annotationModel, HighlightReconcilingStrategy.TEXT_ANNOTATION_TYPE, 13, 4);
				return true;
			});
		} else {
			fail("ISourceViewer expected but got: " + viewer);
		}
	}

	@Test
	public void testCheckIfOtherAnnotationsRemains() throws CoreException {
		checkGenericEditorVersion();

		MockLanguageServer.INSTANCE.setDocumentHighlights(Map.ofEntries( //
				Map.entry(new Position(0, 1), List.of( //
						new DocumentHighlight(new Range(new Position(0, 2), new Position(0, 6)), DocumentHighlightKind.Read)
				))));

		final IFile testFile = TestUtils.createUniqueTestFile(project, "  READ WRITE TEXT");
		final ITextViewer viewer = TestUtils.openTextViewer(testFile);

		if (viewer instanceof ISourceViewer sourceViewer) {
			final var annotationModel = sourceViewer.getAnnotationModel();

			final var fakeAnnotationType = "FAKE_TYPE";
			final var fakeAnnotation = new Annotation(fakeAnnotationType, false, null);
			final var fakeAnnotationPosition = new org.eclipse.jface.text.Position(0, 10);
			annotationModel.addAnnotation(fakeAnnotation, fakeAnnotationPosition);

			viewer.getTextWidget().setCaretOffset(1); // emulate cursor move

			waitForAndAssertCondition(3_000, () -> {
				assertAnnotationExists(annotationModel, HighlightReconcilingStrategy.READ_ANNOTATION_TYPE, 2, 4);
				assertAnnotationExists(annotationModel, fakeAnnotationType, fakeAnnotationPosition.offset,
						fakeAnnotationPosition.length);
				return true;
			});
		}
	}

	@Test
	public void testHighlightsInMultipleViewersForOneSource() throws CoreException {
		checkGenericEditorVersion();

		// Create a test file with two sets of highlights
		final IFile testFile = TestUtils.createUniqueTestFile(project, "ONE\nTWO");

		MockLanguageServer.INSTANCE.setDocumentHighlights(Map.ofEntries( //
				Map.entry(new Position(0, 1), List.of( //
						new DocumentHighlight(new Range(new Position(0, 0), new Position(0, 3)), DocumentHighlightKind.Write)
				)),
				Map.entry(new Position(1, 1), List.of( //
						new DocumentHighlight(new Range(new Position(1, 0), new Position(1, 3)), DocumentHighlightKind.Write)
				))));

		// Open the first viewer
		final var viewer1 = (ISourceViewer) TestUtils.openTextViewer(testFile);
		final var annotationModel1 = viewer1.getAnnotationModel();

		// Set the caret offset to activate the first set of highlights
		viewer1.getTextWidget().setCaretOffset(1);

		waitForAndAssertCondition(3_000, () -> {
			assertAnnotationExists(annotationModel1, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 0, 3);
			return true;
		});

		// Split the view in the active editor
		List<IEditorReference> editorReferences = TestUtils.splitActiveEditor();

		// Keep track of the newly opened editor, so we can close it later
		ISourceViewer viewer2 = null;
		IEditorReference editorToClose = null;
		for (IEditorReference editorReference : editorReferences) {
			final var viewer = (ISourceViewer) LSPEclipseUtils.getTextViewer(editorReference.getEditor(false));
			if (viewer != viewer1) {
				editorToClose = editorReference;
				viewer2 = viewer;
				break;
			}
		}
		assertNotNull(viewer2);
		assertNotNull(editorToClose);

		final var annotationModel2 = viewer2.getAnnotationModel();

		// Set the caret offset to activate the second set of highlights
		viewer2.getTextWidget().setCaretOffset(5);

		// The annotation models of both viewers should now contain both sets of highlights
		waitForAndAssertCondition(3_000, () -> {
			assertAnnotationExists(annotationModel1, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 0, 3);
			assertAnnotationExists(annotationModel1, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 4, 3);
			assertAnnotationExists(annotationModel2, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 0, 3);
			assertAnnotationExists(annotationModel2, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 4, 3);
			return true;
		});

		// Close the second viewer
		TestUtils.closeEditor(editorToClose.getEditor(false), false);

		// Check that the highlights are the same as in the first case
		waitForAndAssertCondition(3_000, () -> {
			assertAnnotationExists(annotationModel1, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 0, 3);
			assertAnnotationDoesNotExist(annotationModel1, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 4, 3);
			assertAnnotationExists(annotationModel2, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 0, 3);
			assertAnnotationDoesNotExist(annotationModel2, HighlightReconcilingStrategy.WRITE_ANNOTATION_TYPE, 4, 3);
			return true;
		});
	}

	private void assertAnnotationExists(IAnnotationModel annotationModel, String annotationType, int posOffset, int posLen) {
		final var hasAnnotation = new boolean[] { false };
		final var annotations = new ArrayList<String>();
		annotationModel.getAnnotationIterator().forEachRemaining(anno -> {
			final var annoPos = annotationModel.getPosition(anno);
			if (anno.getType().equals(annotationType) && annoPos.offset == posOffset && annoPos.length == posLen) {
				hasAnnotation[0] = true;
			}
			annotations.add("Annotation[" + //
					"type=" + anno.getType() + //
					", text=" + anno.getText() + //
					", offset=" + annoPos.offset + //
					", length=" + annoPos.length + //
					"]");
		});

		if (!hasAnnotation[0]) {
			fail("Annotation of type [" + annotationType + "] not found at position {offset=" + posOffset + //
					" length=" + posLen + "}. Annotations found: " + annotations);
		}
	}

	private void assertAnnotationDoesNotExist(IAnnotationModel annotationModel, String annotationType, int posOffset, int posLen) {
		final var hasAnnotation = new boolean[] { false };
		final var annotations = new ArrayList<String>();
		annotationModel.getAnnotationIterator().forEachRemaining(anno -> {
			final var annoPos = annotationModel.getPosition(anno);
			if (anno.getType().equals(annotationType) && annoPos.offset == posOffset && annoPos.length == posLen) {
				hasAnnotation[0] = true;
			}
			annotations.add("Annotation[" + //
					"type=" + anno.getType() + //
					", text=" + anno.getText() + //
					", offset=" + annoPos.offset + //
					", length=" + annoPos.length + //
					"]");
		});

		if (hasAnnotation[0]) {
			fail("Unexpected annotation of type [" + annotationType + "] found at position {offset=" + posOffset + //
					" length=" + posLen + "}. Annotations found: " + annotations);
		}
	}

	private void checkGenericEditorVersion() {
		// ignore tests for generic editor without reconciler API
		Bundle bundle = Platform.getBundle("org.eclipse.ui.genericeditor");
		assumeTrue(bundle.getVersion().compareTo(new Version(1, 1, 0)) >= 0);
	}
}
