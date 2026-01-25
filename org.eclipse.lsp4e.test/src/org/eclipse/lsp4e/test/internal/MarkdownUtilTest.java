/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.test.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.eclipse.lsp4e.internal.MarkdownUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class MarkdownUtilTest {

	public static Stream<Arguments> renderToHtml() {
		return Stream.of( //
				Arguments.argumentSet("Simple", """
						# Heading 1
						- this is a test""", """
						<h1>Heading 1</h1>
						<ul>
						<li>this is a test</li>
						</ul>
												"""), //
				Arguments.argumentSet("With table", """
						| Header  | Another Header |
						|---------|----------------|
						| field 1 | value one      |""", """
						<table>
						<thead>
						<tr>
						<th>Header</th>
						<th>Another Header</th>
						</tr>
						</thead>
						<tbody>
						<tr>
						<td>field 1</td>
						<td>value one</td>
						</tr>
						</tbody>
						</table>
						""") //
		);
	}

	@ParameterizedTest
	@MethodSource
	void renderToHtml(String markdown, String expectedHtml) throws Exception {
		// Simple test to make sure we configured CommonMark correctly.
		assertEquals(expectedHtml, MarkdownUtil.renderToHtml(markdown));
	}

}
