/*******************************************************************************
 * Copyright (c) 2026 Cocotec Ltd and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ahmed Hussain (Cocotec Ltd) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e.test.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.DynamicRegistrationManager;
import org.eclipse.lsp4e.internal.JsonUtil;
import org.eclipse.lsp4e.internal.LSPMethods;
import org.eclipse.lsp4e.internal.files.FileSystemWatcherManager;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionRegistrationOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionRegistrationOptions;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.DocumentFormattingOptions;
import org.eclipse.lsp4j.DocumentFormattingRegistrationOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingRegistrationOptions;
import org.eclipse.lsp4j.DocumentRangeFormattingOptions;
import org.eclipse.lsp4j.DocumentRangeFormattingRegistrationOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandRegistrationOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.SelectionRangeRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TypeHierarchyRegistrationOptions;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.WorkspaceSymbolOptions;
import org.eclipse.lsp4j.WorkspaceSymbolRegistrationOptions;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DynamicRegistrationManager}, exercising it in isolation from
 * {@link org.eclipse.lsp4e.LanguageServerWrapper} (the end-to-end behaviour is covered by
 * {@code DynamicRegistrationTest}).
 */
class DynamicRegistrationManagerTest {

	private static final URI RUST_FILE = URI.create("file:///proj/foo.rs");
	private static final URI MANIFEST_FILE = URI.create("file:///proj/Cargo.toml");

	private record CapabilitiesChange(@Nullable ServerCapabilities oldCapabilities,
			ServerCapabilities newCapabilities) {
	}

	private final List<CapabilitiesChange> changes = new ArrayList<>();
	private final Map<URI, String> languageIds = new HashMap<>(Map.of(RUST_FILE, "rs", MANIFEST_FILE, "toml"));
	private final FileSystemWatcherManager watcherManager = new FileSystemWatcherManager((Path) null);
	private final DynamicRegistrationManager manager = new DynamicRegistrationManager(watcherManager,
			languageIds::get, (oldCaps, newCaps) -> changes.add(new CapabilitiesChange(oldCaps, newCaps)));

	private static ServerCapabilities staticCapabilities() {
		final var caps = new ServerCapabilities();
		caps.setCodeActionProvider(Boolean.FALSE);
		return caps;
	}

	/** Registers via a raw JSON payload, as arriving over the wire. */
	private void register(String id, String method, @Nullable Object options) {
		final var registration = new Registration(id, method);
		if (options != null) {
			registration.setRegisterOptions(JsonUtil.LSP4J_GSON.toJsonTree(options));
		}
		manager.registerCapability(new RegistrationParams(List.of(registration)));
	}

	private void unregister(String id, String method) {
		manager.unregisterCapability(new UnregistrationParams(List.of(new Unregistration(id, method))));
	}

	private static CodeActionRegistrationOptions codeActionOptions(String kind, DocumentFilter... selector) {
		final var options = new CodeActionRegistrationOptions();
		options.setCodeActionKinds(List.of(kind));
		if (selector.length > 0) {
			options.setDocumentSelector(List.of(selector));
		}
		return options;
	}

	private static DocumentFilter languageFilter(String language) {
		final var filter = new DocumentFilter();
		filter.setLanguage(language);
		return filter;
	}

	private static DocumentFilter patternFilter(String pattern) {
		final var filter = new DocumentFilter();
		filter.setPattern(pattern);
		return filter;
	}

	private static DocumentFilter schemeFilter(String scheme) {
		final var filter = new DocumentFilter();
		filter.setScheme(scheme);
		return filter;
	}

	private List<String> effectiveCodeActionKinds() {
		return effectiveCodeActionKinds(null);
	}

	private List<String> effectiveCodeActionKinds(@Nullable URI uri) {
		final ServerCapabilities caps = manager.getCapabilities(uri);
		assertNotNull(caps);
		final Either<Boolean, CodeActionOptions> provider = caps.getCodeActionProvider();
		assertNotNull(provider);
		assertTrue(provider.isRight(), "expected CodeActionOptions but was: " + provider);
		return provider.getRight().getCodeActionKinds();
	}

