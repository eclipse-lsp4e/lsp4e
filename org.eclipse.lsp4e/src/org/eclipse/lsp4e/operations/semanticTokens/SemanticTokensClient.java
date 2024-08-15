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

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.lsp4j.ServerCapabilities;

public final class SemanticTokensClient {

	public static final SemanticTokensClient DEFAULT = new SemanticTokensClient();

	private SemanticTokensClient() {

	}

	public <T> CompletableFuture<Optional<T>> requestFullSemanticTokens(IDocument document, BiFunction<@Nullable SemanticTokensLegend, SemanticTokens, T> callback) {
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document)
				.withFilter(serverCapabilities -> serverCapabilities.getSemanticTokensProvider() != null
						&& LSPEclipseUtils.hasCapability(serverCapabilities.getSemanticTokensProvider().getFull()));

		URI uri = castNonNull(LSPEclipseUtils.toUri(document));
		final var semanticTokensParams = new SemanticTokensParams();
		semanticTokensParams.setTextDocument(LSPEclipseUtils.toTextDocumentIdentifier(uri));

		return executor //
			.computeFirst((w, ls) -> ls.getTextDocumentService().semanticTokensFull(semanticTokensParams)
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
