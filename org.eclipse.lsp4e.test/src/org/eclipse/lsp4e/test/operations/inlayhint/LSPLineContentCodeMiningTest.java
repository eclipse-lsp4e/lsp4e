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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
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
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.Position;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class LSPLineContentCodeMiningTest extends AbstractTestWithProject {

	private static final String MOCK_SERVER_ID = "org.eclipse.lsp4e.test.server";

	@Test
	public void singleLabelPartCommand() throws Exception {
		final InlayHint inlay = createMultiLabelInlayHint(createInlayLabelPart("Label-Text", MockLanguageServer.SUPPORTED_COMMAND_ID));
		Command command = inlay.getLabel().getRight().get(0).getCommand();
		final var jsonObject = new JsonObject();
		jsonObject.addProperty("bar", 42);
		command.setArguments(List.of(new JsonPrimitive("Foo"), jsonObject));

		// Setup test data
		IFile file = TestUtils.createUniqueTestFile(project, "lspt", "test content");
		ITextViewer textViewer = TestUtils.openTextViewer(file);
		IDocument document = textViewer.getDocument();

		MockLanguageServer languageServer = MockLanguageServer.INSTANCE;
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
