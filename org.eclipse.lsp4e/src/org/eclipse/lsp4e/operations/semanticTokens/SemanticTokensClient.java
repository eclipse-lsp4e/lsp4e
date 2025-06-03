/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;

public final class SemanticTokensClient {

	public static final SemanticTokensClient DEFAULT = new SemanticTokensClient();

	private SemanticTokensClient() {

	}

	public <T> CompletableFuture<Optional<T>> requestFullSemanticTokens(IDocument document,
			BiFunction<@Nullable SemanticTokensLegend, SemanticTokens, T> callback) {
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			return CompletableFuture.completedFuture(Optional.empty());
		}

		return LanguageServers.forDocument(document)
				.withFilter(serverCapabilities -> serverCapabilities.getSemanticTokensProvider() != null
						&& LSPEclipseUtils.hasCapability(serverCapabilities.getSemanticTokensProvider().getFull())) //
				.computeFirst((w, ls) -> ls.getTextDocumentService()
						.semanticTokensFull(new SemanticTokensParams(LSPEclipseUtils.toTextDocumentIdentifier(uri)))
						.thenApply(semanticTokens -> callback.apply(getSemanticTokensLegend(w), semanticTokens)));
	}

	// public for testing
	public @Nullable SemanticTokensLegend getSemanticTokensLegend(final LanguageServerWrapper wrapper) {
		ServerCapabilities serverCapabilities = wrapper.getServerCapabilities();
		if (serverCapabilities != null) {
			SemanticTokensWithRegistrationOptions semanticTokensProvider = serverCapabilities
					.getSemanticTokensProvider();
			if (semanticTokensProvider != null) {
				return semanticTokensProvider.getLegend();
			}
		}
		return null;
	}

}
