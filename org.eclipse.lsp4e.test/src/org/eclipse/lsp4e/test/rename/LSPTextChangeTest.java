/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - Added some suites
 *******************************************************************************/
package org.eclipse.lsp4e.test.rename;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.file.Files;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.refactoring.LSPTextChange;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.junit.jupiter.api.Test;

public class LSPTextChangeTest extends AbstractTestWithProject {

	@Test
	public void testPerformOperationWorkspaceFile() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		final var edit = new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new");
		final var operation = new PerformChangeOperation(new LSPTextChange("test", LSPEclipseUtils.toUri(file), edit));
		operation.run(new NullProgressMonitor());
		IDocument document = LSPEclipseUtils.getDocument(file);
		assertNotNull(document);
		assertEquals(edit.getNewText(), document.get());
	}

	@Test
	public void testRefactoringPreview() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "old");
		final var edit = new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new");
		final var change = new LSPTextChange("test", LSPEclipseUtils.toUri(file), edit);
		IDocument preview = change.getPreviewDocument(new NullProgressMonitor());
		assertEquals(preview.get(), "new");
	}

	@Test
	public void testPerformOperationExternalFile() throws Exception {
		File file = TestUtils.createTempFile("testPerformOperationExternalFile", ".lspt");
		Files.write(file.toPath(), "old".getBytes());
		final var edit = new TextEdit(new Range(new Position(0, 0), new Position(0, 3)), "new");
		final var operation = new PerformChangeOperation(new LSPTextChange("test", LSPEclipseUtils.toUri(file), edit));
		operation.run(new NullProgressMonitor());
		assertEquals(edit.getNewText(), new String(Files.readAllBytes(file.toPath())));
	}
}
