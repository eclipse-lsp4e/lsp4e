/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Jozef Tomek - initial implementation
 *******************************************************************************/

package org.eclipse.lsp4e.test.documentLink;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.junit.Test;

public class DocumentLinkReconcilingTest extends AbstractTestWithProject {
	
	private static final String CONTENT = """
				1st_line #LINK1 1st_line
				2nd_line #LINK2_START
				#LINK2_END 3rd_line #LINK3 3rd_line
				4th_line #LINK4 4th_line
				5th_line #LINK5_START_#LINK5_END
				6th_line #LINK6""";
	
	private static final List<DocumentLink> CONTENT_LINKS = List.of(
			new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file://link1"),
			new DocumentLink(new Range(new Position(1, 9), new Position(2, 10)), "file://link2"),
			new DocumentLink(new Range(new Position(2, 20), new Position(2, 26)), "file://link3"),
			new DocumentLink(new Range(new Position(3, 9), new Position(3, 15)), "file://link4"),
			new DocumentLink(new Range(new Position(4, 9), new Position(4, 32)), "file://link5"),
			new DocumentLink(new Range(new Position(5, 9), new Position(5, 15)), "file://link6"));
	
	public static final Color COLOR_1ST_LINE = new Color(255, 0, 0);
	public static final Color COLOR_2ND_LINE = new Color(0, 255, 0);
	public static final Color COLOR_3RD_LINE = new Color(0, 0, 255);
	public static final Color COLOR_4TH_LINE = new Color(255, 255, 0);
	public static final Color COLOR_5TH_LINE = new Color(0, 255, 255);
	public static final Color COLOR_6TH_LINE = new Color(255, 0, 255);
	
	private List<TextPresentation> textPresentations = new ArrayList<>(4);
	
