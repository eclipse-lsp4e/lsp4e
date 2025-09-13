/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.test.outline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.outline.LSSymbolsContentProvider;
import org.eclipse.lsp4e.outline.LSSymbolsContentProvider.OutlineViewerInput;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.Test;

/**
 * Tests to verify that documentURI always contains absolute paths in the file system.
 */
public class OutlineViewerInputTest extends AbstractTestWithProject {

	@Test
	public void testDocumentURIAbsolutePathForWorkspaceFile() throws CoreException, IOException {
		// Create a test file in the workspace
		var project = TestUtils.createProject("testProject" + System.currentTimeMillis());
		var testFile = TestUtils.createUniqueTestFile(project, "test content for outline");
		var document = LSPEclipseUtils.getDocument(testFile);
			
		// Create OutlineViewerInput and verify documentURI
		var outlineInput = new LSSymbolsContentProvider.OutlineViewerInput(document, null, null);
		var documentURI = getDocumentURI(outlineInput);
		
		assertNotNull("documentURI should not be null", documentURI);	
		// Verify it represents a valid file system path
		assertTrue("file".equals(documentURI.getScheme()));
		assertTrue(documentURI.toString().startsWith("file:///"));
	
		// For workspace files, verify the URI corresponds to the absolute workspace file location
		var workspaceFileLocation = testFile.getLocation().toString();
		assertTrue("documentURI should contain the abolute path in the file system", 
				documentURI.toString().contains(workspaceFileLocation));
	}

	@Test
	public void testDocumentURIAbsolutePathForExternalFile() throws IOException, CoreException {
		// Create a temporary file outside the workspace
		var tempFile = TestUtils.createTempFile("externalTest" + System.currentTimeMillis(), ".lspt");
		
		try (var fileWriter = new FileWriter(tempFile)) {
			fileWriter.write("external file content for testing absolute paths");
		}
		// Open the external file in an editor
		var editor = (ITextEditor) TestUtils.openExternalFileInEditor(tempFile);
		var document = LSPEclipseUtils.getDocument(editor);

		// Create OutlineViewerInput and verify documentURI
		var outlineInput = new LSSymbolsContentProvider.OutlineViewerInput(document, null, editor);
		var documentURI = getDocumentURI(outlineInput);
		
		assertNotNull("DocumentURI should not be null for external files", documentURI);
		
		// Verify it represents a valid file system path
		assertTrue("file".equals(documentURI.getScheme()));
		assertTrue(documentURI.toString().startsWith("file:///"));
			
		// Verify it points to the same file we created
		// replace '\' with '/' on Windows and remove leading '/' from documentURI path on Windows:
		assertEquals("documentURI absolute path should match the external file absolute path", 
				tempFile.getAbsolutePath().replace("\\","/"), documentURI.getPath().substring(1));
	}


	/**
	 * Helper method to access the private documentURI field using reflection.
	 * This is necessary since documentURI is private in OutlineViewerInput.
	 */
	private URI getDocumentURI(OutlineViewerInput outlineInput) {
		try {
			var field = OutlineViewerInput.class.getDeclaredField("documentURI");
			field.setAccessible(true);
			return (URI) field.get(outlineInput);
		} catch (Exception e) {
			throw new RuntimeException("Failed to access documentURI field", e);
		}
	}
}
