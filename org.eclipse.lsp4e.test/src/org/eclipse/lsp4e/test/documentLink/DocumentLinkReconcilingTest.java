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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.source.projection.ProjectionAnnotation;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.graphics.Color;
import org.junit.jupiter.api.Test;

public class DocumentLinkReconcilingTest extends AbstractTestWithProject {
	
	private static final String CONTENT = """
				1st-line #LINK1 1st-line
				2nd-line #LINK2_START
				#LINK2_END 3rd-line #LINK3 3rd-line
				#LINK4
				5th-line #LINK5_START_#LINK5_END
				#LINK6_START
				#LINK6_END
				8th-line #LINK7 8th-line""";
	
	private static final List<DocumentLink> CONTENT_LINKS = List.of(
			new DocumentLink(new Range(new Position(0, 9), new Position(0, 15)), "file://link1"),
			new DocumentLink(new Range(new Position(1, 9), new Position(2, 10)), "file://link2"),
			new DocumentLink(new Range(new Position(2, 20), new Position(2, 26)), "file://link3"),
			new DocumentLink(new Range(new Position(3, 0), new Position(3, 6)), "file://link4"),
			new DocumentLink(new Range(new Position(4, 9), new Position(4, 32)), "file://link5"),
			new DocumentLink(new Range(new Position(5, 0), new Position(6, 10)), "file://link6"),
			new DocumentLink(new Range(new Position(7, 9), new Position(7, 15)), "file://link7")
			);
	
	public static final Color COLOR_1ST_LINE = new Color(255, 0, 0);
	public static final Color COLOR_2ND_LINE = new Color(225, 255, 0);
	public static final Color COLOR_3RD_LINE = new Color(255, 0, 255);
	public static final Color COLOR_4TH_LINE = new Color(0, 255, 0);
	public static final Color COLOR_5TH_LINE = new Color(0, 255, 255);
	public static final Color COLOR_6TH_LINE = new Color(128, 128, 255);
	public static final Color COLOR_7TH_LINE = new Color(128, 0, 0);
	public static final Color COLOR_8TH_LINE = new Color(0, 128, 0);
	
	private List<TextPresentation> textPresentations = new ArrayList<>(4);
	
