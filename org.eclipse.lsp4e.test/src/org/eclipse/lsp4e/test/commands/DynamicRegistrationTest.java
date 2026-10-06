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

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.codeactions.CodeActionTests;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.tests.mock.MockWorkspaceService;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionRegistrationOptions;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.junit.jupiter.api.Test;

public class DynamicRegistrationTest extends AbstractTestWithProject {

	private static final String WORKSPACE_EXECUTE_COMMAND = "workspace/executeCommand";
	private static final String WORKSPACE_DID_CHANGE_FOLDERS = "workspace/didChangeWorkspaceFolders";
	private static final String WORKSPACE_DID_CHANGE_WATCHED_FILES = "workspace/didChangeWatchedFiles";
	private static final String TEXT_DOCUMENT_CODE_ACTION = "textDocument/codeAction";

	@Test
	public void testCommandRegistration(MockLanguageServerFactory factory) throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseExtension.lspt", "");
		// Make sure mock language server is created...
		IDocument document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServers.forDocument(document).anyMatching();
		
		waitForCondition(5_000, () -> !factory.getServers().isEmpty());
		
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> true));

		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command")));

		UUID registration = registerCommands(factory.getServer(), "test.command", "test.command.2");
		try {
			assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command")));
			assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command.2")));
		} finally {
			unregister(registration, WORKSPACE_EXECUTE_COMMAND, factory.getServer());
		}
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command")));
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCommand("test.command.2")));
	}

	@Test
	public void testDynamicCodeActionRegistration(MockLanguageServerFactory factory) throws Exception {
		final var testFile = TestUtils.createFile(project, "shouldUseExtension.lspt", "");
		final var resolveCount = new AtomicInteger(0);

		final var staticCapabilities = MockLanguageServer.defaultServerCapabilities();
		staticCapabilities.setCodeActionProvider(Boolean.FALSE);
		factory.withCapabilities(() -> staticCapabilities);
		factory.withConfiguration((idx, server) -> {
			server.getTextDocumentService().setCodeActionResolver(action -> {
				resolveCount.incrementAndGet();
				return action;
			});
			final var tEdit = new TextEdit(new Range(new Position(0, 0), new Position(0, 5)), "fixed");
			final var wEdit = new WorkspaceEdit(Collections.singletonMap(testFile.getLocationURI().toString(), List.of(tEdit)));
			final var codeAction = new CodeAction("fixme");
			codeAction.setCommand(new Command("editCommand", "mockEditCommand", List.of(wEdit)));
			server.setCodeActions(List.of(Either.forRight(codeAction)));
			server.setDiagnostics(List.of(
					new Diagnostic(new Range(new Position(0, 0), new Position(0, 5)), "error", DiagnosticSeverity.Error, null)));
		});

		final var document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServers.forDocument(document).anyMatching();
		waitForCondition(5_000, () -> !factory.getServers().isEmpty());
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> true));
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActions()));

		// A real server sends CodeActionRegistrationOptions (an object), never a bare boolean
		final var options = new CodeActionRegistrationOptions();
		options.setCodeActionKinds(List.of(CodeActionKind.QuickFix, CodeActionKind.Refactor));
		options.setResolveProvider(Boolean.FALSE);
		final UUID firstRegistration = registerCodeActionProvider(factory.getServer(), options);

		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActions(o -> //
				o.getCodeActionKinds().containsAll(List.of(CodeActionKind.QuickFix, CodeActionKind.Refactor))
				&& Boolean.FALSE.equals(o.getResolveProvider()))));

		final var editor = (AbstractTextEditor) TestUtils.openEditor(testFile);
		IMarker m = CodeActionTests.assertDiagnostics(testFile, "error", "fixme");
		CodeActionTests.assertResolution(editor, m, "fixed");
		// This registration says the server does not support codeAction/resolve
		assertEquals(0, resolveCount.get());

		TestUtils.closeEditor(editor, false);
		unregister(firstRegistration, TEXT_DOCUMENT_CODE_ACTION, factory.getServer());
		// back to the static state (no code actions)
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActions()));

		options.setResolveProvider(Boolean.TRUE);
		registerCodeActionProvider(factory.getServer(), options);
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActions()));

		final var editor2 = (AbstractTextEditor) TestUtils.openEditor(testFile);
		IMarker m2 = CodeActionTests.assertDiagnostics(testFile, "error", "fixme");
		CodeActionTests.assertResolution(editor2, m2, "fixed");
		// Now codeAction/resolve is supported and must have been used
		assertEquals(1, resolveCount.get());
	}

	@Test
	public void testCodeActionRegistrationWithoutOptions(MockLanguageServerFactory factory) throws Exception {
		final var staticCapabilities = MockLanguageServer.defaultServerCapabilities();
		staticCapabilities.setCodeActionProvider(Boolean.FALSE);
		factory.withCapabilities(() -> staticCapabilities);
		startServer(factory);
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActions()));

		// registerOptions is optional: a registration without it simply enables the capability
		final UUID registration = registerCodeActionProvider(factory.getServer(), null);
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActions()));

		unregister(registration, TEXT_DOCUMENT_CODE_ACTION, factory.getServer());
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActions()));
	}

	@Test
	public void testOverlappingCodeActionRegistrations(MockLanguageServerFactory factory) throws Exception {
		final var staticCapabilities = MockLanguageServer.defaultServerCapabilities();
		final var staticOptions = new CodeActionOptions(List.of(CodeActionKind.Source));
		staticCapabilities.setCodeActionProvider(staticOptions);
		factory.withCapabilities(() -> staticCapabilities);
		startServer(factory);
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActionKinds(List.of(CodeActionKind.Source))));

		final var quickFix = new CodeActionRegistrationOptions();
		quickFix.setCodeActionKinds(List.of(CodeActionKind.QuickFix));
		final var refactor = new CodeActionRegistrationOptions();
		refactor.setCodeActionKinds(List.of(CodeActionKind.Refactor));

		// Without document selectors both registrations apply everywhere: the most recent wins as a whole
		final UUID first = registerCodeActionProvider(factory.getServer(), quickFix);
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActionKinds(List.of(CodeActionKind.QuickFix))));
		final UUID second = registerCodeActionProvider(factory.getServer(), refactor);
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActionKinds(List.of(CodeActionKind.Refactor))));

		// Unregistering out of order must not disturb the remaining registration...
		unregister(first, TEXT_DOCUMENT_CODE_ACTION, factory.getServer());
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActionKinds(List.of(CodeActionKind.Refactor))));
		// ...and removing the last one restores the static capabilities
		unregister(second, TEXT_DOCUMENT_CODE_ACTION, factory.getServer());
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActionKinds(List.of(CodeActionKind.Source))));

		// Same again, unregistering in registration order: the older registration takes over
		final UUID third = registerCodeActionProvider(factory.getServer(), quickFix);
		final UUID fourth = registerCodeActionProvider(factory.getServer(), refactor);
		unregister(fourth, TEXT_DOCUMENT_CODE_ACTION, factory.getServer());
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActionKinds(List.of(CodeActionKind.QuickFix))));
		unregister(third, TEXT_DOCUMENT_CODE_ACTION, factory.getServer());
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActionKinds(List.of(CodeActionKind.Source))));
	}

	@Test
	public void testDocumentSelectorScopesRegistrationPerDocument(MockLanguageServerFactory factory) throws Exception {
		final var staticCapabilities = MockLanguageServer.defaultServerCapabilities();
		staticCapabilities.setCodeActionProvider(Boolean.FALSE);
		factory.withCapabilities(() -> staticCapabilities);

		final IFile matchingFile = TestUtils.createFile(project, "matching.lspt", "");
		final IFile otherFile = TestUtils.createFile(project, "other.lspt", "");
		final IDocument matchingDocument = LSPEclipseUtils.getDocument(matchingFile);
		final IDocument otherDocument = LSPEclipseUtils.getDocument(otherFile);
		assertNotNull(matchingDocument);
		assertNotNull(otherDocument);
		LanguageServers.forDocument(matchingDocument).anyMatching();
		waitForCondition(5_000, () -> !factory.getServers().isEmpty());
		LanguageServers.forDocument(otherDocument).anyMatching();
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> true));

		// register codeAction support scoped by a glob pattern to matching.lspt only
		final var filter = new DocumentFilter();
		filter.setPattern("**/matching.lspt");
		final var options = new CodeActionRegistrationOptions();
		options.setCodeActionKinds(List.of(CodeActionKind.QuickFix));
		options.setDocumentSelector(List.of(filter));
		final UUID registration = registerCodeActionProvider(factory.getServer(), options);
		try {
			// document-scoped queries see the capability only for the matching document
			assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(matchingDocument, handlesCodeActions()));
			assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(otherDocument, handlesCodeActions()));
			// document-independent queries use the union view, in which every registration applies
			assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(handlesCodeActions()));
			// the LanguageServers executor selects/skips the server per document accordingly
			assertTrue(LanguageServers.forDocument(matchingDocument)
					.withCapability(ServerCapabilities::getCodeActionProvider).anyMatching());
			assertFalse(LanguageServers.forDocument(otherDocument)
					.withCapability(ServerCapabilities::getCodeActionProvider).anyMatching());
		} finally {
			unregister(registration, TEXT_DOCUMENT_CODE_ACTION, factory.getServer());
		}
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(matchingDocument, handlesCodeActions()));
	}

	@Test
	public void testWatchedFilesRegistrationAndNotification(MockLanguageServerFactory factory) throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseExtension.lspt", "");
		// Make sure mock language server is created...
		IDocument document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServers.forDocument(document).anyMatching();
		
		waitForCondition(5_000, () -> !factory.getServers().isEmpty());
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> true));

		UUID registration = registerWatchedFiles(factory.getServer());
		try {
			MockWorkspaceService workspaceService = factory.getServer().getWorkspaceService();

			TestUtils.createFile(project, "watched.txt", "");
			TestUtils.createFile(project, "unwatched.bin", "");

			waitForCondition(5_000, () -> !workspaceService.getWatchedFilesEvents().isEmpty());

			DidChangeWatchedFilesParams params = workspaceService.getWatchedFilesEvents().get(0);
			assertFalse(params.getChanges().isEmpty());
			assertTrue(params.getChanges().stream().anyMatch(
					ev -> ev.getUri().endsWith("watched.txt") && ev.getType() == FileChangeType.Created));
			assertFalse(params.getChanges().stream()
					.anyMatch(ev -> ev.getUri().endsWith("unwatched.bin")));
		} finally {
			unregister(registration, WORKSPACE_DID_CHANGE_WATCHED_FILES, factory.getServer());
		}
	}

	@Test
	public void testWorkspaceFoldersRegistration(MockLanguageServerFactory factory) throws Exception {
		IFile testFile = TestUtils.createFile(project, "shouldUseExtension.lspt", "");
		// Make sure mock language server is created...
		IDocument document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServers.forDocument(document).anyMatching();
		
		waitForCondition(5_000, () -> !factory.getServers().isEmpty());
		
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> true));

		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(c -> hasWorkspaceFolderSupport(c)));

		UUID registration = registerWorkspaceFolders(factory.getServer());
		try {
			assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> hasWorkspaceFolderSupport(c)));
		} finally {
			unregister(registration, WORKSPACE_DID_CHANGE_FOLDERS, factory.getServer());
		}
		assertFalse(LanguageServiceAccessor.hasActiveLanguageServers(c -> hasWorkspaceFolderSupport(c)));
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> !hasWorkspaceFolderSupport(c)));
	}

	//////////////////////////////////////////////////////////////////////////////////

	private void unregister(UUID registration, String method, MockLanguageServer server) throws Exception {
		LanguageClient client = server.getRemoteProxy();
		final var unregistration = new Unregistration(registration.toString(), method);
		client.unregisterCapability(new UnregistrationParams(List.of(unregistration)))
			.get(1, TimeUnit.SECONDS);
	}

	private UUID registerWatchedFiles(MockLanguageServer server) throws Exception {
		var id = UUID.randomUUID();
		LanguageClient client = server.getRemoteProxy();
		final var registration = new Registration();
		registration.setId(id.toString());
		registration.setMethod(WORKSPACE_DID_CHANGE_WATCHED_FILES);
		// Only watch *.txt files to verify that glob-based filtering works
		final var options = new org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions();
		final var watcher = new org.eclipse.lsp4j.FileSystemWatcher(
				org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft("**/*.txt"), null);
		options.setWatchers(List.of(watcher));
		registration.setRegisterOptions(options);
		client.registerCapability(new RegistrationParams(List.of(registration))).get(1, TimeUnit.SECONDS);
		return id;
	}

	private UUID registerWorkspaceFolders(MockLanguageServer server) throws Exception {
		UUID id = UUID.randomUUID();
		LanguageClient client = server.getRemoteProxy();
		final var registration = new Registration();
		registration.setId(id.toString());
		registration.setMethod(WORKSPACE_DID_CHANGE_FOLDERS);
		client.registerCapability(new RegistrationParams(List.of(registration)))
			.get(1, TimeUnit.SECONDS);
		return id;
	}

	private UUID registerCommands(MockLanguageServer server, String... command) throws Exception {
		UUID id = UUID.randomUUID();
		LanguageClient client = server.getRemoteProxy();
		final var registration = new Registration();
		registration.setId(id.toString());
		registration.setMethod(WORKSPACE_EXECUTE_COMMAND);
		registration.setRegisterOptions(new ExecuteCommandOptions(List.of(command)));
		client.registerCapability(new RegistrationParams(List.of(registration))).get(1, TimeUnit.SECONDS);
		return id;
	}

	private void startServer(MockLanguageServerFactory factory) throws Exception {
		final var testFile = TestUtils.createFile(project, "shouldUseExtension.lspt", "");
		final var document = LSPEclipseUtils.getDocument(testFile);
		assertNotNull(document);
		LanguageServers.forDocument(document).anyMatching();
		waitForCondition(5_000, () -> !factory.getServers().isEmpty());
		assertTrue(LanguageServiceAccessor.hasActiveLanguageServers(c -> true));
	}

	private UUID registerCodeActionProvider(MockLanguageServer server, @Nullable CodeActionRegistrationOptions options)
			throws Exception {
		UUID id = UUID.randomUUID();
		LanguageClient client = server.getRemoteProxy();
		final var registration = new Registration();
		registration.setId(id.toString());
		registration.setMethod(TEXT_DOCUMENT_CODE_ACTION);
		registration.setRegisterOptions(options);
		client.registerCapability(new RegistrationParams(List.of(registration))).get(1, TimeUnit.SECONDS);
		return id;
	}

	private Predicate<ServerCapabilities> handlesCodeActions() {
		return cap -> {
			final var provider = cap.getCodeActionProvider();
			return provider != null && (provider.isRight() || Boolean.TRUE.equals(provider.getLeft()));
		};
	}

	private Predicate<ServerCapabilities> handlesCodeActions(Predicate<CodeActionOptions> options) {
		return cap -> {
			final var provider = cap.getCodeActionProvider();
			return provider != null && provider.isRight() && options.test(provider.getRight());
		};
	}

	private Predicate<ServerCapabilities> handlesCodeActionKinds(List<String> kinds) {
		return handlesCodeActions(o -> kinds.equals(o.getCodeActionKinds()));
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
