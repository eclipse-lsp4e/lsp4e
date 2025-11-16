/*******************************************************************************
 * Copyright (c) 2020, 2023 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Martin Lippert (Pivotal Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.declaration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import org.eclipse.core.resources.IFile;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.declaration.LSBasedHyperlink;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.junit.jupiter.api.Test;

public class LSBasedHyperlinkTest extends AbstractTestWithProject {

	private static String locationType = "Open Declaration";

	@Test
	public void testHyperlinkLabelNoLocation() {
		final var location = new Location();
		final var hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals(locationType, hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForFileLocation() throws URISyntaxException {
		final var location = new Location();
		location.setUri("file:///Users/someuser/testfile");
		final var hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - testfile - " + Path.of(new URI(location.getUri())),
				hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForFileLocationLink() throws URISyntaxException {
		final var location = new LocationLink();
		location.setTargetUri("file:///Users/someuser/testfile");
		final var hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - testfile - " + Path.of(new URI(location.getTargetUri())), hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForIntroBasedLocationWithoutLabel() {
		final var location = new Location();
		location.setUri("http://org.eclipse.ui.intro/execute?command=mycommand%28bindingKey%3DLorg%2Ftest%2Fmvctest%2FMyComponent%3B%2CprojectName%3Dmvctest%29");
		final var hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForIntroBasedLocationLinkWithLabel() {
		final var location = new LocationLink();
		location.setTargetUri("http://org.eclipse.ui.intro/execute?command=org.springframework.tooling.ls.eclipse.commons.commands.OpenJavaElementInEditor%28bindingKey%3DLorg%2Ftest%2Fmvctest%2FMyComponent%3B%2CprojectName%3Dmvctest%29&label=MyComponent+-+org.test.mvctest");
		final var hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - MyComponent - org.test.mvctest", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForRandomURLLocation() {
		final var location = new Location();
		location.setUri("http://eclipse.org");
		final var hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - http://eclipse.org", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForRandomURLLocationLink() {
		final var location = new LocationLink();
		location.setTargetUri("http://eclipse.org");
		final var hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - http://eclipse.org", hyperlink.getHyperlinkText());
	}

	@Test
	public void testHyperlinkLabelForFileInProject() throws Exception {
		IFile file = TestUtils.createFile(project, "my-test.txt", "Example Text");
		final var location = new LocationLink();
		location.setTargetUri(LSPEclipseUtils.toUri(new File(file.getLocation().toOSString())).toASCIIString());
		final var hyperlink = new LSBasedHyperlink(location, null, locationType);

		assertEquals("Open Declaration - my-test.txt - " + project.getName(), hyperlink.getHyperlinkText());
	}
}
