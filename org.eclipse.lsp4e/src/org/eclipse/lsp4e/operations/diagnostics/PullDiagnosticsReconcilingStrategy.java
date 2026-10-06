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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
import org.eclipse.lsp4e.internal.files.PathPatternMatcher;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

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
		updateDiagnostics();
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
		LanguageServers.forDocument(doc)//
				.withFilter(capabilities -> capabilities.getDiagnosticProvider() != null).collectAll((wrapper, ls) -> {
					final ServerCapabilities capabilities = wrapper.getServerCapabilities();
					if (capabilities == null) {
						return CompletableFuture.completedFuture(null);
					}
					final DiagnosticRegistrationOptions provider = capabilities.getDiagnosticProvider();
					if (provider == null || !matches(provider.getDocumentSelector(), wrapper, uri)) {
						return CompletableFuture.completedFuture(null);
					}
					final var param = new DocumentDiagnosticParams(id);
					param.setIdentifier(provider.getIdentifier());
					return ls.getTextDocumentService().diagnostic(param).thenAccept(report -> {
						if (report.isLeft()) {
							var client = wrapper.getLanguageClient();
							if (client != null) {
								var params = new PublishDiagnosticsParams(uri.toString(), report.getLeft().getItems());
								client.publishDiagnostics(params);
							}
						}
					});
				});
	}

	private static boolean matches(@Nullable List<DocumentFilter> documentSelector, LanguageServerWrapper wrapper,
			URI uri) {
		if (documentSelector == null) {
			return true;
		}
		return documentSelector.stream().anyMatch(filter -> matches(filter, wrapper, uri));
	}

	private static boolean matches(DocumentFilter filter, LanguageServerWrapper wrapper, URI uri) {
		final String language = filter.getLanguage();
		if (language != null && !language.equals(wrapper.getTextDocumentLanguageId(uri))) {
			return false;
		}
		final String scheme = filter.getScheme();
		if (scheme != null && !scheme.equals(uri.getScheme())) {
			return false;
		}
		final Either<String, RelativePattern> pattern = filter.getPattern();
		return pattern == null || matches(pattern, uri);
	}

	private static boolean matches(Either<String, RelativePattern> pattern, URI uri) {
		try {
			final Path path = Paths.get(uri);
			if (pattern.isLeft()) {
				return new PathPatternMatcher(pattern.getLeft(), null).matches(path);
			}
			final RelativePattern relativePattern = pattern.getRight();
			final Either<WorkspaceFolder, String> baseUri = relativePattern.getBaseUri();
			final Path basePath = Paths
					.get(URI.create(baseUri.isLeft() ? baseUri.getLeft().getUri() : baseUri.getRight()));
			return path.startsWith(basePath) && new PathPatternMatcher(relativePattern.getPattern(), basePath)
					.matches(basePath.relativize(path));
		} catch (final Exception ex) {
			// URIs that can't be resolved to a path can't match a glob pattern
			return false;
		}
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
