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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.documentLink.DocumentLinkDetector;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.tests.mock.MockTextDocumentService;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
	public void testDocumentLink(MockLanguageServerFactory factory) throws Exception {
		final var links = new ArrayList<DocumentLink>();
		links.add(new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file://test0"));
		factory.withConfiguration((idx, server)-> {
			server.setDocumentLinks(links);
		});

		IFile file = TestUtils.createUniqueTestFile(project, "not_link <link>");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = waitForHyperlinks(viewer, 13);
		assertEquals(1, hyperlinks.length);
		assertEquals("file://test0", hyperlinks[0].getHyperlinkText());
	}

	@Test
	public void testDocumentLinkExternalFile(@TempDir Path tempDir, MockLanguageServerFactory factory) throws Exception {
		final var links = new ArrayList<DocumentLink>();
		links.add(new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file://test0"));
		factory.withConfiguration((idx, server)-> {
			server.setDocumentLinks(links);
		});

		Path file = Files.createFile(tempDir.resolve("testDocumentLinkExternalFile.lspt"));
		final var editor = (ITextEditor) IDE.openInternalEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toUri()));
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);
		viewer.getDocument().set("Long enough dummy content to match ranges");

		IHyperlink[] hyperlinks = waitForHyperlinks(viewer, 13);
		assertEquals(1, hyperlinks.length);
		assertEquals("file://test0", hyperlinks[0].getHyperlinkText());
	}

	@Test
	public void testDocumentLinkWithEncodedUri(MockLanguageServerFactory factory) throws Exception {
		final var links = new ArrayList<DocumentLink>();
		links.add(new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file:///tmp/fi%C3%A9le.ts"));
		factory.withConfiguration((idx, server)-> {
			server.setDocumentLinks(links);
		});

		IFile file = TestUtils.createUniqueTestFile(project, "not_link <link>");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = waitForHyperlinks(viewer, 13);
		assertEquals(1, hyperlinks.length);
		assertEquals("/tmp/fiéle.ts", hyperlinks[0].getHyperlinkText());
	}

	@Test
	public void testDocumentLinkRequestedOncePerDocumentVersion(MockLanguageServerFactory factory) throws Exception {
		final var requests = new AtomicInteger();
		factory.withConfiguration((idx, server) -> {
			server.setTextDocumentService(new MockTextDocumentService(server::buildMaybeDelayedFuture) {
				@Override
				public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
					requests.incrementAndGet();
					return super.documentLink(params);
				}
			});
			server.setDocumentLinks(List.of(new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file://test0")));
		});

		IFile file = TestUtils.createUniqueTestFile(project, "not_link <link>");
		// a bare viewer, since an editor's link reconciler sends documentLink requests of its own
		final var bufferManager = FileBuffers.getTextFileBufferManager();
		bufferManager.connect(file.getFullPath(), LocationKind.IFILE, null);
		final var shell = new Shell();
		try {
			final var viewer = new TextViewer(shell, SWT.NONE);
			viewer.setDocument(bufferManager.getTextFileBuffer(file.getFullPath(), LocationKind.IFILE).getDocument());

			waitForHyperlinks(viewer, 13);
			final int requestsBefore = requests.get();
			for (int offset = 0; offset < 15; offset++) {
				documentLinkDetector.detectHyperlinks(viewer, new Region(offset, 0), true);
			}
			assertEquals(requestsBefore, requests.get());

			factory.getServer().setDocumentLinks(
					List.of(new DocumentLink(new Range(new Position(0, 10), new Position(0, 16)), "file://test1")));
			viewer.getDocument().replace(0, 0, " ");
			TestUtils.waitForAndAssertCondition(5_000, () -> {
				IHyperlink[] hyperlinks = documentLinkDetector.detectHyperlinks(viewer, new Region(14, 0), true);
				assertNotNull(hyperlinks);
				assertEquals("file://test1", hyperlinks[0].getHyperlinkText());
			});
		} finally {
			shell.dispose();
			bufferManager.disconnect(file.getFullPath(), LocationKind.IFILE, null);
		}
	}

	private IHyperlink[] waitForHyperlinks(ITextViewer viewer, int offset) {
		final var result = new IHyperlink[1][];
		TestUtils.waitForAndAssertCondition(5_000, () -> {
			result[0] = documentLinkDetector.detectHyperlinks(viewer, new Region(offset, 0), true);
			assertNotNull(result[0]);
		});
		return result[0];
	}

	@Test
	public void testDocumentLinkWrongRegion(MockLanguageServerFactory factory) throws Exception {
		final var links = new ArrayList<DocumentLink>();
		links.add(new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file://test0"));
		factory.withConfiguration((idx, server)-> {
			server.setDocumentLinks(links);
		});

		IFile file = TestUtils.createUniqueTestFile(project, "not_link <link>");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = documentLinkDetector.detectHyperlinks(viewer, new Region(0, 0), true);
		assertArrayEquals(null, hyperlinks);
	}
}
