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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerLifecycle;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.LSPMethods;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentDiagnosticReport;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.services.LanguageServer;

public class PullDiagnosticsReconcilingStrategy implements IReconcilingStrategy, ITextViewerLifecycle {

	private @Nullable IDocument document;
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
		this.document = document;
		// delay update on document set as server could be initializing and the registration options for textdocument/diagnostic
		// capability not yet sent. Delaying can then prevent from synchronization issues without being perceived as annoyance
		// by users at this stage as the editor is initializing in other ways as well.
		CompletableFuture.runAsync(this::updateDiagnostics, CompletableFuture.delayedExecutor(2000, TimeUnit.MILLISECONDS));
	}

	private void updateDiagnostics() {
		final var doc = this.document;
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
				.collectAll((wrapper, ls) -> pullDiagnostics(wrapper, ls, id, uri));
	}

	/**
	 * Requests the diagnostics of every identifier the server registered for the document and publishes
	 * them together, as each publication replaces the server's previous diagnostics for the document.
	 */
	private static CompletableFuture<@Nullable Void> pullDiagnostics(LanguageServerWrapper wrapper, LanguageServer ls,
			TextDocumentIdentifier id, URI uri) {
		final List<CompletableFuture<DocumentDiagnosticReport>> requests = getIdentifiers(wrapper, uri).stream()
				.map(identifier -> {
					final var param = new DocumentDiagnosticParams(id);
					param.setIdentifier(identifier);
					return ls.getTextDocumentService().diagnostic(param);
				}).toList();
		return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).thenRun(() -> {
			final var diagnostics = new ArrayList<Diagnostic>();
			boolean hasFullReport = false;
			for (final var request : requests) {
				final DocumentDiagnosticReport report = request.join();
				if (report.isLeft()) {
					hasFullReport = true;
					diagnostics.addAll(report.getLeft().getItems());
				}
			}
			final var client = wrapper.getLanguageClient();
			if (hasFullReport && client != null) {
				client.publishDiagnostics(new PublishDiagnosticsParams(uri.toString(), diagnostics));
			}
		});
	}

	/**
	 * @return the identifiers of the static and dynamic {@code textDocument/diagnostic} registrations
	 *         whose {@code documentSelector} matches the document
	 */
	private static Set<@Nullable String> getIdentifiers(LanguageServerWrapper wrapper, URI uri) {
		final var identifiers = new LinkedHashSet<@Nullable String>();
		for (final DiagnosticRegistrationOptions options : wrapper.getRegistrationOptions(uri,
				LSPMethods.TEXT_DOCUMENT_DIAGNOSTIC, DiagnosticRegistrationOptions.class)) {
			identifiers.add(options.getIdentifier());
		}
		return identifiers;
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
