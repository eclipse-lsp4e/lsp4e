/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codelens;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.internal.AbstractLSPCodeMiningProvider;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class CodeLensProvider extends AbstractLSPCodeMiningProvider {

	@Override
	protected @Nullable CompletableFuture<List<? extends ICodeMining>> doProvideCodeMinings(IDocument document,
			TextDocumentIdentifier docId) {
		final var param = new CodeLensParams(docId);
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document)
				.withFilter(sc -> sc.getCodeLensProvider() != null);
		return executor
				.collectAll((w, ls) -> ls.getTextDocumentService().codeLens(param)
						.thenApply(codeLenses -> LanguageServers.streamSafely(codeLenses)
								.map(codeLens -> toCodeMining(document, w, codeLens)).filter(Objects::nonNull)))
				.thenApply(result -> result.stream().flatMap(s -> s).toList());
	}

	private @Nullable LSPCodeMining toCodeMining(IDocument document, LanguageServerWrapper languageServerWrapper,
			@Nullable CodeLens codeLens) {
		if (codeLens == null) {
			return null;
		}
		try {
			return new LSPCodeMining(codeLens, document, languageServerWrapper, CodeLensProvider.this);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}
	}
}
