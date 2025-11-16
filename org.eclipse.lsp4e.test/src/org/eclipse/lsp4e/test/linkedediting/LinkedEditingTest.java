/*******************************************************************************
 * Copyright (c) 2021 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Victor Rubezhny (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.linkedediting;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

public class LinkedEditingTest extends AbstractTestWithProject {

	@Test
	public void testLinkedEditing() throws CoreException {
		final var sourceViewer = setupSimpleHtmlPageViewer();

		sourceViewer.getTextWidget().setSelection(11); // 10-14 <body|>

		final var annotations = new HashMap<org.eclipse.jface.text.Position, Annotation>();

		waitForAnnotationsPresent(sourceViewer);

		IAnnotationModel model = sourceViewer.getAnnotationModel();
		final Iterator<Annotation> iterator = model.getAnnotationIterator();
		while (iterator.hasNext()) {
			Annotation annotation = iterator.next();
			annotations.put(model.getPosition(annotation), annotation);
		}

		Annotation annotation = annotations.get(new LinkedPosition(sourceViewer.getDocument(), 10, 4, 0));
		assertNotNull(annotation);
		assertTrue(annotation.getType().startsWith("org.eclipse.ui.internal.workbench.texteditor.link"));
	}

	@Test
	public void testLinkedEditingExitPolicy() throws CoreException {
		final var sourceViewer = setupSimpleHtmlPageViewer();

		// Test linked editing annotation in a tag name position
		sourceViewer.getTextWidget().setCaretOffset(14);
		sourceViewer.getTextWidget().setSelection(14); // 10-14 <body| class="test">
		waitForAnnotationsPresent(sourceViewer);

		IAnnotationModel model = sourceViewer.getAnnotationModel();
		List<Annotation> annotations = findAnnotations(sourceViewer, 14).stream().filter(a -> a.getType().startsWith("org.eclipse.ui.internal.workbench.texteditor.link")).toList();
		assertEquals(1, annotations.size(), "Exepected only 1 link annotation here, got " + annotations);
		Annotation masterAnnotation = findAnnotation(sourceViewer, "org.eclipse.ui.internal.workbench.texteditor.link.master");
		assertNotNull(masterAnnotation);
		assertTrue(annotations.contains(masterAnnotation));
		org.eclipse.jface.text.Position masterPosition = model.getPosition(masterAnnotation);
		assertNotNull(masterPosition);
		Annotation slaveAnnotation = findAnnotation(sourceViewer, "org.eclipse.ui.internal.workbench.texteditor.link.slave");
		assertNotNull(slaveAnnotation);
		org.eclipse.jface.text.Position slavePosition = model.getPosition(slaveAnnotation);
		assertNotNull(slavePosition);
		assertEquals(sourceViewer.getTextWidget().getTextRange(masterPosition.getOffset(), masterPosition.getLength()),
				sourceViewer.getTextWidget().getTextRange(slavePosition.getOffset(), slavePosition.getLength()));

		// Test linked editing annotation out of a tag name position (should be absent)
		sourceViewer.getTextWidget().setCaretOffset(15);
		sourceViewer.getTextWidget().setSelection(15); // 10-14 <body |class="test">
		waitForAnnotationsPresent(sourceViewer);

		model = sourceViewer.getAnnotationModel();
		// No "linked" annotation is to be found at this offset
		assertFalse(findAnnotations(sourceViewer, 15).stream().anyMatch(a -> a.getType().startsWith("org.eclipse.ui.internal.workbench.texteditor.link")));
	}

	@Test
	public void testSelectionChange() throws CoreException {

		final var ranges = new ArrayList<Range>();
		ranges.add(new Range(new Position(0, 0), new Position(0, 5)));
		ranges.add(new Range(new Position(0, 6), new Position(0, 12)));

		final var linkedEditingRanges = new LinkedEditingRanges(ranges, "[:A-Z_a-z]*\\b");
		MockLanguageServer.INSTANCE.setLinkedEditingRanges(linkedEditingRanges);

		IFile testFile = TestUtils.createUniqueTestFile(project, "first second");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		if (!(viewer instanceof ISourceViewer)) {
			fail();
		}

		var sourceViewer = (ISourceViewer) viewer;

		sourceViewer.getTextWidget().setSelection(9,3); //selection range with caret at beginning

		waitForAndAssertCondition(3_000, () -> findAnnotations(sourceViewer, 1).size() != 0);

		assertEquals(3, sourceViewer.getTextWidget().getCaretOffset());

	}

	private void waitForAnnotationsPresent(final ISourceViewer sourceViewer) {
		waitForAndAssertCondition(3_000, () -> {
			Iterator<Annotation> iterator = sourceViewer.getAnnotationModel().getAnnotationIterator();
			while (iterator.hasNext()) {
				Annotation annotation = iterator.next();
				if (annotation.getType().startsWith("org.eclipse.ui.internal.workbench.texteditor.link")) {
					return true;
				}
			}
			return false;
		});
	}


	private ISourceViewer setupSimpleHtmlPageViewer() throws CoreException {
		final var ranges = new ArrayList<Range>();
		ranges.add(new Range(new Position(1, 3), new Position(1, 7)));
		ranges.add(new Range(new Position(3, 4), new Position(3, 8)));

		final var linkkedEditingRanges = new LinkedEditingRanges(ranges, "[:A-Z_a-z]*\\Z");
		MockLanguageServer.INSTANCE.setLinkedEditingRanges(linkkedEditingRanges);

		IFile testFile = TestUtils.createUniqueTestFile(project, "<html>\n  <body class=\"test\">\n    a body text\n  </body>\n</html>");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		if (!(viewer instanceof ISourceViewer)) {
			fail();
		}

		return (ISourceViewer) viewer;
	}


	private List<Annotation> findAnnotations(ISourceViewer sourceViewer, int offset) {
		final var annotations = new ArrayList<Annotation>();
		IAnnotationModel model = sourceViewer.getAnnotationModel();
		Iterator<Annotation> iterator = model.getAnnotationIterator();
		while (iterator.hasNext()) {
			Annotation annotation = iterator.next();
			org.eclipse.jface.text.Position position = model.getPosition(annotation);
			if (position != null && position.includes(offset)) {
				annotations.add(annotation);
			}
		}

		return annotations;
	}

	private Annotation findAnnotation(ISourceViewer sourceViewer, String type) {
		IAnnotationModel model = sourceViewer.getAnnotationModel();
		Iterator<Annotation> iterator = model.getAnnotationIterator();
		while (iterator.hasNext()) {
			Annotation annotation = iterator.next();
			if (annotation.getType() != null && annotation.getType().equals(type)) {
				return annotation;
			}
		}

		return null;
	}
}
