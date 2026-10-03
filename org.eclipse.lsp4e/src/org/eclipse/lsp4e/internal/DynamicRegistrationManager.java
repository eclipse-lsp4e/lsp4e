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
package org.eclipse.lsp4e.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.DocumentSelectorMatcher.LanguageIdResolver;
import org.eclipse.lsp4e.internal.files.FileSystemWatcherManager;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionRegistrationOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionRegistrationOptions;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.DocumentFormattingOptions;
import org.eclipse.lsp4j.DocumentFormattingRegistrationOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingOptions;
import org.eclipse.lsp4j.DocumentOnTypeFormattingRegistrationOptions;
import org.eclipse.lsp4j.DocumentRangeFormattingOptions;
import org.eclipse.lsp4j.DocumentRangeFormattingRegistrationOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandRegistrationOptions;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SelectionRangeRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentRegistrationOptions;
import org.eclipse.lsp4j.TypeHierarchyRegistrationOptions;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkspaceFoldersOptions;
import org.eclipse.lsp4j.WorkspaceServerCapabilities;
import org.eclipse.lsp4j.WorkspaceSymbolOptions;
import org.eclipse.lsp4j.WorkspaceSymbolRegistrationOptions;

/**
 * Tracks the capabilities of a language server: the immutable static capabilities from the
 * {@code initialize} result plus the live dynamic capability registrations sent by the server via
 * {@code client/registerCapability} / {@code client/unregisterCapability}.
 * <p>
 * The static capabilities are never modified. Instead, the effective capabilities returned by
 * {@link #getCapabilities()} are recomputed from the static capabilities plus all currently live
 * registrations whenever a registration is added or removed, so that unregistering never has to
 * "undo" anything and the result does not depend on the order of (un)registrations. After each
 * recomputation the {@link CapabilitiesListener} passed to the constructor is notified, so the
 * owner can apply side effects (e.g. installing workspace listeners).
 * <p>
 * This manager also owns the {@link FileSystemWatcherManager}: {@code workspace/didChangeWatchedFiles}
 * registrations are forwarded to it, and its glob matching is reachable via
 * {@link #getFileSystemWatcherManager()}.
 * <p>
 * {@code documentSelector}s are honoured per document: {@link #getCapabilities(URI)} returns the
 * effective capabilities for one document, i.e. the static capabilities plus only the registrations
 * whose selector matches that document's URI. These are cached by the set of matching registration
 * ids, of which only a handful of distinct values are expected per server. The no-argument
 * {@link #getCapabilities()} keeps its union semantics — every live registration is assumed to
 * apply — which is what document-independent (workspace-scoped) callers want. Where several live
 * registrations for one method match the same document, the most recently registered one wins.
 */
public final class DynamicRegistrationManager {

	/** Notified after a register/unregister event has recomputed the effective capabilities. */
	public interface CapabilitiesListener {
		void capabilitiesChanged(@Nullable ServerCapabilities oldCapabilities, ServerCapabilities newCapabilities);
	}

	private record DynamicRegistration(String method, @Nullable SupportedMethod supportedMethod,
			@Nullable Object options, @Nullable DocumentSelectorMatcher selector) {

		/** @return whether this registration applies to the document at the given URI */
		boolean matches(final URI uri, final LanguageIdResolver languageIdResolver) {
			// no selector: the registration applies to every document handled by the server
			return selector == null || selector.matches(uri, languageIdResolver);
		}
	}

	private final FileSystemWatcherManager fileSystemWatcherManager;
	private final LanguageIdResolver languageIdResolver;
	private final CapabilitiesListener listener;

	/** The capabilities from the {@code initialize} result; never modified after initialization. */
	private volatile @Nullable ServerCapabilities staticCapabilities;

	/** The effective capabilities: static capabilities plus all live dynamic registrations. */
	private volatile @Nullable ServerCapabilities capabilities;

	/**
	 * Live dynamic registrations by registration id, in registration order. See
	 * {@link #registerCapability(RegistrationParams)}.
	 */
	private final Map<String, DynamicRegistration> dynamicRegistrations = new LinkedHashMap<>();

