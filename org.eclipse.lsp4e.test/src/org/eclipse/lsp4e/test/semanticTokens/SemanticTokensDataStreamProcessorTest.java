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

import java.util.List;

import org.eclipse.jface.text.Document;
import org.eclipse.lsp4e.operations.semanticTokens.SemanticTokensDataStreamProcessor;
import org.eclipse.lsp4e.test.utils.AbstractTest;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.swt.custom.StyleRange;
import org.junit.jupiter.api.Test;

public class SemanticTokensDataStreamProcessorTest extends AbstractTest {

	@Test
	public void testKeyword() {
		final var document = new Document(SemanticTokensTestUtil.keywordText);

		final var processor = new SemanticTokensDataStreamProcessor(SemanticTokensTestUtil
				.keywordTokenTypeMapper(SemanticTokensTestUtil.RED_TOKEN), SemanticTokensTestUtil.offsetMapper(document));

		List<Integer> expectedStream = SemanticTokensTestUtil.keywordSemanticTokens();
		List<StyleRange> expectedStyleRanges = List.of(//
				new StyleRange(0, 4, SemanticTokensTestUtil.RED, null), //
				new StyleRange(15, 4, SemanticTokensTestUtil.RED, null), //
				new StyleRange(24, 7, SemanticTokensTestUtil.RED, null)//
				);

		List<StyleRange> styleRanges = processor.getTokensData(expectedStream, getSemanticTokensLegend());

		assertEquals(expectedStyleRanges, styleRanges);
	}

	private SemanticTokensLegend getSemanticTokensLegend() {
		final var semanticTokensLegend = new SemanticTokensLegend();
		semanticTokensLegend.setTokenTypes(List.of("keyword","other"));
		semanticTokensLegend.setTokenModifiers(List.of("obsolete"));
		return semanticTokensLegend;
	}
}
