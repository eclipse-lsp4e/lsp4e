package org.eclipse.lsp4e.test.jdt.textblocks;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.lsp4e.jdt.internal.TextBlockSourceContentMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JavaTextBlockSourceMapperTests {
	@ParameterizedTest
	@ValueSource(strings = {
			"simple",
			"indented",
			"trailing_whitespace_normal",
			"escapes",
			"escaped_lf"
	})
	void testMapBothDirectionsFromResourceFile(String name) throws IOException {
		try (InputStream sourceStream = getClass().getResourceAsStream("rsc/" + name + "_source.txt");
				InputStream contentStream = getClass().getResourceAsStream("rsc/" + name + "_content.txt")) {
			String source = new String(sourceStream.readAllBytes());
			String content = new String(contentStream.readAllBytes());
			testMapBothDirectionsWithCursorMarkers(source.replace("\r\n", "\n"), content, false);
			testMapBothDirectionsWithCursorMarkers(source.replaceAll("\r?\n", "\r\n"), content, false);
		}
	}
	
	@ParameterizedTest
	@ValueSource(strings = {
			"trailing_whitespace_noskip"
	})
	void testMapBothDirectionsFromResourceFileLeaveTrailingWhitespace(String name) throws IOException {
		try (InputStream sourceStream = getClass().getResourceAsStream("rsc/" + name + "_source.txt");
				InputStream contentStream = getClass().getResourceAsStream("rsc/" + name + "_content.txt")) {
			String source = new String(sourceStream.readAllBytes());
			String content = new String(contentStream.readAllBytes());
			testMapBothDirectionsWithCursorMarkers(source, content, true);
		}
	}
	
	private void testMapBothDirectionsWithCursorMarkers(String sourceCodePart, String textBlockContent, boolean doNotSkipTrailingWhitespaceAtCursor) {
		int sourceCodeIndex = sourceCodePart.indexOf('|');
		int textBlockIndex = textBlockContent.indexOf('|');
		// ensure content uses UNIX-style line feeds if the file was checked out with Windows line feeds
		textBlockContent = textBlockContent.replace("\r\n", "\n");
		testMapBothDirections(removeCharacterAt(sourceCodePart, sourceCodeIndex), sourceCodeIndex,
				removeCharacterAt(textBlockContent, textBlockIndex), textBlockIndex,
				doNotSkipTrailingWhitespaceAtCursor ? sourceCodeIndex : -1);
	}

	private String removeCharacterAt(String content, int indexToRemove) {
		return content.substring(0, indexToRemove) + content.substring(indexToRemove + 1);
	}
	
	private void testMapBothDirections(String sourceCodePart, int sourceCodeIndex, String textBlockContent, int textBlockIndex, int doNotSkipTrailingWhitespaceAt) {
		int resolvedTextBlockIndex = TextBlockSourceContentMapper.mapSourceToContent(sourceCodePart.toCharArray(), textBlockContent.toCharArray(), sourceCodeIndex, doNotSkipTrailingWhitespaceAt);
		assertEquals(textBlockIndex, resolvedTextBlockIndex, "incorrect result when mapping source index " + sourceCodeIndex + " to text block content in text block:\n" + sourceCodePart);
		int resolvedSourceCodeIndex = TextBlockSourceContentMapper.mapContentToSource(sourceCodePart.toCharArray(), textBlockContent.toCharArray(), textBlockIndex, doNotSkipTrailingWhitespaceAt);
		assertEquals(sourceCodeIndex, resolvedSourceCodeIndex, "incorrect result when mapping content index " + textBlockIndex + " to get source code index in source code:\n" + sourceCodePart);
	}
}
