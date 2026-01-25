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
package org.eclipse.lsp4e.internal;

import java.util.List;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.html.HtmlRenderer;

public class MarkdownUtil {

	/**
	 * Used commonmark extensions
	 */
	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());

	/**
	 * Singleton instance, as both classes are thread-safe, see
	 * https://github.com/commonmark/commonmark-java?tab=readme-ov-file#thread-safety
	 */
	private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
	private static final Renderer RENDERER = HtmlRenderer.builder().extensions(EXTENSIONS).build();

	/**
	 * Renders the given markdown content to HTML.
	 */
	public static String renderToHtml(String markdown) {
		return RENDERER.render(PARSER.parse(markdown));
	}

}