	@Test
	public void testFullDocumentLinkReconciling() throws Exception {
		MockLanguageServer.INSTANCE.setDocumentLinks(CONTENT_LINKS);

		TextViewer viewer = (TextViewer) TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, CONTENT));
		IDocument doc = viewer.getDocument();
		var pos = 0;
		var len = 0;
		viewer.getTextWidget().setStyleRanges(new StyleRange[] {
				textStyle(pos, len = doc.getLineLength(0), COLOR_1ST_LINE), // whole 1st line
				textStyle(pos += len, len = doc.getLineLength(1), COLOR_2ND_LINE), // whole 2nd line
				textStyle(pos += len, len = doc.getLineLength(2), COLOR_3RD_LINE), // whole 3rd line
				textStyle(pos += len, len = doc.getLineLength(3), COLOR_4TH_LINE), // whole 4th line
				textStyle(pos += len, len = doc.getLineLength(4), COLOR_5TH_LINE), // whole 5th line
				textStyle(pos += len, len = doc.getLineLength(5), COLOR_6TH_LINE), // whole 6th line
				textStyle(pos += len, len = doc.getLineLength(6), COLOR_7TH_LINE), // whole 7th line
				textStyle(pos += len, doc.getLineLength(7), COLOR_8TH_LINE) // whole 8th line
		});
		viewer.addTextPresentationListener(this::textPresentationListener);
		
		waitForAndAssertTextPresentations(doc);
		
		assertEquals(1, getStylesCount(textPresentations.get(0)));
		assertEquals(2, getStylesCount(textPresentations.get(1))); // link2 spans 2 visible lines, 2nd and 3rd
		assertEquals(1, getStylesCount(textPresentations.get(2)));
		assertEquals(1, getStylesCount(textPresentations.get(3)));
		assertEquals(1, getStylesCount(textPresentations.get(4)));
		assertEquals(2, getStylesCount(textPresentations.get(5))); // link6 spans 2 visible lines, 6th and 7th
		assertEquals(1, getStylesCount(textPresentations.get(6)));
		
		var styles = viewer.getTextWidget().getStyleRanges();
		
		assertEquals(20, styles.length);
		
		pos = 0;
		len = 0;
		assertEquals(textStyle(pos, len = 9, COLOR_1ST_LINE), styles[0]);
		assertEquals(linkStyle(pos += len, len = 6, COLOR_1ST_LINE), styles[1]);
		assertEquals(textStyle(pos += len, len = 9 + 1, COLOR_1ST_LINE), styles[2]); // also new line char
		
		assertEquals(textStyle(pos += len, len = 9, COLOR_2ND_LINE), styles[3]);
		assertEquals(linkStyle(pos += len, len = 12 + 1, COLOR_2ND_LINE), styles[4]); // also new line char
		
		assertEquals(linkStyle(pos += len, len = 10, COLOR_3RD_LINE), styles[5]);
		assertEquals(textStyle(pos += len, len = 10, COLOR_3RD_LINE), styles[6]);
		assertEquals(linkStyle(pos += len, len = 6, COLOR_3RD_LINE), styles[7]);
		assertEquals(textStyle(pos += len, len = 9 + 1, COLOR_3RD_LINE), styles[8]); // also new line char
		
		assertEquals(linkStyle(pos += len, len = 6, COLOR_4TH_LINE), styles[9]);
		assertEquals(textStyle(pos += len, len = 1, COLOR_4TH_LINE), styles[10]); // new line char
		
		assertEquals(textStyle(pos += len, len = 9, COLOR_5TH_LINE), styles[11]);
		assertEquals(linkStyle(pos += len, len = 23, COLOR_5TH_LINE), styles[12]);
		assertEquals(textStyle(pos += len, len = 1, COLOR_5TH_LINE), styles[13]); // new line char

		assertEquals(linkStyle(pos += len, len = 12 + 1, COLOR_6TH_LINE), styles[14]); // also new line char
		
		assertEquals(linkStyle(pos += len, len = 10, COLOR_7TH_LINE), styles[15]);
		assertEquals(textStyle(pos += len, len = 1, COLOR_7TH_LINE), styles[16]); // new line char
		
		assertEquals(textStyle(pos += len, len = 9, COLOR_8TH_LINE), styles[17]);
		assertEquals(linkStyle(pos += len, len = 6, COLOR_8TH_LINE), styles[18]);
		assertEquals(textStyle(pos += len, 9, COLOR_8TH_LINE), styles[19]);
	}

	@Test
	public void testClippedDocumentLinkReconciling() throws Exception {
		MockLanguageServer.INSTANCE.setDocumentLinks(CONTENT_LINKS);
		
		TextViewer viewer = (TextViewer) TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, CONTENT));
		IDocument doc = viewer.getDocument();
		int line5visibleLength = 21;
		int line3Start = doc.getLineOffset(2);
		int middleOfLink5 = doc.getLineOffset(4) + line5visibleLength;
		// set visible region to 3rd + 4th + half of 5th line, link1 + link6 + link7 are completely outside
		viewer.setVisibleRegion(
				line3Start, // TextViewer would align start of visible region to start of the line anyway
				middleOfLink5 - line3Start);
		var pos = 0;
		var len = 0;
		viewer.getTextWidget().setStyleRanges(new StyleRange[] {
				textStyle(pos += len, len = doc.getLineLength(2), COLOR_3RD_LINE), // whole 3rd line
				textStyle(pos += len, len = doc.getLineLength(3), COLOR_4TH_LINE), // whole 4th line
				textStyle(pos += len, line5visibleLength, COLOR_5TH_LINE) // visible part of 5th line
		});
		viewer.addTextPresentationListener(this::textPresentationListener);
		
		waitForAndAssertTextPresentations(doc);
		
		assertEquals(1, getStylesCount(textPresentations.get(0)));
		assertEquals(2, getStylesCount(textPresentations.get(1))); // link2 spans 2 lines, invisible 2nd and visible 3rd
		assertEquals(1, getStylesCount(textPresentations.get(2)));
		assertEquals(1, getStylesCount(textPresentations.get(3)));
		assertEquals(2, getStylesCount(textPresentations.get(4))); // end of visible region cuts link into 2 styles
		assertEquals(1, getStylesCount(textPresentations.get(5)));
		assertEquals(1, getStylesCount(textPresentations.get(6)));
		
		var styles = viewer.getTextWidget().getStyleRanges();
		
		assertEquals(8, styles.length);
		
		pos = 0;
		len = 0;
		assertEquals(linkStyle(pos, len = 10, COLOR_3RD_LINE), styles[0]);
		assertEquals(textStyle(pos += len, len = 10, COLOR_3RD_LINE), styles[1]);
		assertEquals(linkStyle(pos += len, len = 6, COLOR_3RD_LINE), styles[2]);
		assertEquals(textStyle(pos += len, len = 9 + 1, COLOR_3RD_LINE), styles[3]); // also new line char
		
		assertEquals(linkStyle(pos += len, len = 6, COLOR_4TH_LINE), styles[4]);
		assertEquals(textStyle(pos += len, len = 1, COLOR_4TH_LINE), styles[5]); // new line char
		
		assertEquals(textStyle(pos += len, len = 9, COLOR_5TH_LINE), styles[6]);
		assertEquals(linkStyle(pos += len, 12, COLOR_5TH_LINE), styles[7]);
	}
	
	@Test
	public void testDocumentWithFoldingLinkReconciling() throws Exception {
		MockLanguageServer.INSTANCE.setDocumentLinks(CONTENT_LINKS);
		
		ProjectionViewer viewer = (ProjectionViewer) TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, CONTENT));
		IDocument doc = viewer.getDocument();
		// line 1 visible
		foldLines(0, 1, viewer); // fold line 2 into line 1
		// line 3 visible
		// line 4 visible
		foldLines(3, 4, viewer); // fold line 5 into line 4
		// line 6 visible
		foldLines(5, 6, viewer); // fold line 7 into line 6
		// line 8 visible
		
		var pos = 0;
		var len = 0;
		viewer.getTextWidget().setStyleRanges(new StyleRange[] {
				textStyle(pos, len = doc.getLineLength(0), COLOR_1ST_LINE), // whole 1st line
				textStyle(pos += len, len = doc.getLineLength(2), COLOR_3RD_LINE), // whole 3rd line
				textStyle(pos += len, len = doc.getLineLength(3), COLOR_4TH_LINE), // whole 4th line
				textStyle(pos += len, len = doc.getLineLength(5), COLOR_6TH_LINE), // whole 6th line
				textStyle(pos += len, doc.getLineLength(7), COLOR_8TH_LINE) // whole 8th line
		});
		viewer.addTextPresentationListener(this::textPresentationListener);
		
		waitForAndAssertTextPresentations(doc);
		
		assertEquals(1, getStylesCount(textPresentations.get(0)));
		assertEquals(2, getStylesCount(textPresentations.get(1))); // link2 spans 2 lines, folded 2nd and visible 3rd
		assertEquals(1, getStylesCount(textPresentations.get(2)));
		assertEquals(1, getStylesCount(textPresentations.get(3)));
		assertEquals(1, getStylesCount(textPresentations.get(4)));
		assertEquals(2, getStylesCount(textPresentations.get(5))); // link6 spans 2 lines, visible 6th and folded 7th
		assertEquals(1, getStylesCount(textPresentations.get(6)));
		
		var styles = viewer.getTextWidget().getStyleRanges();
		
		assertEquals(13, styles.length);
		
		pos = 0;
		len = 0;
		assertEquals(textStyle(pos, len = 9, COLOR_1ST_LINE), styles[0]);
		assertEquals(linkStyle(pos += len, len = 6, COLOR_1ST_LINE), styles[1]);
		assertEquals(textStyle(pos += len, len = 9 + 1, COLOR_1ST_LINE), styles[2]); // also new line char
		
		assertEquals(linkStyle(pos += len, len = 10, COLOR_3RD_LINE), styles[3]);
		assertEquals(textStyle(pos += len, len = 10, COLOR_3RD_LINE), styles[4]);
		assertEquals(linkStyle(pos += len, len = 6, COLOR_3RD_LINE), styles[5]);
		assertEquals(textStyle(pos += len, len = 9 + 1, COLOR_3RD_LINE), styles[6]); // also new line char
		
		assertEquals(linkStyle(pos += len, len = 6, COLOR_4TH_LINE), styles[7]);
		assertEquals(textStyle(pos += len, len = 1, COLOR_4TH_LINE), styles[8]); // new line char
		
		assertEquals(linkStyle(pos += len, len = 12 + 1, COLOR_6TH_LINE), styles[9]); // also new line char
		
		assertEquals(textStyle(pos += len, len = 9, COLOR_8TH_LINE), styles[10]);
		assertEquals(linkStyle(pos += len, len = 6, COLOR_8TH_LINE), styles[11]);
		assertEquals(textStyle(pos += len, 9, COLOR_8TH_LINE), styles[12]);
	}
	
	private void waitForAndAssertTextPresentations(IDocument document) throws BadLocationException {
		TestUtils.waitForAndAssertCondition(1_000, () -> textPresentations.size() == 7);
		
		Region linkRegion = linkRegion(0, document); 
		assertEquals(linkRegion, textPresentations.get(0).getExtent());
		assertEquals(linkRegion, textPresentations.get(0).getCoverage());
		
		linkRegion = linkRegion(1, document); 
		assertEquals(linkRegion, textPresentations.get(1).getExtent());
		assertEquals(linkRegion, textPresentations.get(1).getCoverage());
		
		linkRegion = linkRegion(2, document); 
		assertEquals(linkRegion, textPresentations.get(2).getExtent());
		assertEquals(linkRegion, textPresentations.get(2).getCoverage());
		
		linkRegion = linkRegion(3, document); 
		assertEquals(linkRegion, textPresentations.get(3).getExtent());
		assertEquals(linkRegion, textPresentations.get(3).getCoverage());
		
		linkRegion = linkRegion(4, document); 
		assertEquals(linkRegion, textPresentations.get(4).getExtent());
		assertEquals(linkRegion, textPresentations.get(4).getCoverage());
		
		linkRegion = linkRegion(5, document); 
		assertEquals(linkRegion, textPresentations.get(5).getExtent());
		assertEquals(linkRegion, textPresentations.get(5).getCoverage());
		
		linkRegion = linkRegion(6, document); 
		assertEquals(linkRegion, textPresentations.get(6).getExtent());
		assertEquals(linkRegion, textPresentations.get(6).getCoverage());
	}
	
	private int getStylesCount(TextPresentation presentation) {
		var list = new ArrayList<>(2);
		presentation.getAllStyleRangeIterator().forEachRemaining(list::add);
		return list.size();
	}

	private StyleRange textStyle(int start, int length, Color backgroundColor ) {
		return new StyleRange(start, length, null, backgroundColor);
	}
	
	private StyleRange linkStyle(int start, int length, Color backgroundColor ) {
		var retVal = new StyleRange(start, length, null, backgroundColor);
		retVal.underline = true;
		return retVal;
	}

	private Region linkRegion(int contentlinkPos, IDocument document) throws BadLocationException {
		DocumentLink link = CONTENT_LINKS.get(contentlinkPos);
		var start = link.getRange().getStart();
		var end = link.getRange().getEnd();
		int startOffset = document.getLineOffset(start.getLine()) + start.getCharacter();
		return new Region(startOffset, (document.getLineOffset(end.getLine()) + end.getCharacter()) - startOffset);
	}
	
	private void foldLines(int startLine, int endLine, ProjectionViewer viewer) throws BadLocationException {
		var doc = viewer.getDocument();
		int firstLineOffset = doc.getLineOffset(startLine);
		int lastLineEndOffset = doc.getLineOffset(endLine) + doc.getLineLength(endLine);
		viewer.getProjectionAnnotationModel().addAnnotation(
				new ProjectionAnnotation(true),
				new org.eclipse.jface.text.Position(firstLineOffset, lastLineEndOffset - firstLineOffset));
	}
	
	private void textPresentationListener(TextPresentation textPresentation) {
		textPresentations.add(textPresentation);
	}
}
