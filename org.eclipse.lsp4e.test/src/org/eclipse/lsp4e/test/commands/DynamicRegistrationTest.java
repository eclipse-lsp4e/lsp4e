/*******************************************************************************
 * Copyright (c) 2018 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Kris De Volder - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.commands;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

public class DynamicRegistrationTest extends AbstractTestWithProject {

	private static final String WORKSPACE_EXECUTE_COMMAND = "workspace/executeCommand";
	private static final String WORKSPACE_DID_CHANGE_FOLDERS = "workspace/didChangeWorkspaceFolders";

	@BeforeEach
	public void setUp() throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseExtension.lspt", "");

		// Make sure mock language server is created...
		IDocument document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServers.forDocument(document).anyMatching();

		waitForCondition(5_000, () -> !MockLanguageServer.INSTANCE.getRemoteProxies().isEmpty());
		getMockClient();
	}

	@Test
	public void testCommandRegistration() throws Exception {
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> true));

		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command")));

		UUID registration = registerCommands("test.command", "test.command.2");
		try {
			assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command")));
			assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command.2")));
		} finally {
			unregister(registration);
		}
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command")));
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command.2")));
	}

	@Test
	public void testWorkspaceFoldersRegistration() throws Exception {
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> true));

		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(c -> hasWorkspaceFolderSupport(c)));

		UUID registration = registerWorkspaceFolders();
		try {
			assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> hasWorkspaceFolderSupport(c)));
		} finally {
			unregister(registration);
		}
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(c -> hasWorkspaceFolderSupport(c)));
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> !hasWorkspaceFolderSupport(c)));
	}

	//////////////////////////////////////////////////////////////////////////////////

	private void unregister(UUID registration) throws Exception {
		LanguageClient client = getMockClient();
		final var unregistration = new Unregistration(registration.toString(), WORKSPACE_EXECUTE_COMMAND);
		client.unregisterCapability(new UnregistrationParams(List.of(unregistration)))
			.get(1, TimeUnit.SECONDS);
	}

	private UUID registerWorkspaceFolders() throws Exception {
		UUID id = UUID.randomUUID();
		LanguageClient client = getMockClient();
		final var registration = new Registration();
		registration.setId(id.toString());
		registration.setMethod(WORKSPACE_DID_CHANGE_FOLDERS);
		client.registerCapability(new RegistrationParams(List.of(registration)))
			.get(1, TimeUnit.SECONDS);
		return id;
	}

	private UUID registerCommands(String... command) throws Exception {
		UUID id = UUID.randomUUID();
		LanguageClient client = getMockClient();
		final var registration = new Registration();
		registration.setId(id.toString());
		registration.setMethod(WORKSPACE_EXECUTE_COMMAND);
		registration.setRegisterOptions(new Gson().toJsonTree(new ExecuteCommandOptions(List.of(command))));
		client.registerCapability(new RegistrationParams(List.of(registration))).get(1, TimeUnit.SECONDS);
		return id;
	}

	private LanguageClient getMockClient() {
		List<LanguageClient> proxies = MockLanguageServer.INSTANCE.getRemoteProxies();
		assertEquals(1, proxies.size());
		return proxies.get(0);
	}

	private Predicate<ServerCapabilities> handlesCommand(String command) {
		return cap -> {
			ExecuteCommandOptions commandProvider = cap.getExecuteCommandProvider();
			return commandProvider != null && commandProvider.getCommands().contains(command);
		};
	}

	private boolean hasWorkspaceFolderSupport(ServerCapabilities cap) {
		if (cap != null) {
			WorkspaceServerCapabilities ws = cap.getWorkspace();
			if (ws != null) {
				WorkspaceFoldersOptions f = ws.getWorkspaceFolders();
				if (f != null) {
					return f.getSupported();
				}
			}
		}
		return false;
	}
}