	/**
	 * Effective capabilities per set of matching registration ids, for documents whose URI is not
	 * matched by every live registration's selector. Guarded by the {@link #dynamicRegistrations}
	 * monitor; cleared whenever the registrations or the static capabilities change.
	 */
	private final Map<Set<String>, ServerCapabilities> effectiveCapabilitiesBySelection = new HashMap<>();

	public DynamicRegistrationManager(final FileSystemWatcherManager fileSystemWatcherManager,
			final LanguageIdResolver languageIdResolver, final CapabilitiesListener listener) {
		this.fileSystemWatcherManager = fileSystemWatcherManager;
		this.languageIdResolver = languageIdResolver;
		this.listener = listener;
	}

	/**
	 * Stores the capabilities from the {@code initialize} result. Until the first dynamic
	 * registration arrives, the effective capabilities are exactly these. Does not notify the
	 * {@link CapabilitiesListener}.
	 */
	public void setStaticCapabilities(final ServerCapabilities capabilities) {
		this.staticCapabilities = capabilities;
		this.capabilities = capabilities;
		synchronized (dynamicRegistrations) {
			effectiveCapabilitiesBySelection.clear();
		}
	}

	/**
	 * @return the effective capabilities assuming every live dynamic registration applies (the union
	 *         view, appropriate for document-independent queries), or {@code null} before
	 *         {@link #setStaticCapabilities(ServerCapabilities)} / after {@link #clear()}
	 */
	public @Nullable ServerCapabilities getCapabilities() {
		return capabilities;
	}

	/**
	 * Returns the effective capabilities for the document at the given URI: the static capabilities
	 * plus the live dynamic registrations whose {@code documentSelector} matches the document.
	 * Results are cached by the set of matching registration ids, so at most one recomputation
	 * happens per distinct set of applicable registrations.
	 *
	 * @param uri the document URI, or {@code null} for the union view of {@link #getCapabilities()}
	 * @return the effective capabilities, or {@code null} before
	 *         {@link #setStaticCapabilities(ServerCapabilities)} / after {@link #clear()}
	 */
	public @Nullable ServerCapabilities getCapabilities(final @Nullable URI uri) {
		if (uri == null) {
			return capabilities;
		}
		final ServerCapabilities staticCaps = this.staticCapabilities;
		if (staticCaps == null) {
			return null;
		}
		synchronized (dynamicRegistrations) {
			if (dynamicRegistrations.isEmpty()) {
				return capabilities;
			}
			final var matchingIds = new HashSet<String>();
			dynamicRegistrations.forEach((id, registration) -> {
				if (registration.matches(uri, languageIdResolver)) {
					matchingIds.add(id);
				}
			});
			if (matchingIds.size() == dynamicRegistrations.size()) {
				// every registration applies: identical to the union view (the common case for
				// servers that send no document selectors)
				return capabilities;
			}
			return effectiveCapabilitiesBySelection.computeIfAbsent(Set.copyOf(matchingIds), key -> {
				final ServerCapabilities effective = JsonUtil.deepCopy(staticCaps, ServerCapabilities.class);
				// iterate the registration map, not the key, so that where several matching
				// registrations exist for one method the most recently registered one wins
				dynamicRegistrations.forEach((id, registration) -> {
					if (key.contains(id)) {
						applyRegistration(effective, registration);
					}
				});
				return effective;
			});
		}
	}

	/** @return the file-system watcher manager fed by {@code workspace/didChangeWatchedFiles} registrations */
	public FileSystemWatcherManager getFileSystemWatcherManager() {
		return fileSystemWatcherManager;
	}

	/**
	 * Forgets the static capabilities and all live registrations, including the file-system
	 * watchers. Does not notify the {@link CapabilitiesListener}.
	 */
	public void clear() {
		this.staticCapabilities = null;
		this.capabilities = null;
		synchronized (dynamicRegistrations) {
			dynamicRegistrations.clear();
			effectiveCapabilitiesBySelection.clear();
		}
		fileSystemWatcherManager.clear();
	}

