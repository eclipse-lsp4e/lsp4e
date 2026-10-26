/*******************************************************************************
 * Copyright (c) 2026 🌘🧑‍💻⚗️ (EclipseDevLab) and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria - 🌘🧑‍💻⚗️ (EclipseDevLab) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.diagnostics;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerLifecycle;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.CancellationUtil;
import org.eclipse.lsp4e.internal.LSPMethods;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;

public class PullDiagnosticsReconcilingStrategy implements IReconcilingStrategy, ITextViewerLifecycle {

	/**
	 * A document with the last full report per server and identifier, reused while the request for an
	 * identifier fails. Replaced as a whole on {@link #setDocument(IDocument)}, so responses to requests for
	 * a previous document cannot leak into the cache of the current one. The cache is guarded by its own
	 * monitor.
	 */
	private record DocumentState(@Nullable IDocument document,
			Map<LanguageServerWrapper, Map<@Nullable String, List<Diagnostic>>> lastDiagnostics) {
	}

	private volatile DocumentState state = new DocumentState(null, new HashMap<>());
	private boolean enabled = true;

	@Override
	public void install(@Nullable ITextViewer textViewer) {
		enabled = true;
		updateDiagnostics();
	}

	@Override
	public void uninstall() {
		enabled = false;
	}

	@Override
	public void setDocument(@Nullable IDocument document) {
		this.state = new DocumentState(document, new HashMap<>());
		// delay update on document set as server could be initializing and the registration options for textdocument/diagnostic
		// capability not yet sent. Delaying can then prevent from synchronization issues without being perceived as annoyance
		// by users at this stage as the editor is initializing in other ways as well.
		CompletableFuture.runAsync(this::updateDiagnostics, CompletableFuture.delayedExecutor(2000, TimeUnit.MILLISECONDS));
	}

	private void updateDiagnostics() {
		final DocumentState currentState = this.state;
		final var doc = currentState.document();
		if (doc == null || !enabled) {
			return;
		}
		var id = LSPEclipseUtils.toTextDocumentIdentifier(doc);
		if (id == null) {
			return;
		}
		final URI uri = LSPEclipseUtils.toUri(doc);
		if (uri == null) {
			return;
		}
		// LanguageServers.forDocument evaluates the filter against the document's effective capabilities,
		// so servers whose diagnostic registrations' documentSelectors don't match are skipped
		LanguageServers.forDocument(doc) //
				.withFilter(capabilities -> capabilities.getDiagnosticProvider() != null) //
				.collectAll((wrapper, ls) -> pullDiagnostics(wrapper, ls, id, uri, currentState.lastDiagnostics()));
	}

	/**
	 * Requests the diagnostics of every identifier the server registered for the document and publishes
	 * them together, as each publication replaces the server's previous diagnostics for the document.
	 * An identifier whose request fails contributes its last full report, so that neither the other
	 * identifiers' new diagnostics nor its own previous ones are lost.
	 */
	private static CompletableFuture<@Nullable Void> pullDiagnostics(LanguageServerWrapper wrapper, LanguageServer ls,
			TextDocumentIdentifier id, URI uri,
			Map<LanguageServerWrapper, Map<@Nullable String, List<Diagnostic>>> lastDiagnostics) {
		final var requests = new HashMap<@Nullable String, CompletableFuture<Optional<List<Diagnostic>>>>();
		for (final String identifier : getIdentifiers(wrapper, uri)) {
			final var param = new DocumentDiagnosticParams(id);
			param.setIdentifier(identifier);
			requests.put(identifier, ls.getTextDocumentService().diagnostic(param) //
					.thenApply(report -> report.map(d -> Optional.of(d.getItems()),
							r -> Optional.<List<Diagnostic>>empty()))
					.exceptionally(ex -> {
						if (!CancellationUtil.isRequestCancelledException(ex)) {
							LanguageServerPlugin.logError(ex);
						}
						return Optional.empty();
					}));
		}
		return CompletableFuture.allOf(requests.values().toArray(CompletableFuture[]::new)).thenRun(() -> {
			synchronized (lastDiagnostics) {
				final Map<@Nullable String, List<Diagnostic>> last = lastDiagnostics.computeIfAbsent(wrapper,
						w -> new HashMap<>());
				last.keySet().retainAll(requests.keySet());
				AtomicBoolean hasFullReport = new AtomicBoolean();
				for (final var request : requests.entrySet()) {
					request.getValue().join().ifPresent(item -> {
						hasFullReport.set(true);
						last.put(request.getKey(), item);
					});
				}
				final var client = wrapper.getLanguageClient();
				if (hasFullReport.get() && client != null) {
					final var diagnostics = last.values().stream().flatMap(List::stream).toList();
					client.publishDiagnostics(new PublishDiagnosticsParams(uri.toString(), diagnostics));
				}
			}
		});
	}

	/**
	 * @return the identifiers of the static and dynamic {@code textDocument/diagnostic} registrations
	 *         whose {@code documentSelector} matches the document
	 */
	private static Set<@Nullable String> getIdentifiers(LanguageServerWrapper wrapper, URI uri) {
		return wrapper
				.getRegistrationOptions(uri, LSPMethods.TEXT_DOCUMENT_DIAGNOSTIC, DiagnosticRegistrationOptions.class)
				.stream().map(DiagnosticRegistrationOptions::getIdentifier).collect(Collectors.toSet());
	}

	@Override
	public void reconcile(@Nullable DirtyRegion dirtyRegion, @Nullable IRegion subRegion) {
		updateDiagnostics();
	}

	@Override
	public void reconcile(@Nullable IRegion partition) {
		updateDiagnostics();
	}

}
