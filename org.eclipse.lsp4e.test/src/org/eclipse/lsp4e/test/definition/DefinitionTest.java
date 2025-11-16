/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.declaration.LSBasedHyperlink;
import org.eclipse.lsp4e.operations.declaration.OpenDeclarationHyperlinkDetector;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;
import org.junit.jupiter.api.Test;

public class DefinitionTest extends AbstractTestWithProject {

	private final OpenDeclarationHyperlinkDetector hyperlinkDetector = new OpenDeclarationHyperlinkDetector();

	@Test
	public void testDefinitionOneLocation() throws Exception {
		final var location = new Location("file://test", new Range(new Position(0, 0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setDefinition(List.of(location));

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		assertEquals(1, hyperlinks.length);
		// TODO add location check
	}

	@Test
	public void testDefinitionAndTypeDefinition() throws Exception {
		final var definitionRange = new Range(new Position(0, 0), new Position(0, 1));
		MockLanguageServer.INSTANCE.setDefinition(List.of(new Location("file://testDefinition", definitionRange)));
		final var typeDefinitionRange = new Range(new Position(0, 2), new Position(0, 3));
		MockLanguageServer.INSTANCE.setTypeDefinitions(List.of(new LocationLink("file://testTypeDefinition", typeDefinitionRange, typeDefinitionRange)));

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		assertEquals(2, hyperlinks.length);
		Set<String> uris = Arrays.stream(hyperlinks).map(LSBasedHyperlink.class::cast).map(LSBasedHyperlink::getLocation).map(location -> {
			if (location.isLeft()) {
				return location.getLeft().getUri();
			}
			return location.getRight().getTargetUri();
		}).collect(Collectors.toSet());
		assertTrue(uris.contains("file://testDefinition"));
		assertTrue(uris.contains("file://testTypeDefinition"));
	}

	@Test
	public void testDefinitionOneLocationExternalFile() throws Exception {
		final var location = new Location("file://test", new Range(new Position(0, 0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setDefinition(List.of(location));

		File file = TestUtils.createTempFile("testDocumentLinkExternalFile", ".lspt");
		final var editor = (ITextEditor) IDE.openInternalEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toURI()));
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(editor);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(0, 0), true);
		assertEquals(1, hyperlinks.length);
	}

	@Test
	public void testDefinitionManyLocation() throws Exception {
		final var locations = new ArrayList<Location>();
		locations.add(new Location("file://test0", new Range(new Position(0, 0), new Position(0, 10))));
		locations.add(new Location("file://test1", new Range(new Position(1, 0), new Position(1, 10))));
		locations.add(new Location("file://test2", new Range(new Position(2, 0), new Position(2, 10))));
		MockLanguageServer.INSTANCE.setDefinition(locations);

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		assertEquals(3, hyperlinks.length);
		// TODO add location check
	}

	@Test
	public void testDefinitionNoLocations() throws Exception {
		MockLanguageServer.INSTANCE.setDefinition(null);

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		assertEquals(true, hyperlinks == null);
	}

	@Test
	public void testDefinitionEmptyLocations() throws Exception {
		MockLanguageServer.INSTANCE.setDefinition(Collections.emptyList());

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] hyperlinks = hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		assertEquals(true, hyperlinks == null);
	}

	@Test
	public void testReturnsPromptly() throws Exception {
		final var location = new Location("file://test", new Range(new Position(0, 0), new Position(0, 10)));
		MockLanguageServer.INSTANCE.setDefinition(List.of(location));

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		long duration = System.currentTimeMillis();
		hyperlinkDetector.detectHyperlinks(viewer, new Region(1, 0), true);
		duration = System.currentTimeMillis() - duration;
		assertTrue(duration < 500);
	}
}