	/**
	 * Applies the dynamic capability registrations sent by the server via
	 * {@code client/registerCapability} and recomputes the effective capabilities.
	 */
	public void registerCapability(final RegistrationParams params) {
		Assert.isNotNull(this.staticCapabilities,
				"Dynamic capability registration failed! Server not yet initialized?"); //$NON-NLS-1$
		synchronized (dynamicRegistrations) {
			for (final Registration reg : params.getRegistrations()) {
				final String id = reg.getId();
				final String method = reg.getMethod();
				final SupportedMethod supported = SupportedMethod.forMethod(method);

				Object options = null;
				final Class<?> optionsType = supported == null ? null : supported.optionsType();
				if (optionsType != null) {
					try {
						options = JsonUtil.registrationOptions(reg, optionsType);
					} catch (final Exception ex) {
						LanguageServerPlugin.logError("Failed to decode options of dynamic registration for '" + method + "'", ex); //$NON-NLS-1$ //$NON-NLS-2$
					}
				}
				final DocumentSelectorMatcher selector = options instanceof TextDocumentRegistrationOptions textDocumentOptions
						? DocumentSelectorMatcher.compile(textDocumentOptions.getDocumentSelector())
						: null;

				if (dynamicRegistrations.containsKey(id)) {
					LanguageServerPlugin.logWarning("A dynamic registration with id '" + id //$NON-NLS-1$
							+ "' already exists and will be replaced."); //$NON-NLS-1$
				} else if (supported != null && !supported.isAdditive() && selector == null
						&& dynamicRegistrations.values().stream()
								.anyMatch(r -> r.supportedMethod() == supported && r.selector() == null)) {
					// several registrations for one method are legitimate when they carry document
					// selectors; without selectors they all apply everywhere and the most recent wins
					LanguageServerPlugin.logWarning("Multiple dynamic registrations for '" + method //$NON-NLS-1$
							+ "' without a document selector. The most recent registration will be used for all documents."); //$NON-NLS-1$
				}

				if (supported != null) {
					supported.onRegister(id, options, fileSystemWatcherManager);
				}
				// Always record the registration, even for unsupported methods, so unregistration is clean
				dynamicRegistrations.put(id, new DynamicRegistration(method, supported, options, selector));
			}
			effectiveCapabilitiesBySelection.clear();
		}
		recomputeCapabilities();
	}

	/**
	 * Removes the dynamic capability registrations named by a {@code client/unregisterCapability}
	 * request and recomputes the effective capabilities.
	 */
	public void unregisterCapability(final UnregistrationParams params) {
		synchronized (dynamicRegistrations) {
			params.getUnregisterations().forEach(unreg -> {
				final DynamicRegistration removed = dynamicRegistrations.remove(unreg.getId());
				final SupportedMethod supported = removed == null ? null : removed.supportedMethod();
				if (supported != null) {
					supported.onUnregister(unreg.getId(), fileSystemWatcherManager);
				}
			});
			effectiveCapabilitiesBySelection.clear();
		}
		recomputeCapabilities();
	}

	/**
	 * Recomputes the effective capabilities from {@link #staticCapabilities} and the live
	 * {@link #dynamicRegistrations}, then notifies the {@link CapabilitiesListener}.
	 */
	private void recomputeCapabilities() {
		final ServerCapabilities staticCaps = this.staticCapabilities;
		if (staticCaps == null) {
			return;
		}
		final ServerCapabilities oldCapabilities = this.capabilities;
		final ServerCapabilities effective = JsonUtil.deepCopy(staticCaps, ServerCapabilities.class);
		synchronized (dynamicRegistrations) {
			// insertion order: for methods registered more than once, the most recent registration wins
			dynamicRegistrations.values().forEach(reg -> applyRegistration(effective, reg));
		}
		this.capabilities = effective;
		listener.capabilitiesChanged(oldCapabilities, effective);
	}

	private static void applyRegistration(final ServerCapabilities caps, final DynamicRegistration reg) {
		final SupportedMethod supported = reg.supportedMethod();
		if (supported != null) {
			supported.applyTo(caps, reg.options());
		}
		// unsupported methods: no capability change
	}

