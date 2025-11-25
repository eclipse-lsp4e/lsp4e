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
 *******************************************************************************/
package org.eclipse.lsp4e.test.documentLink;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.ArrayList;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.documentLink.DocumentLinkDetector;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.Test;

public class DocumentLinkTest extends AbstractTestWithProject {

	private final DocumentLinkDetector documentLinkDetector = new DocumentLinkDetector();

	@Test
	public void testDocumentLinkNoResults() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = documentLinkDetector.detectHyperlinks(viewer, new Region(0, 0), true);
		assertArrayEquals(null, hyperlinks);
	}

	@Test
	public void testDocumentLink() throws Exception {
		final var links = new ArrayList<DocumentLink>();
		links.add(new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file://test0"));
		MockLanguageServer.INSTANCE.setDocumentLinks(links);

		IFile file = TestUtils.createUniqueTestFile(project, "not_link <link>");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = documentLinkDetector.detectHyperlinks(viewer, new Region(13, 0), true);
		assertEquals(1, hyperlinks.length);
		assertEquals("file://test0", hyperlinks[0].getHyperlinkText());
	}

	@Test
	public void testDocumentLinkExternalFile() throws Exception {
		final var links = new ArrayList<DocumentLink>();
		links.add(new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file://test0"));
		MockLanguageServer.INSTANCE.setDocumentLinks(links);

		File file = TestUtils.createTempFile("testDocumentLinkExternalFile", ".lspt");
		final var editor = (ITextEditor) IDE.openInternalEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toURI()));
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		viewer.getDocument().set("Long enough dummy content to match ranges");

		IHyperlink[] hyperlinks = documentLinkDetector.detectHyperlinks(viewer, new Region(13, 0), true);
		assertEquals(1, hyperlinks.length);
		assertEquals("file://test0", hyperlinks[0].getHyperlinkText());
	}

	@Test
	public void testDocumentLinkWithEncodedUri() throws Exception {
		final var links = new ArrayList<DocumentLink>();
		links.add(new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file:///tmp/fi%C3%A9le.ts"));
		MockLanguageServer.INSTANCE.setDocumentLinks(links);

		IFile file = TestUtils.createUniqueTestFile(project, "not_link <link>");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = documentLinkDetector.detectHyperlinks(viewer, new Region(13, 0), true);
		assertEquals(1, hyperlinks.length);
		assertEquals("/tmp/fiéle.ts", hyperlinks[0].getHyperlinkText());
	}

	@Test
	public void testDocumentLinkWrongRegion() throws Exception {
		final var links = new ArrayList<DocumentLink>();
		links.add(new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file://test0"));
		MockLanguageServer.INSTANCE.setDocumentLinks(links);

		IFile file = TestUtils.createUniqueTestFile(project, "not_link <link>");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = documentLinkDetector.detectHyperlinks(viewer, new Region(0, 0), true);
		assertArrayEquals(null, hyperlinks);
	}
}
