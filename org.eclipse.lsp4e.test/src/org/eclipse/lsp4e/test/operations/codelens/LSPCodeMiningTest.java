/*******************************************************************************
 * Copyright (c) 2019 Fraunhofer FOKUS and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Max Bureck - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.operations.codelens;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.command.LSPCommandHandler;
import org.eclipse.lsp4e.operations.codelens.CodeLensProvider;
import org.eclipse.lsp4e.operations.codelens.LSPCodeMining;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Tests executing actions on server side or client side via registered
 * IHandler.
 */
public class LSPCodeMiningTest extends AbstractTestWithProject {

	private static final String MOCK_SERVER_ID = "org.eclipse.lsp4e.test.server";

	@Test
	public void testLSPCodeMiningActionClientSideHandling() throws Exception {
		final var commandID = "test.command";
		final CodeLens lens = createCodeLens(commandID);

		final var actualCommand = new AtomicReference<Command>(null);
		final var actualPath = new AtomicReference<IPath>(null);

		// Create and register handler
		final var handler = new LSPCommandHandler() {
			@Override
			public Object execute(ExecutionEvent event, Command command, IPath context) throws ExecutionException {
				actualCommand.set(command);
				actualPath.set(context);
				return null;
			}
		};
		IHandlerService handlerService = PlatformUI.getWorkbench().getService(IHandlerService.class);
		handlerService.activateHandler(commandID, handler);

		// Setup test data
		IFile file = TestUtils.createUniqueTestFile(project, "lspt", "test content");
		IDocument document = TestUtils.openTextViewer(file).getDocument();

		final var provider = new CodeLensProvider();
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrapper(project, LanguageServersRegistry.getInstance().getDefinition(MOCK_SERVER_ID));

		final var sut = new LSPCodeMining(lens, document, wrapper, provider);
		MouseEvent mouseEvent = createMouseEvent();
		sut.getAction().accept(mouseEvent);

		assertEquals(lens.getCommand(), actualCommand.get());
		assertEquals(file.getFullPath(), actualPath.get());
	}

	@Test
	public void testLSPCodeMiningActionServerSideHandling()
			throws Exception {
		final CodeLens lens = createCodeLens(MockLanguageServer.SUPPORTED_COMMAND_ID);
		Command command = lens.getCommand();
		final var jsonObject = new JsonObject();
		jsonObject.addProperty("bar", 42);
		command.setArguments(List.of(new JsonPrimitive("Foo"), jsonObject));

		// Setup test data
		IFile file = TestUtils.createUniqueTestFile(project, "lspt", "test content");
		IDocument document = TestUtils.openTextViewer(file).getDocument();

		MockLanguageServer languageServer = MockLanguageServer.INSTANCE;
		final var provider = new CodeLensProvider();

		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrapper(project, LanguageServersRegistry.getInstance().getDefinition(MOCK_SERVER_ID));

		final var sut = new LSPCodeMining(lens, document, wrapper, provider);		MouseEvent mouseEvent = createMouseEvent();
		sut.getAction().accept(mouseEvent);

		// We expect that the language server will be called to execute the command
		ExecuteCommandParams executedCommand = languageServer.getWorkspaceService().getExecutedCommand().get(5,
				TimeUnit.SECONDS);

		assertEquals(MockLanguageServer.SUPPORTED_COMMAND_ID, executedCommand.getCommand());
		assertEquals(command.getArguments(), executedCommand.getArguments());
	}

	private static MouseEvent createMouseEvent() {
		final var event = new Event();
		event.button = SWT.BUTTON1;
		Display display = Display.getCurrent();
		event.widget = display.getSystemTray();
		return new MouseEvent(event);
	}

	private static CodeLens createCodeLens(String commandID) {
		final var lens = new CodeLens();
		final var zero = new Position(0, 0);
		lens.setRange(new Range(zero, zero));
		final var command = new Command("TestCommand", commandID);
		lens.setCommand(command);
		return lens;
	}
}
