/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.semanticTokens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SemanticHighlightReconcilerStrategyTest extends AbstractTestWithProject {

	private Shell shell;

	@BeforeEach
	public void setUp() {
		shell = new Shell();

		// Setup Server Capabilities
		List<String> tokenTypes = List.of("keyword");
		List<String> tokenModifiers = List.of("obsolete");
		SemanticTokensTestUtil.setSemanticTokensLegend(tokenTypes, tokenModifiers);
	}

	@Test
	public void testKeyword() throws CoreException {
		final var semanticTokens = new SemanticTokens();
		semanticTokens.setData(SemanticTokensTestUtil.keywordSemanticTokens());

		MockLanguageServer.INSTANCE.getTextDocumentService().setSemanticTokens(semanticTokens);

		IFile file = TestUtils.createUniqueTestFile(project, "lsptm", SemanticTokensTestUtil.keywordText);
		ITextViewer textViewer = TestUtils.openTextViewer(file);

		Display display = shell.getDisplay();
		DisplayHelper.sleep(display, 2_000); // Give some time to the editor to update

		StyleRange[] styleRanges = textViewer.getTextWidget().getStyleRanges();
		var backgroundColor = textViewer.getTextWidget().getBackground();

		assertEquals(6, styleRanges.length);

		assertEquals(0, styleRanges[0].start);
		assertEquals(4, styleRanges[0].length);
		assertNotEquals(styleRanges[0].foreground, backgroundColor);

		assertEquals(4, styleRanges[1].start);
		assertEquals(11, styleRanges[1].length);
		assertNotEquals(styleRanges[1].foreground, backgroundColor);

		assertEquals(15, styleRanges[2].start);
		assertEquals(4, styleRanges[2].length);
		assertNotEquals(styleRanges[2].foreground, backgroundColor);

		assertEquals(19, styleRanges[3].start);
		assertEquals(5, styleRanges[3].length);
		assertNotEquals(styleRanges[3].foreground, backgroundColor);

		assertEquals(24, styleRanges[4].start);
		assertEquals(7, styleRanges[4].length);
		assertNotEquals(styleRanges[4].foreground, backgroundColor);

		assertEquals(31, styleRanges[5].start);
		assertEquals(11, styleRanges[5].length);
		assertNotEquals(styleRanges[5].foreground, backgroundColor);
	}
}