	private void assertCodeActionsUnsupported(@Nullable URI uri) {
		final ServerCapabilities caps = manager.getCapabilities(uri);
		assertNotNull(caps);
		assertEquals(Boolean.FALSE, caps.getCodeActionProvider().getLeft());
	}

	@Test
	void nullBeforeInitialization() {
		assertNull(manager.getCapabilities());
	}

	@Test
	void staticCapabilitiesAreEffectiveUntilFirstRegistration() {
		final ServerCapabilities staticCaps = staticCapabilities();
		manager.registerInitializationCapabilities(staticCaps);
		assertSame(staticCaps, manager.getCapabilities());
		assertTrue(changes.isEmpty(), "registerInitializationCapabilities must not notify the listener");
	}

	@Test
	void registerDecodesJsonPayloadAndNotifiesListener() {
		manager.registerInitializationCapabilities(staticCapabilities());

		final var options = codeActionOptions(CodeActionKind.QuickFix);
		options.setResolveProvider(Boolean.TRUE);
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, options);

		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds());
		final ServerCapabilities caps = manager.getCapabilities();
		assertNotNull(caps);
		assertEquals(Boolean.TRUE, caps.getCodeActionProvider().getRight().getResolveProvider());

		assertEquals(1, changes.size());
		final CapabilitiesChange change = changes.get(0);
		final ServerCapabilities oldCaps = change.oldCapabilities();
		assertNotNull(oldCaps);
		assertEquals(Boolean.FALSE, oldCaps.getCodeActionProvider().getLeft());
		assertSame(caps, change.newCapabilities());
	}

	@Test
	void absentRegisterOptionsFallBackToTrue() {
		manager.registerInitializationCapabilities(staticCapabilities());

		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, null);
		ServerCapabilities caps = manager.getCapabilities();
		assertNotNull(caps);
		assertEquals(Boolean.TRUE, caps.getCodeActionProvider().getLeft());

		unregister("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION);
		caps = manager.getCapabilities();
		assertNotNull(caps);
		assertEquals(Boolean.FALSE, caps.getCodeActionProvider().getLeft());
	}

	@Test
	void lastRegistrationWins() {
		manager.registerInitializationCapabilities(staticCapabilities());

		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix));
		register("r2", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.Refactor));
		assertEquals(List.of(CodeActionKind.Refactor), effectiveCodeActionKinds());
	}

	@Test
	void unregistrationOrderDoesNotMatter() {
		manager.registerInitializationCapabilities(staticCapabilities());

		// unregister the winner: falls back to the older live registration
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix));
		register("r2", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.Refactor));
		unregister("r2", LSPMethods.TEXT_DOCUMENT_CODE_ACTION);
		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds());
		unregister("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION);

		// unregister the loser first: the winner stays in effect
		register("r3", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix));
		register("r4", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.Refactor));
		unregister("r3", LSPMethods.TEXT_DOCUMENT_CODE_ACTION);
		assertEquals(List.of(CodeActionKind.Refactor), effectiveCodeActionKinds());
		unregister("r4", LSPMethods.TEXT_DOCUMENT_CODE_ACTION);

		// all live registrations gone: back to the static capabilities
		final ServerCapabilities caps = manager.getCapabilities();
		assertNotNull(caps);
		assertEquals(Boolean.FALSE, caps.getCodeActionProvider().getLeft());
	}

	@Test
	void watchedFilesRegistrationsAreForwardedToTheWatcherManager() {
		manager.registerInitializationCapabilities(staticCapabilities());
		assertFalse(watcherManager.hasFilePatterns());

		register("w1", LSPMethods.WORKSPACE_DID_CHANGE_WATCHED_FILES, new DidChangeWatchedFilesRegistrationOptions(
				List.of(new FileSystemWatcher(Either.forLeft("**/*.txt")))));
		assertTrue(watcherManager.hasFilePatterns());
		assertEquals(1, changes.size(), "recompute must still notify the listener");

		unregister("w1", LSPMethods.WORKSPACE_DID_CHANGE_WATCHED_FILES);
		assertFalse(watcherManager.hasFilePatterns());
		assertEquals(2, changes.size());
	}

	@Test
	void reRegisteringWatchersUnderTheSameIdNeedsOnlyOneUnregistration() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("w1", LSPMethods.WORKSPACE_DID_CHANGE_WATCHED_FILES, new DidChangeWatchedFilesRegistrationOptions(
				List.of(new FileSystemWatcher(Either.forLeft("**/*.txt")))));
		register("w1", LSPMethods.WORKSPACE_DID_CHANGE_WATCHED_FILES, new DidChangeWatchedFilesRegistrationOptions(
				List.of(new FileSystemWatcher(Either.forLeft("**/*.md")))));
		assertTrue(watcherManager.hasFilePatterns());

		unregister("w1", LSPMethods.WORKSPACE_DID_CHANGE_WATCHED_FILES);
		assertFalse(watcherManager.hasFilePatterns());
	}

	@Test
	void clearResetsEverythingWithoutNotifying() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("w1", LSPMethods.WORKSPACE_DID_CHANGE_WATCHED_FILES, new DidChangeWatchedFilesRegistrationOptions(
				List.of(new FileSystemWatcher(Either.forLeft("**/*.txt")))));
		changes.clear();

		manager.clear();
		assertNull(manager.getCapabilities());
		assertFalse(watcherManager.hasFilePatterns());
		assertTrue(changes.isEmpty(), "clear() must not notify the listener");
	}

	@Test
	void languageSelectorScopesRegistrationPerDocument() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix, languageFilter("rs")));

		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds(RUST_FILE));
		assertCodeActionsUnsupported(MANIFEST_FILE);
		// the union view assumes every registration applies
		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds(null));
	}

	@Test
	void patternSelectorMatchesAbsolutePath() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix, patternFilter("**/Cargo.toml")));

		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds(MANIFEST_FILE));
		assertCodeActionsUnsupported(RUST_FILE);
	}

	@Test
	void relativePatternSelectorMatchesRelativeToItsBaseUri() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var relativePattern = new RelativePattern();
		relativePattern.setBaseUri(Either.forRight("file:///proj")); //$NON-NLS-1$
		relativePattern.setPattern("*.rs"); //$NON-NLS-1$
		final var filter = new DocumentFilter();
		filter.setPattern(relativePattern);
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix, filter));

		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds(RUST_FILE));
		assertCodeActionsUnsupported(URI.create("file:///other/foo.rs"));
	}

	@Test
	void schemeSelectorMatchesTheUriScheme() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix, schemeFilter("untitled")));

		assertCodeActionsUnsupported(RUST_FILE);
		assertEquals(List.of(CodeActionKind.QuickFix),
				effectiveCodeActionKinds(URI.create("untitled:Untitled-1.rs")));
	}

	@Test
	void filterPropertiesAreConjunctive() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final DocumentFilter filter = languageFilter("rs");
		filter.setScheme("untitled");
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix, filter));

		// the language matches but the scheme does not: per spec the set properties are ANDed
		assertCodeActionsUnsupported(RUST_FILE);
	}

	@Test
	void selectorFiltersAreDisjunctive() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION,
				codeActionOptions(CodeActionKind.QuickFix, languageFilter("rs"), patternFilter("**/Cargo.toml")));

		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds(RUST_FILE));
		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds(MANIFEST_FILE));
		assertCodeActionsUnsupported(URI.create("file:///proj/readme.md"));
	}

	@Test
	void absentSelectorMatchesEveryDocument() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix));

		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds(RUST_FILE));
		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds(MANIFEST_FILE));
		// all registrations apply: identical to the union view
		assertSame(manager.getCapabilities(), manager.getCapabilities(RUST_FILE));
	}

	@Test
	void emptySelectorMatchesNoDocument() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var options = codeActionOptions(CodeActionKind.QuickFix);
		options.setDocumentSelector(List.of());
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, options);

		assertCodeActionsUnsupported(RUST_FILE);
		assertCodeActionsUnsupported(MANIFEST_FILE);
	}

	@Test
	void effectiveCapabilitiesAreCachedPerMatchingRegistrationSet() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix, languageFilter("rs")));

		languageIds.put(URI.create("file:///proj/bar.rs"), "rs");
		final ServerCapabilities forFoo = manager.getCapabilities(RUST_FILE);
		final ServerCapabilities forBar = manager.getCapabilities(URI.create("file:///proj/bar.rs"));
		final ServerCapabilities forManifest = manager.getCapabilities(MANIFEST_FILE);
		// same set of matching registrations: the same cached instance, not a recomputation
		assertSame(forFoo, forBar);
		assertSame(forFoo, manager.getCapabilities(RUST_FILE));
		assertNotSame(forFoo, forManifest);
	}

	@Test
	void overlappingSelectorsResolveToTheMostRecentRegistration() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.QuickFix, languageFilter("rs")));
		register("r2", LSPMethods.TEXT_DOCUMENT_CODE_ACTION, codeActionOptions(CodeActionKind.Refactor, patternFilter("**/*.rs")));

		// both registrations match: the most recently registered one wins
		assertEquals(List.of(CodeActionKind.Refactor), effectiveCodeActionKinds(RUST_FILE));

		// the per-set cache is invalidated by unregistration
		unregister("r2", LSPMethods.TEXT_DOCUMENT_CODE_ACTION);
		assertEquals(List.of(CodeActionKind.QuickFix), effectiveCodeActionKinds(RUST_FILE));
		unregister("r1", LSPMethods.TEXT_DOCUMENT_CODE_ACTION);
		assertCodeActionsUnsupported(RUST_FILE);
	}

	// One decode-and-apply test per remaining supported method: not exhaustive, but each one proves
	// the wire payload is unserialized into the method's registration options type and applied to
	// the effective capabilities.

	private ServerCapabilities effectiveCapabilities() {
		final ServerCapabilities caps = manager.getCapabilities();
		assertNotNull(caps);
		return caps;
	}

	@Test
	void completionRegistrationIsDecodedAndApplied() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var options = new CompletionRegistrationOptions();
		options.setTriggerCharacters(List.of("."));
		options.setAllCommitCharacters(List.of(";"));
		options.setResolveProvider(Boolean.TRUE);
		register("r1", LSPMethods.TEXT_DOCUMENT_COMPLETION, options);

		final CompletionOptions provider = effectiveCapabilities().getCompletionProvider();
		assertNotNull(provider);
		assertEquals(List.of("."), provider.getTriggerCharacters());
		assertEquals(List.of(";"), provider.getAllCommitCharacters());
		assertEquals(Boolean.TRUE, provider.getResolveProvider());

		unregister("r1", LSPMethods.TEXT_DOCUMENT_COMPLETION);
		assertNull(effectiveCapabilities().getCompletionProvider());
	}

	@Test
	void formattingRegistrationIsDecodedAndApplied() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var options = new DocumentFormattingRegistrationOptions();
		options.setWorkDoneProgress(Boolean.TRUE);
		register("r1", LSPMethods.TEXT_DOCUMENT_FORMATTING, options);

		final Either<Boolean, DocumentFormattingOptions> provider = effectiveCapabilities()
				.getDocumentFormattingProvider();
		assertNotNull(provider);
		assertTrue(provider.isRight());
		assertEquals(Boolean.TRUE, provider.getRight().getWorkDoneProgress());

		unregister("r1", LSPMethods.TEXT_DOCUMENT_FORMATTING);
		assertNull(effectiveCapabilities().getDocumentFormattingProvider());
	}

	@Test
	void rangeFormattingRegistrationPreservesRangesSupport() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var options = new DocumentRangeFormattingRegistrationOptions();
		options.setRangesSupport(Boolean.TRUE);
		register("r1", LSPMethods.TEXT_DOCUMENT_RANGE_FORMATTING, options);

		final Either<Boolean, DocumentRangeFormattingOptions> provider = effectiveCapabilities()
				.getDocumentRangeFormattingProvider();
		assertNotNull(provider);
		assertTrue(provider.isRight());
		assertEquals(Boolean.TRUE, provider.getRight().getRangesSupport());
	}

	@Test
	void onTypeFormattingRegistrationIsDecodedAndApplied() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var options = new DocumentOnTypeFormattingRegistrationOptions();
		options.setFirstTriggerCharacter("}");
		options.setMoreTriggerCharacter(List.of(";"));
		register("r1", LSPMethods.TEXT_DOCUMENT_ON_TYPE_FORMATTING, options);

		final DocumentOnTypeFormattingOptions provider = effectiveCapabilities().getDocumentOnTypeFormattingProvider();
		assertNotNull(provider);
		assertEquals("}", provider.getFirstTriggerCharacter());
		assertEquals(List.of(";"), provider.getMoreTriggerCharacter());
	}

	@Test
	void onTypeFormattingRegistrationWithoutFirstTriggerCharacterIsIgnored() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("r1", LSPMethods.TEXT_DOCUMENT_ON_TYPE_FORMATTING, new DocumentOnTypeFormattingRegistrationOptions());
		assertNull(effectiveCapabilities().getDocumentOnTypeFormattingProvider());
	}

	@Test
	void selectionRangeAndTypeHierarchyRegistrationsAreDecodedAndApplied() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var selectionRangeOptions = new SelectionRangeRegistrationOptions();
		selectionRangeOptions.setWorkDoneProgress(Boolean.TRUE);
		register("r1", LSPMethods.TEXT_DOCUMENT_SELECTION_RANGE, selectionRangeOptions);
		final var typeHierarchyOptions = new TypeHierarchyRegistrationOptions();
		typeHierarchyOptions.setWorkDoneProgress(Boolean.TRUE);
		register("r2", LSPMethods.TEXT_DOCUMENT_TYPE_HIERARCHY, typeHierarchyOptions);

		final ServerCapabilities caps = effectiveCapabilities();
		assertNotNull(caps.getSelectionRangeProvider());
		assertTrue(caps.getSelectionRangeProvider().isRight());
		assertEquals(Boolean.TRUE, caps.getSelectionRangeProvider().getRight().getWorkDoneProgress());
		assertNotNull(caps.getTypeHierarchyProvider());
		assertTrue(caps.getTypeHierarchyProvider().isRight());
		assertEquals(Boolean.TRUE, caps.getTypeHierarchyProvider().getRight().getWorkDoneProgress());
	}

	@Test
	void workspaceSymbolRegistrationIsDecodedAndApplied() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var options = new WorkspaceSymbolRegistrationOptions();
		options.setResolveProvider(Boolean.TRUE);
		register("r1", LSPMethods.WORKSPACE_SYMBOL, options);

		final Either<Boolean, WorkspaceSymbolOptions> provider = effectiveCapabilities().getWorkspaceSymbolProvider();
		assertNotNull(provider);
		assertTrue(provider.isRight());
		assertEquals(Boolean.TRUE, provider.getRight().getResolveProvider());
	}

	@Test
	void executeCommandRegistrationsAreDecodedAndAdditive() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var first = new ExecuteCommandRegistrationOptions();
		first.setCommands(List.of("a", "b"));
		register("r1", LSPMethods.WORKSPACE_EXECUTE_COMMAND, first);
		final var second = new ExecuteCommandRegistrationOptions();
		second.setCommands(List.of("b", "c"));
		register("r2", LSPMethods.WORKSPACE_EXECUTE_COMMAND, second);

		ExecuteCommandOptions provider = effectiveCapabilities().getExecuteCommandProvider();
		assertNotNull(provider);
		assertEquals(List.of("a", "b", "c"), provider.getCommands());

		unregister("r1", LSPMethods.WORKSPACE_EXECUTE_COMMAND);
		provider = effectiveCapabilities().getExecuteCommandProvider();
		assertNotNull(provider);
		assertEquals(List.of("b", "c"), provider.getCommands());
	}

	@Test
	void workspaceFoldersRegistrationEnablesSupport() {
		manager.registerInitializationCapabilities(staticCapabilities());
		register("r1", LSPMethods.WORKSPACE_DID_CHANGE_WORKSPACE_FOLDERS, null);

		final WorkspaceServerCapabilities workspace = effectiveCapabilities().getWorkspace();
		assertNotNull(workspace);
		assertNotNull(workspace.getWorkspaceFolders());
		assertEquals(Boolean.TRUE, workspace.getWorkspaceFolders().getSupported());

		unregister("r1", LSPMethods.WORKSPACE_DID_CHANGE_WORKSPACE_FOLDERS);
		assertNull(effectiveCapabilities().getWorkspace());
	}

	@Test
	void diagnosticRegistrationEnablesPullDiagnosticsPerDocument() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var options = new DiagnosticRegistrationOptions();
		options.setIdentifier("rust");
		options.setDocumentSelector(List.of(languageFilter("rs")));
		register("r1", LSPMethods.TEXT_DOCUMENT_DIAGNOSTIC, options);

		final ServerCapabilities rustCapabilities = manager.getCapabilities(RUST_FILE);
		assertNotNull(rustCapabilities);
		final DiagnosticRegistrationOptions provider = rustCapabilities.getDiagnosticProvider();
		assertNotNull(provider);
		assertEquals("rust", provider.getIdentifier());
		final ServerCapabilities manifestCapabilities = manager.getCapabilities(MANIFEST_FILE);
		assertNotNull(manifestCapabilities);
		assertNull(manifestCapabilities.getDiagnosticProvider());

		unregister("r1", LSPMethods.TEXT_DOCUMENT_DIAGNOSTIC);
		assertNull(effectiveCapabilities().getDiagnosticProvider());
	}

	@Test
	void allDiagnosticRegistrationsMatchingADocumentAreAvailable() {
		final ServerCapabilities staticCaps = staticCapabilities();
		final var staticOptions = new DiagnosticRegistrationOptions();
		staticOptions.setIdentifier("static");
		staticOptions.setDocumentSelector(List.of(languageFilter("toml")));
		staticCaps.setDiagnosticProvider(staticOptions);
		manager.registerInitializationCapabilities(staticCaps);

		final var syntax = new DiagnosticRegistrationOptions();
		syntax.setIdentifier("syntax");
		register("r1", LSPMethods.TEXT_DOCUMENT_DIAGNOSTIC, syntax);
		final var semantic = new DiagnosticRegistrationOptions();
		semantic.setIdentifier("semantic");
		semantic.setDocumentSelector(List.of(languageFilter("rs")));
		register("r2", LSPMethods.TEXT_DOCUMENT_DIAGNOSTIC, semantic);

		assertEquals(List.of("syntax", "semantic"), diagnosticIdentifiers(RUST_FILE));
		assertEquals(List.of("static", "syntax"), diagnosticIdentifiers(MANIFEST_FILE));
		assertEquals(List.of("static", "syntax", "semantic"), diagnosticIdentifiers(null));

		unregister("r1", LSPMethods.TEXT_DOCUMENT_DIAGNOSTIC);
		assertEquals(List.of("semantic"), diagnosticIdentifiers(RUST_FILE));
	}

	private List<@Nullable String> diagnosticIdentifiers(@Nullable URI uri) {
		return manager.getRegistrationOptions(uri, LSPMethods.TEXT_DOCUMENT_DIAGNOSTIC, DiagnosticRegistrationOptions.class)
				.stream().map(DiagnosticRegistrationOptions::getIdentifier).toList();
	}

	@Test
	void registeringAnAlreadyRegisteredCommandLogsAWarning() {
		manager.registerInitializationCapabilities(staticCapabilities());
		final var warnings = new ArrayList<String>();
		final ILogListener listener = (status, plugin) -> {
			if (status.getSeverity() == IStatus.WARNING && status.getMessage().startsWith("Command already registered")) {
				warnings.add(status.getMessage());
			}
		};
		final ILog log = LanguageServerPlugin.getDefault().getLog();
		log.addLogListener(listener);
		try {
			final var first = new ExecuteCommandRegistrationOptions();
			first.setCommands(List.of("a", "b"));
			register("r1", LSPMethods.WORKSPACE_EXECUTE_COMMAND, first);
			final var second = new ExecuteCommandRegistrationOptions();
			second.setCommands(List.of("b", "c"));
			register("r2", LSPMethods.WORKSPACE_EXECUTE_COMMAND, second);
			// recomputations must not repeat the warning
			unregister("r1", LSPMethods.WORKSPACE_EXECUTE_COMMAND);
		} finally {
			log.removeLogListener(listener);
		}
		assertEquals(List.of("Command already registered 'b'"), warnings);
	}
}