	@Test
	public void testFullDocumentLinkReconciling() throws Exception {
		MockLanguageServer.INSTANCE.setDocumentLinks(CONTENT_LINKS);

		TextViewer viewer = (TextViewer) TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, CONTENT));
		IDocument document = viewer.getDocument();
		viewer.getTextWidget().setStyleRanges(new StyleRange[] {
				textStyle(0, 25, COLOR_1ST_LINE), // whole 1st line
				textStyle(25, 22, COLOR_2ND_LINE), // whole 2nd line
				textStyle(47, 36, COLOR_3RD_LINE), // whole 3rd line
				textStyle(83, 25, COLOR_4TH_LINE), // whole 4th line
				textStyle(108, 33, COLOR_5TH_LINE), // whole 5th line
				textStyle(141, 15, COLOR_6TH_LINE) // whole 6th line
		});
		viewer.addTextPresentationListener(this::textPresentationListener);
		
		TestUtils.waitForAndAssertCondition(1_000, () -> textPresentations.size() == 6);
		
		assertEquals(linkRegion(CONTENT_LINKS.get(0), document), textPresentations.get(0).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(1), document), textPresentations.get(1).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(2), document), textPresentations.get(2).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(3), document), textPresentations.get(3).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(4), document), textPresentations.get(4).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(5), document), textPresentations.get(5).getExtent());
		
		var styles = viewer.getTextWidget().getStyleRanges();
		
		assertEquals(17, styles.length);
		
		var pos = 0;
		var length = 0;
		assertEquals(textStyle(pos, length = 9, COLOR_1ST_LINE), styles[0]);
		assertEquals(linkStyle(pos += length, length = 6, COLOR_1ST_LINE), styles[1]);
		assertEquals(textStyle(pos += length, length = 9 + 1, COLOR_1ST_LINE), styles[2]); // also new line char
		
		assertEquals(textStyle(pos += length, length = 9, COLOR_2ND_LINE), styles[3]);
		assertEquals(linkStyle(pos += length, length = 12 + 1, COLOR_2ND_LINE), styles[4]); // also new line char
		
		assertEquals(linkStyle(pos += length, length = 10, COLOR_3RD_LINE), styles[5]);
		assertEquals(textStyle(pos += length, length = 10, COLOR_3RD_LINE), styles[6]);
		assertEquals(linkStyle(pos += length, length = 6, COLOR_3RD_LINE), styles[7]);
		assertEquals(textStyle(pos += length, length = 9 + 1, COLOR_3RD_LINE), styles[8]); // also new line char
		
		assertEquals(textStyle(pos += length, length = 9, COLOR_4TH_LINE), styles[9]);
		assertEquals(linkStyle(pos += length, length = 6, COLOR_4TH_LINE), styles[10]);
		assertEquals(textStyle(pos += length, length = 9 + 1, COLOR_4TH_LINE), styles[11]); // also new line char
		
		assertEquals(textStyle(pos += length, length = 9, COLOR_5TH_LINE), styles[12]);
		assertEquals(linkStyle(pos += length, length = 23, COLOR_5TH_LINE), styles[13]);
		assertEquals(textStyle(pos += length, length = 1, COLOR_5TH_LINE), styles[14]); // new line char

		assertEquals(textStyle(pos += length, length = 9, COLOR_6TH_LINE), styles[15]);
		assertEquals(linkStyle(pos += length, length = 6, COLOR_6TH_LINE), styles[16]);
	}

	@Test
	public void testClippedDocumentLinkReconciling() throws Exception {
		MockLanguageServer.INSTANCE.setDocumentLinks(CONTENT_LINKS);
		
		TextViewer viewer = (TextViewer) TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, CONTENT));
		IDocument document = viewer.getDocument();
		int thirdLineStart = document.getLineOffset(2);
		int untilMiddleOfLink5 = document.getLength() - thirdLineStart - document.getLineLength(5) - 1 - 11; 
		// set visible region to 3rd + 4th + half of 5th line - link1 and link6 are out of visible region completely
		// this triggers reconciliation by LSPDocumentLinkPresentationReconcilingStrategy
		viewer.setVisibleRegion(
				thirdLineStart, // TextViewer would align start of visible region to start of the line anyway
				untilMiddleOfLink5);
		viewer.getTextWidget().setStyleRanges(new StyleRange[] {
				textStyle(0, 36, COLOR_3RD_LINE), // whole 3rd line
				textStyle(36, 25, COLOR_4TH_LINE), // whole 4th line
				textStyle(61, 21, COLOR_5TH_LINE) // whole last displayed line - visible part of 5th line
		});
		viewer.addTextPresentationListener(this::textPresentationListener);
		
		TestUtils.waitForAndAssertCondition(1_000, () -> textPresentations.size() == 6);
		
		assertEquals(linkRegion(CONTENT_LINKS.get(0), document), textPresentations.get(0).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(1), document), textPresentations.get(1).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(2), document), textPresentations.get(2).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(3), document), textPresentations.get(3).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(4), document), textPresentations.get(4).getExtent());
		assertEquals(linkRegion(CONTENT_LINKS.get(5), document), textPresentations.get(5).getExtent());
		
		var styles = viewer.getTextWidget().getStyleRanges();
		
		assertEquals(9, styles.length);
		
		var pos = 0;
		var length = 0;
		assertEquals(linkStyle(pos, length = 10, COLOR_3RD_LINE), styles[0]);
		assertEquals(textStyle(pos += length, length = 10, COLOR_3RD_LINE), styles[1]);
		assertEquals(linkStyle(pos += length, length = 6, COLOR_3RD_LINE), styles[2]);
		assertEquals(textStyle(pos += length, length = 9 + 1, COLOR_3RD_LINE), styles[3]); // also new line char
		
		assertEquals(textStyle(pos += length, length = 9, COLOR_4TH_LINE), styles[4]);
		assertEquals(linkStyle(pos += length, length = 6, COLOR_4TH_LINE), styles[5]);
		assertEquals(textStyle(pos += length, length = 9 + 1, COLOR_4TH_LINE), styles[6]); // also new line char
		
		assertEquals(textStyle(pos += length, length = 9, COLOR_5TH_LINE), styles[7]);
		assertEquals(linkStyle(pos += length, length = 12, COLOR_5TH_LINE), styles[8]);
	}
	
	private StyleRange textStyle(int start, int length, Color backgroundColor ) {
		return new StyleRange(start, length, null, backgroundColor);
	}
	
	private StyleRange linkStyle(int start, int length, Color backgroundColor ) {
		var retVal = new StyleRange(start, length, null, backgroundColor);
		retVal.underline = true;
		return retVal;
	}

	private Region linkRegion(DocumentLink link, IDocument document) throws BadLocationException {
		var start = link.getRange().getStart();
		var end = link.getRange().getEnd();
		int startOffset = document.getLineOffset(start.getLine()) + start.getCharacter();
		return new Region(startOffset, (document.getLineOffset(end.getLine()) + end.getCharacter()) - startOffset);
	}

	private void textPresentationListener(TextPresentation textPresentation) {
		textPresentations.add(textPresentation);
	}
}
