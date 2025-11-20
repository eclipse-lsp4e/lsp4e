/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * - Angelo ZERR (Red Hat Inc.) - initial API and implementation
 * - Sebastian Thomschke (Vegard IT GmbH) - adapted the code from LSP4IJ to LSP4E
 *******************************************************************************/
package org.eclipse.lsp4e.test.files;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4e.internal.files.PathPatternMatcher;
import org.junit.jupiter.api.Test;

// Based on https://github.com/redhat-developer/lsp4ij/blob/6f41f6d22a7146f31e0218cb459513abd5dc16d3/src/test/java/com/redhat/devtools/lsp4ij/features/files/PathPatternMatcherTest.java

/**
 * Tests for glob pattern expansion in {@link PathPatternMatcher}.
 */
class PathPatternMatcherTest {

	@Test
	void noExpansion() {
		assertExpandPatterns("foo", "foo");
	}

	@Test
	void oneExpansion() {
		assertExpandPatterns("**/foo", "foo", "**/foo");
	}

	@Test
	void twoExpansion() {
		assertExpandPatterns("**/foo/**", "foo", "**/foo", "foo/**", "**/foo/**");
	}

	@Test
	void sixExpansion() {
		assertExpandPatterns("{**/node_modules/**,**/.git/**,**/bower_components/**}", //
				"{node_modules,.git,bower_components}", //
				"{node_modules,.git,bower_components/**}", //
				"{node_modules,.git,**/bower_components}", //
				"{node_modules,.git,**/bower_components/**}", //
				"{node_modules,.git/**,bower_components}", //
				"{node_modules,.git/**,bower_components/**}", //
				"{node_modules,.git/**,**/bower_components}", //
				"{node_modules,.git/**,**/bower_components/**}", //
				"{node_modules,**/.git,bower_components}", //
				"{node_modules,**/.git,bower_components/**}", //
				"{node_modules,**/.git,**/bower_components}", //
				"{node_modules,**/.git,**/bower_components/**}", //
				"{node_modules,**/.git/**,bower_components}", //
				"{node_modules,**/.git/**,bower_components/**}", //
				"{node_modules,**/.git/**,**/bower_components}", //
				"{node_modules,**/.git/**,**/bower_components/**}", //
				"{node_modules/**,.git,bower_components}", //
				"{node_modules/**,.git,bower_components/**}", //
				"{node_modules/**,.git,**/bower_components}", //
				"{node_modules/**,.git,**/bower_components/**}", //
				"{node_modules/**,.git/**,bower_components}", //
				"{node_modules/**,.git/**,bower_components/**}", //
				"{node_modules/**,.git/**,**/bower_components}", //
				"{node_modules/**,.git/**,**/bower_components/**}", //
				"{node_modules/**,**/.git,bower_components}", //
				"{node_modules/**,**/.git,bower_components/**}", //
				"{node_modules/**,**/.git,**/bower_components}", //
				"{node_modules/**,**/.git,**/bower_components/**}", //
				"{node_modules/**,**/.git/**,bower_components}", //
				"{node_modules/**,**/.git/**,bower_components/**}", //
				"{node_modules/**,**/.git/**,**/bower_components}", //
				"{node_modules/**,**/.git/**,**/bower_components/**}", //
				"{**/node_modules,.git,bower_components}", //
				"{**/node_modules,.git,bower_components/**}", //
				"{**/node_modules,.git,**/bower_components}", //
				"{**/node_modules,.git,**/bower_components/**}", //
				"{**/node_modules,.git/**,bower_components}", //
				"{**/node_modules,.git/**,bower_components/**}", //
				"{**/node_modules,.git/**,**/bower_components}", //
				"{**/node_modules,.git/**,**/bower_components/**}", //
				"{**/node_modules,**/.git,bower_components}", //
				"{**/node_modules,**/.git,bower_components/**}", //
				"{**/node_modules,**/.git,**/bower_components}", //
				"{**/node_modules,**/.git,**/bower_components/**}", //
				"{**/node_modules,**/.git/**,bower_components}", //
				"{**/node_modules,**/.git/**,bower_components/**}", //
				"{**/node_modules,**/.git/**,**/bower_components}", //
				"{**/node_modules,**/.git/**,**/bower_components/**}", //
				"{**/node_modules/**,.git,bower_components}", //
				"{**/node_modules/**,.git,bower_components/**}", //
				"{**/node_modules/**,.git,**/bower_components}", //
				"{**/node_modules/**,.git,**/bower_components/**}", //
				"{**/node_modules/**,.git/**,bower_components}", //
				"{**/node_modules/**,.git/**,bower_components/**}", //
				"{**/node_modules/**,.git/**,**/bower_components}", //
				"{**/node_modules/**,.git/**,**/bower_components/**}", //
				"{**/node_modules/**,**/.git,bower_components}", //
				"{**/node_modules/**,**/.git,bower_components/**}", //
				"{**/node_modules/**,**/.git,**/bower_components}", //
				"{**/node_modules/**,**/.git,**/bower_components/**}", //
				"{**/node_modules/**,**/.git/**,bower_components}", //
				"{**/node_modules/**,**/.git/**,bower_components/**}", //
				"{**/node_modules/**,**/.git/**,**/bower_components}", //
				"{**/node_modules/**,**/.git/**,**/bower_components/**}");
	}

	private static void assertExpandPatterns(String pattern, String... expectedPatterns) {
		List<String> actual = PathPatternMatcher.expandPatterns(pattern);
		Collections.sort(actual);
		List<String> expected = Arrays.asList(expectedPatterns);
		Collections.sort(expected);
		assertArrayEquals(actual.toArray(String[]::new), expected.toArray(String[]::new),
				"'" + pattern + "' pattern expansion should match [\"" + String.join("\",\"", actual) + "\"]");
	}
}
