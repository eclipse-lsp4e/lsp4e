package org.eclipse.lsp4e.test.jdt.textblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.URI;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4e.jdt.internal.TextBlockDocument;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.junit.jupiter.api.Test;

class TextBlockDocumentTest extends AbstractTestWithProject{
	@Test
	void testReplace() throws CoreException, IOException, BadLocationException {
		String source = """
				\"""
				Hello
				beautiful world
				\"""
				""";
		String content = """
				Hello
				beautiful world
				""";
		IFile file = TestUtils.createFile(project, "test.txt", source);
		ITextViewer viewer = TestUtils.openTextViewer(file);
		IDocument fullDocument = viewer.getDocument();
		TextBlockDocument textBlockDocument = new TextBlockDocument(fullDocument, new Region(0, source.length()), source.toCharArray(), content.toCharArray(), URI.create("test://nothing"), -1);
		assertEquals(content, textBlockDocument.get());
		textBlockDocument.replace(6, 9, "Eclipse");
		assertEquals("""
				\"""
				Hello
				Eclipse world
				\"""
				""", fullDocument.get());
		assertThrows(UnsupportedOperationException.class, textBlockDocument::get);
	}
	
	@Test
	void testInsert() throws CoreException, IOException, BadLocationException {
		String source = """
				\"""
				Hello
				beautiful world
				\"""
				""";
		String content = """
				Hello
				beautiful world
				""";
		IFile file = TestUtils.createFile(project, "test.txt", source);
		ITextViewer viewer = TestUtils.openTextViewer(file);
		IDocument fullDocument = viewer.getDocument();
		TextBlockDocument textBlockDocument = new TextBlockDocument(fullDocument, new Region(0, source.length()), source.toCharArray(), content.toCharArray(), URI.create("test://nothing"), -1);
		assertEquals(content, textBlockDocument.get());
		textBlockDocument.replace(16, 0, "Eclipse ");
		assertEquals("""
				\"""
				Hello
				beautiful Eclipse world
				\"""
				""", fullDocument.get());
		assertThrows(UnsupportedOperationException.class, textBlockDocument::get);
	}
}
