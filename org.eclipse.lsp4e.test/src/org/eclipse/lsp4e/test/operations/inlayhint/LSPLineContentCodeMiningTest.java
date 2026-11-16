/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.operations.inlayhint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.inlayhint.InlayHintProvider;
import org.eclipse.lsp4e.operations.inlayhint.LSPLineContentCodeMining;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class LSPLineContentCodeMiningTest extends AbstractTestWithProject {

	private static final String MOCK_SERVER_ID = "org.eclipse.lsp4e.test.server";

	@Test
	public void singleLabelPartCommand(MockLanguageServerFactory factory) throws Exception {
		final InlayHint inlay = createMultiLabelInlayHint(createInlayLabelPart("Label-Text", MockLanguageServer.SUPPORTED_COMMAND_ID));
		Command command = inlay.getLabel().getRight().get(0).getCommand();
		final var jsonObject = new JsonObject();
		jsonObject.addProperty("bar", 42);
		command.setArguments(List.of(new JsonPrimitive("Foo"), jsonObject));

		// Setup test data
		IFile file = TestUtils.createUniqueTestFile(project, "lspt", "test content");
		ITextViewer textViewer = TestUtils.openTextViewer(file);
		IDocument document = textViewer.getDocument();

		MockLanguageServer languageServer = factory.getServer();
		final var provider = new InlayHintProvider();

		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrapper(project, LanguageServersRegistry.getInstance().getDefinition(MOCK_SERVER_ID));

		final var sut = new LSPLineContentCodeMining(inlay, document, wrapper, provider);
		MouseEvent mouseEvent = createMouseEvent();
		sut.getAction().accept(mouseEvent);

		// We expect that the language server will be called to execute the command
		ExecuteCommandParams executedCommand = languageServer.getWorkspaceService().getExecutedCommand().get(5,
				TimeUnit.SECONDS);

		assertEquals(MockLanguageServer.SUPPORTED_COMMAND_ID, executedCommand.getCommand());
		assertEquals(command.getArguments(), executedCommand.getArguments());
	}
	
	@Test
	void inlayHintWithTextEdit() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "lspt", "x = [1, 2]");
		ITextViewer textViewer = TestUtils.openTextViewer(file);
		IDocument document = textViewer.getDocument();

		final var provider = new InlayHintProvider();

		// Create inlayhint with text edit
		InlayHint inlayHint = new InlayHint(new Position(0, 0), Either.forLeft(": list[int]"));
		inlayHint.setTextEdits(List.of(new TextEdit(new Range(new Position(0, 1), new Position(0, 1)), ": list[int]")));

		// Simulate user clicking on inlayhint
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrapper(project,
				LanguageServersRegistry.getInstance().getDefinition(MOCK_SERVER_ID));
		final var sut = new LSPLineContentCodeMining(inlayHint, document, wrapper, provider);
		MouseEvent mouseEvent = createMouseEvent();
		sut.getAction().accept(mouseEvent);

		// Text edit should be applied.
		assertEquals("x: list[int] = [1, 2]", document.get());
	}

	/**
	 * Hint places that real language servers send. {@code ^} marks the hint
	 * position. The expected values are the same as in VS Code.
	 */
	static Stream<Arguments> hintPlaces() {
		return Stream.of( //
				// rust-analyzer
				arguments("let x^ = 5;", ": i32", true), //
				arguments("let c = |y|^ y + 1;", " -> i32", true), //
				arguments("let n = add(^x, 2);", "a:", false), //
				arguments("let f = first(^\"a\", \"b\");", "s:", false), //
				arguments("fn first(s: &^str) -> &str { s }", "'s", false), //
				arguments("fn first^(s: &str) -> &str { s }", "<'s>", true), //
				arguments("enum E { A^, B }", " = 0", true), //
				arguments("let s = ^String::new();", "(&*", false), //
				arguments("let ^(m, k) = p;", "&", true), //
				arguments("    let s = String::new()^", "String", true), //
				arguments("}^", "fn main", true), //
				// clangd
				arguments("auto f()^ { return 1; }", "-> int", true), //
				arguments("P q{^3, 4};", ".x=", false), //
				arguments("auto n = add(4^);", ", b: 3", true), //
				// basedpyright
				arguments("def g()^:", "-> Literal['s']", true), //
				arguments("x = add(^1, 2)", "a=", false), //
				// typescript-language-server
				arguments("foo(^\"str\");", "text:", false), //
				arguments("const arr = [1, 2].map(n^ => n * 2);", ": number", true), //
				// other places
				arguments("scale(^-1.5)", "factor:", false), //
				arguments("scale(^.5)", "factor:", false), //
				arguments("f(^...xs)", "args:", false), //
				arguments("f(^::g())", "x:", false), //
				arguments("f(^@\"s\")", "x:", false), //
				arguments("foo(^(1))", "x:", true), //
				arguments("foo(\u00a0^a)", "x:", false), //
				arguments("^x", "hint", false), //
				arguments("  ^", "hint", true));
	}

	@ParameterizedTest
	@MethodSource("hintPlaces")
	void hintIsAfterPositionLikeInVSCode(String line, String label, boolean expected) throws Exception {
		int offset = line.indexOf('^');
		String text = line.substring(0, offset) + line.substring(offset + 1) + "\nnext line";
		assertEquals(expected, isAfterPosition(text, new Position(0, offset), label), line);
	}

	@Test
	void hintAtLineEndOrDocumentEnd() throws Exception {
		assertTrue(isAfterPosition("let x", new Position(0, 5), ": i32"));
		assertTrue(isAfterPosition("let x\r\nnext", new Position(0, 5), ": i32"));
		// positions that are not valid any more
		assertTrue(isAfterPosition("let x\r\nnext", new Position(0, 6), ": i32"));
		assertTrue(isAfterPosition("let x", new Position(0, 99), ": i32"));
		assertTrue(isAfterPosition("let x", new Position(5, 0), ": i32"));
	}

	@Test
	void hintOnEmptyLine() throws Exception {
		assertFalse(isAfterPosition("", new Position(0, 0), "hint"));
		assertFalse(isAfterPosition("a\n\nb", new Position(1, 0), "hint"));
	}

	private boolean isAfterPosition(String text, Position position, String label) throws Exception {
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrapper(project,
				LanguageServersRegistry.getInstance().getDefinition(MOCK_SERVER_ID));
		final var inlayHint = new InlayHint(position, Either.forLeft(label));
		return new LSPLineContentCodeMining(inlayHint, new Document(text), wrapper, new InlayHintProvider())
				.isAfterPosition();
	}

	private static InlayHintLabelPart createInlayLabelPart(String text, String commandID) {
		final var labelPart = new InlayHintLabelPart(text);
		final var command = new Command(text, commandID);
		labelPart.setCommand(command);
		return labelPart;
	}

	private static InlayHint createMultiLabelInlayHint(InlayHintLabelPart... parts) {
		final var inlay = new InlayHint();
		inlay.setLabel(List.of(parts));
		inlay.setPosition(new Position(0, 0));
		return inlay;
	}

	private static MouseEvent createMouseEvent() {
		final var event = new Event();
		event.button = SWT.BUTTON1;
		Display display = Display.getCurrent();
		event.widget = display.getSystemTray();
		return new MouseEvent(event);
	}

}