	/**
	 * The dynamic registration methods LSP4E supports. Each constant carries everything needed to
	 * handle its method — the LSP method name, the LSP4J type of the {@code registerOptions} payload,
	 * whether concurrent registrations are additive, how a registration transforms the effective
	 * {@link ServerCapabilities}, and any register/unregister side effects — so that supporting a new
	 * method means adding exactly one constant.
	 */
	private enum SupportedMethod {

		CODE_ACTION("textDocument/codeAction", CodeActionRegistrationOptions.class, false) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				if (options instanceof CodeActionRegistrationOptions o) {
					final var codeActionOptions = new CodeActionOptions(o.getCodeActionKinds());
					codeActionOptions.setResolveProvider(o.getResolveProvider());
					caps.setCodeActionProvider(codeActionOptions);
				} else {
					caps.setCodeActionProvider(Boolean.TRUE);
				}
			}
		},

		COMPLETION("textDocument/completion", CompletionRegistrationOptions.class, false) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				final var completionOptions = new CompletionOptions();
				if (options instanceof CompletionRegistrationOptions o) {
					completionOptions.setTriggerCharacters(o.getTriggerCharacters());
					completionOptions.setAllCommitCharacters(o.getAllCommitCharacters());
					completionOptions.setResolveProvider(o.getResolveProvider());
					completionOptions.setCompletionItem(o.getCompletionItem());
				}
				caps.setCompletionProvider(completionOptions);
			}
		},

		FORMATTING("textDocument/formatting", DocumentFormattingRegistrationOptions.class, false) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				if (options instanceof DocumentFormattingRegistrationOptions o) {
					final var formattingOptions = new DocumentFormattingOptions();
					formattingOptions.setWorkDoneProgress(o.getWorkDoneProgress());
					caps.setDocumentFormattingProvider(formattingOptions);
				} else {
					caps.setDocumentFormattingProvider(Boolean.TRUE);
				}
			}
		},

		RANGE_FORMATTING("textDocument/rangeFormatting", DocumentRangeFormattingRegistrationOptions.class, false) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				if (options instanceof DocumentRangeFormattingRegistrationOptions o) {
					final var rangeFormattingOptions = new DocumentRangeFormattingOptions();
					rangeFormattingOptions.setWorkDoneProgress(o.getWorkDoneProgress());
					rangeFormattingOptions.setRangesSupport(o.getRangesSupport());
					caps.setDocumentRangeFormattingProvider(rangeFormattingOptions);
				} else {
					caps.setDocumentRangeFormattingProvider(Boolean.TRUE);
				}
			}
		},

		ON_TYPE_FORMATTING("textDocument/onTypeFormatting", DocumentOnTypeFormattingRegistrationOptions.class, false) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				if (options instanceof DocumentOnTypeFormattingRegistrationOptions o
						&& o.getFirstTriggerCharacter() != null) {
					caps.setDocumentOnTypeFormattingProvider(new DocumentOnTypeFormattingOptions(
							o.getFirstTriggerCharacter(), o.getMoreTriggerCharacter()));
				} else {
					LanguageServerPlugin.logWarning("Ignoring dynamic registration for '" + method() //$NON-NLS-1$
							+ "' without a firstTriggerCharacter"); //$NON-NLS-1$
				}
			}
		},

		SELECTION_RANGE("textDocument/selectionRange", SelectionRangeRegistrationOptions.class, false) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				if (options instanceof SelectionRangeRegistrationOptions o) {
					caps.setSelectionRangeProvider(o);
				} else {
					caps.setSelectionRangeProvider(Boolean.TRUE);
				}
			}
		},

		TYPE_HIERARCHY("textDocument/typeHierarchy", TypeHierarchyRegistrationOptions.class, false) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				if (options instanceof TypeHierarchyRegistrationOptions o) {
					caps.setTypeHierarchyProvider(o);
				} else {
					caps.setTypeHierarchyProvider(Boolean.TRUE);
				}
			}
		},

		WORKSPACE_SYMBOL("workspace/symbol", WorkspaceSymbolRegistrationOptions.class, false) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				if (options instanceof WorkspaceSymbolOptions o) { // WorkspaceSymbolRegistrationOptions extends WorkspaceSymbolOptions
					caps.setWorkspaceSymbolProvider(o);
				} else {
					caps.setWorkspaceSymbolProvider(Boolean.TRUE);
				}
			}
		},

		EXECUTE_COMMAND("workspace/executeCommand", ExecuteCommandRegistrationOptions.class, true) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				// several executeCommand registrations are legitimate and additive
				if (options instanceof ExecuteCommandOptions o && o.getCommands() != null) {
					ExecuteCommandOptions provider = caps.getExecuteCommandProvider();
					if (provider == null) {
						provider = new ExecuteCommandOptions(new ArrayList<>());
						caps.setExecuteCommandProvider(provider);
					}
					final List<String> commands = new ArrayList<>(provider.getCommands());
					for (final String command : o.getCommands()) {
						if (!commands.contains(command)) {
							commands.add(command);
						}
					}
					provider.setCommands(commands);
				}
			}
		},

		DID_CHANGE_WATCHED_FILES("workspace/didChangeWatchedFiles", DidChangeWatchedFilesRegistrationOptions.class, //$NON-NLS-1$
				true) {
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				// watched files do not affect the effective capabilities; see onRegister/onUnregister
			}

			@Override
			void onRegister(final String id, final @Nullable Object options,
					final FileSystemWatcherManager watcherManager) {
				if (options instanceof DidChangeWatchedFilesRegistrationOptions o) {
					watcherManager.registerFileSystemWatchers(id, o.getWatchers());
				}
			}

			@Override
			void onUnregister(final String id, final FileSystemWatcherManager watcherManager) {
				watcherManager.unregisterFileSystemWatchers(id);
			}
		},

		DID_CHANGE_WORKSPACE_FOLDERS("workspace/didChangeWorkspaceFolders", null, true) { //$NON-NLS-1$
			@Override
			void applyTo(final ServerCapabilities caps, final @Nullable Object options) {
				WorkspaceServerCapabilities workspace = caps.getWorkspace();
				if (workspace == null) {
					workspace = new WorkspaceServerCapabilities();
					caps.setWorkspace(workspace);
				}
				WorkspaceFoldersOptions folders = workspace.getWorkspaceFolders();
				if (folders == null) {
					folders = new WorkspaceFoldersOptions();
					workspace.setWorkspaceFolders(folders);
				}
				folders.setSupported(Boolean.TRUE);
			}
		};

		private static final Map<String, SupportedMethod> BY_METHOD = new HashMap<>();
		static {
			for (final SupportedMethod supported : values()) {
				BY_METHOD.put(supported.method, supported);
			}
		}

		static @Nullable SupportedMethod forMethod(final String method) {
			return BY_METHOD.get(method);
		}

		private final String method;
		private final @Nullable Class<?> optionsType;
		private final boolean additive;

		SupportedMethod(final String method, final @Nullable Class<?> optionsType, final boolean additive) {
			this.method = method;
			this.optionsType = optionsType;
			this.additive = additive;
		}

		String method() {
			return method;
		}

		/** @return the LSP4J type of this method's {@code Registration.registerOptions}, if it has one */
		@Nullable
		Class<?> optionsType() {
			return optionsType;
		}

		/**
		 * @return whether several concurrent registrations for this method are legitimate and combine
		 *         additively (such methods do not carry a document selector)
		 */
		boolean isAdditive() {
			return additive;
		}

		/** Applies one registration for this method to the effective capabilities. */
		abstract void applyTo(ServerCapabilities caps, @Nullable Object options);

		/** Register-time side effect of a registration for this method; no-op by default. */
		void onRegister(final String id, final @Nullable Object options,
				final FileSystemWatcherManager watcherManager) {
		}

		/** Unregister-time side effect of a registration for this method; no-op by default. */
		void onUnregister(final String id, final FileSystemWatcherManager watcherManager) {
		}
	}
}
