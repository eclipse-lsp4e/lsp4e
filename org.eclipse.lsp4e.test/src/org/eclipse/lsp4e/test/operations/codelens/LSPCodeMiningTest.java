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

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.command.LSPCommandHandler;
import org.eclipse.lsp4e.operations.codelens.CodeLensProvider;
import org.eclipse.lsp4e.operations.codelens.LSPCodeMining;
import org.eclipse.lsp4e.test.AllCleanRule;
import org.eclipse.lsp4e.test.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Tests executing actions on server side or client side via registered
 * IHandler.
 */
public class LSPCodeMiningTest {

	private static final String MOCK_SERVER_ID = "org.eclipse.lsp4e.test.server";

	@Rule
	public AllCleanRule clear = new AllCleanRule();
	private IProject project;

	@Before
	public void setUp() throws CoreException {
		project = TestUtils.createProject(getClass().getName() + System.currentTimeMillis());
	}

	@Test
	public void testLSPCodeMiningActionClientSideHandling() throws BadLocationException, CoreException {
		String commandID = "test.command";
		final CodeLens lens = createCodeLens(commandID);

		final AtomicReference<Command> actualCommand = new AtomicReference<>(null);
		final AtomicReference<IPath> actualPath = new AtomicReference<>(null);

		// Create and register handler
		IHandler handler = new LSPCommandHandler() {
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

		LanguageServer languageServer = MockLanguageServer.INSTANCE;
		CodeLensProvider provider = new CodeLensProvider();

		LSPCodeMining sut = new LSPCodeMining(lens, document, languageServer, LanguageServersRegistry.getInstance().getDefinition(MOCK_SERVER_ID), provider);
		MouseEvent mouseEvent = createMouseEvent();
		sut.getAction().accept(mouseEvent);

		assertEquals(lens.getCommand(), actualCommand.get());
		assertEquals(file.getFullPath(), actualPath.get());
	}

	@Test
	public void testLSPCodeMiningActionServerSideHandling()
			throws InterruptedException, java.util.concurrent.ExecutionException, TimeoutException,
			BadLocationException, CoreException {
		final CodeLens lens = createCodeLens(MockLanguageServer.SUPPORTED_COMMAND_ID);
		Command command = lens.getCommand();
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("bar", 42);
		command.setArguments(Arrays.asList(new JsonPrimitive("Foo"), jsonObject));

		// Setup test data
		IFile file = TestUtils.createUniqueTestFile(project, "lspt", "test content");
		IDocument document = TestUtils.openTextViewer(file).getDocument();

		MockLanguageServer languageServer = MockLanguageServer.INSTANCE;
		CodeLensProvider provider = new CodeLensProvider();

		LSPCodeMining sut = new LSPCodeMining(lens, document, languageServer, LanguageServersRegistry.getInstance().getDefinition(MOCK_SERVER_ID), provider);
		MouseEvent mouseEvent = createMouseEvent();
		sut.getAction().accept(mouseEvent);

		// We expect that the language server will be called to execute the command
		ExecuteCommandParams executedCommand = languageServer.getWorkspaceService().getExecutedCommand().get(5,
				TimeUnit.SECONDS);

		assertEquals(MockLanguageServer.SUPPORTED_COMMAND_ID, executedCommand.getCommand());
		assertEquals(command.getArguments(), executedCommand.getArguments());
	}

	private static MouseEvent createMouseEvent() {
		Event event = new Event();
		event.button = SWT.BUTTON1;
		Display display = Display.getCurrent();
		event.widget = display.getSystemTray();
		return new MouseEvent(event);
	}

	private static CodeLens createCodeLens(String commandID) {
		CodeLens lens = new CodeLens();
		Position zero = new Position(0, 0);
		lens.setRange(new Range(zero, zero));
		Command command = new Command("TestCommand", commandID);
		lens.setCommand(command);
		return lens;
	}

}
