/*******************************************************************************
 * Copyright (c) 2022 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.semanticTokens;

import static org.eclipse.lsp4e.test.semanticTokens.SemanticTokensTestUtil.GREEN;
import static org.eclipse.lsp4e.test.semanticTokens.SemanticTokensTestUtil.RED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.lsp4e.operations.semanticTokens.StyleRangeHolder;
import org.eclipse.lsp4e.operations.semanticTokens.StyleRangeMerger;
import org.eclipse.lsp4e.test.utils.AbstractTest;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.junit.jupiter.api.Test;

public class StyleRangeMergerTest extends AbstractTest {

	@Test
	public void testSemanticHighlightMergesWithExistingStyleRanges() {
		// make 3 adjacent regions: ___AABBCC
		int length = 2;
		Region rangeA = new Region(2, length);
		Region rangeB = new Region(rangeA.getOffset() + length, length);
		Region rangeC = new Region(rangeB.getOffset() + length, length);

		StyleRange backGroundRangeAB = aStyleRange(span(rangeA, rangeB), null, GREEN, SWT.NORMAL);
		StyleRange boldRedStyleRangeBC = aStyleRange(span(rangeB, rangeC), RED, null, SWT.BOLD);

		List<StyleRange> resultingStyleRanges = mergeStyleRanges(new StyleRange[] { backGroundRangeAB },
				new StyleRange[] { boldRedStyleRangeBC });

		assertEquals(aStyleRange(rangeA, null, GREEN, SWT.NORMAL), resultingStyleRanges.get(0));
		assertEquals(aStyleRange(rangeB, RED, GREEN, SWT.BOLD), resultingStyleRanges.get(1));
		assertEquals(aStyleRange(rangeC, RED, null, SWT.BOLD), resultingStyleRanges.get(2));

	}

	@Test
	public void testSemanticHighlightUnBoldsExistingStyleRanges() {
		// make 4 adjacent regions: ___AABBCCDD
		int length = 2;
		Region rangeA = new Region(2, length);
		Region rangeB = new Region(rangeA.getOffset() + length, length);
		Region rangeC = new Region(rangeB.getOffset() + length, length);
		Region rangeD = new Region(rangeC.getOffset() + length, length);

		StyleRange[] existingRanges = new StyleRange[] { //
				aStyleRangeWithFontStyle(rangeA, SWT.BOLD), //
				aStyleRangeWithFontStyle(rangeB, SWT.BOLD), //
				aStyleRangeWithFontStyle(rangeC, SWT.BOLD), //
				aStyleRangeWithFontStyle(rangeD, SWT.BOLD) //
		};

		StyleRange[] newRanges = new StyleRange[] { //
				aStyleRangeWithFontStyle(rangeA, SWT.NORMAL), //
				aStyleRangeWithFontStyle(rangeC, SWT.NORMAL), //
				aStyleRangeWithFontStyle(rangeD, SWT.NORMAL), //
		};

		List<StyleRange> resultingStyleRanges = mergeStyleRanges(existingRanges, newRanges);

		assertEquals(aStyleRangeWithFontStyle(rangeA, SWT.NORMAL), resultingStyleRanges.get(0));
		assertEquals(aStyleRangeWithFontStyle(rangeB, SWT.BOLD), resultingStyleRanges.get(1));
		assertEquals(aStyleRangeWithFontStyle(rangeC, SWT.NORMAL), resultingStyleRanges.get(2));
		assertEquals(aStyleRangeWithFontStyle(rangeD, SWT.NORMAL), resultingStyleRanges.get(3));
	}

	@Test
	public void testSemanticHighlightMergesWithAndUnBoldsExistingStyleRanges() {
		// make adjacent regions: AABBCCDDEEFFGGHHII
		// existing style spans: XXXX VVYYYYWWZZ
		// new style spans: LLMMMMMMMM NNNNNN
		int length = 2;
		Region rangeA = new Region(0, length);
		Region rangeB = new Region(rangeA.getOffset() + length, length);
		Region rangeC = new Region(rangeB.getOffset() + length, length);
		Region rangeD = new Region(rangeC.getOffset() + length, length);
		Region rangeE = new Region(rangeD.getOffset() + length, length);
		Region rangeF = new Region(rangeE.getOffset() + length, length);
		Region rangeG = new Region(rangeF.getOffset() + length, length);
		Region rangeH = new Region(rangeG.getOffset() + length, length);
		Region rangeI = new Region(rangeH.getOffset() + length, length);

		StyleRange[] existingRanges = new StyleRange[] { //
				aStyleRange(span(rangeA, rangeB), GREEN, null, SWT.BOLD), //
				aStyleRange(rangeD, null, null, SWT.BOLD), //
				aStyleRange(span(rangeE, rangeF), null, GREEN, SWT.BOLD), //
				aStyleRange(rangeG, null, GREEN, SWT.NORMAL), //
				aStyleRange(rangeH, null, null, SWT.BOLD) //
		};

		StyleRange[] newRanges = new StyleRange[] { //
				aStyleRange(rangeA, null, RED, SWT.BOLD), //
				aStyleRange(span(rangeB, rangeE), RED, null, SWT.NORMAL), //
				aStyleRange(span(rangeG, rangeI), null, null, SWT.NORMAL), //
		};

		List<StyleRange> resultingStyleRanges = mergeStyleRanges(existingRanges, newRanges);

		assertEquals(aStyleRange(rangeA, GREEN, RED, SWT.BOLD), resultingStyleRanges.get(0));
		assertEquals(aStyleRange(rangeB, RED, null, SWT.NORMAL), resultingStyleRanges.get(1));
		assertEquals(aStyleRange(rangeC, RED, null, SWT.NORMAL), resultingStyleRanges.get(2));
		assertEquals(aStyleRange(rangeD, RED, null, SWT.NORMAL), resultingStyleRanges.get(3));
		assertEquals(aStyleRange(rangeE, RED, GREEN, SWT.NORMAL), resultingStyleRanges.get(4));
		assertEquals(aStyleRange(rangeF, null, GREEN, SWT.BOLD), resultingStyleRanges.get(5));
		assertEquals(aStyleRange(rangeG, null, GREEN, SWT.NORMAL), resultingStyleRanges.get(6));
		assertEquals(aStyleRange(rangeH, null, null, SWT.NORMAL), resultingStyleRanges.get(7));
		assertEquals(aStyleRange(rangeI, null, null, SWT.NORMAL), resultingStyleRanges.get(8));
	}

	@Test
	public void testSemanticHighlightCombinesItalicAndBoldWithExistingStyleRanges() {
		// make 3 adjacent regions: AABBCC
		int length = 2;
		Region rangeA = new Region(0, length);
		Region rangeB = new Region(rangeA.getOffset() + length, length);
		Region rangeC = new Region(rangeB.getOffset() + length, length);
		Region rangeD = new Region(rangeC.getOffset() + length, length);

		// in these tests, bold gets overridden by "not-bold", but italic does not get
		// overridden
		StyleRange[] existingRanges = new StyleRange[] { //
				aStyleRangeWithFontStyle(rangeA, SWT.BOLD | SWT.ITALIC), //
				aStyleRangeWithFontStyle(rangeB, SWT.BOLD), //
				aStyleRangeWithFontStyle(rangeC, SWT.BOLD), //
				aStyleRangeWithFontStyle(rangeD, SWT.ITALIC), //
		};

		StyleRange[] newRanges = new StyleRange[] { //
				aStyleRangeWithFontStyle(rangeA, SWT.NORMAL), //
				aStyleRangeWithFontStyle(rangeB, SWT.ITALIC), //
				aStyleRangeWithFontStyle(rangeC, SWT.BOLD | SWT.ITALIC), //
				aStyleRangeWithFontStyle(rangeD, SWT.BOLD), //
		};

		List<StyleRange> resultingStyleRanges = mergeStyleRanges(existingRanges, newRanges);

		assertEquals(aStyleRangeWithFontStyle(rangeA, SWT.ITALIC), resultingStyleRanges.get(0));
		assertEquals(aStyleRangeWithFontStyle(rangeB, SWT.ITALIC), resultingStyleRanges.get(1));
		assertEquals(aStyleRangeWithFontStyle(rangeC, SWT.BOLD | SWT.ITALIC), resultingStyleRanges.get(2));
		assertEquals(aStyleRangeWithFontStyle(rangeD, SWT.BOLD | SWT.ITALIC), resultingStyleRanges.get(3));
	}

	private List<StyleRange> mergeStyleRanges(StyleRange[] existingRanges, StyleRange[] newRanges) {
		TextPresentation textPresentation = new TextPresentation();
		textPresentation.replaceStyleRanges(existingRanges);

		StyleRangeHolder styleRangeHolder = new StyleRangeHolder();
		styleRangeHolder.saveStyles(List.of(newRanges));

		StyleRangeMerger semanticMergeStrategy = new StyleRangeMerger(true, false);
		semanticMergeStrategy.mergeStyleRanges(textPresentation, styleRangeHolder);

		List<StyleRange> resultingStyleRanges = new ArrayList<>();
		textPresentation.getNonDefaultStyleRangeIterator().forEachRemaining(resultingStyleRanges::add);
		return resultingStyleRanges;
	}

	private StyleRange aStyleRangeWithFontStyle(IRegion region, int fontStyle) {
		return new StyleRange(region.getOffset(), region.getLength(), null, null, fontStyle);
	}

	private StyleRange aStyleRange(IRegion region, Color foreground, Color background, int fontStyle) {
		return new StyleRange(region.getOffset(), region.getLength(), foreground, background, fontStyle);
	}

	// works for both contiguous and non-contiguous a and b
	private IRegion span(IRegion a, IRegion b) {
		return new Region(a.getOffset(), b.getOffset() + b.getLength() - a.getOffset());
	}
}
