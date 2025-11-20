/*******************************************************************************
 * Copyright (c) 2022-23 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlayhint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.AbstractLSPCodeMiningProvider;
import org.eclipse.lsp4e.internal.CancellationUtil;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;

public class InlayHintProvider extends AbstractLSPCodeMiningProvider {

	@Override
	protected @Nullable CompletableFuture<List<? extends ICodeMining>> doProvideCodeMinings(IDocument document,
			TextDocumentIdentifier docId) {
		// Eclipse seems to request minings only when the document is loaded (or changed), rather than
		// whenever the viewport [displayed area] changes, so request minings for the whole document in one go.
		var end = new Position(0,0);
		try {
			end = LSPEclipseUtils.toPosition(document.getLength(), document);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logWarning("Unable to compute end of document", e); //$NON-NLS-1$
		}
		final var viewPortRange = new Range(new Position(0,0), end);
		final var param = new InlayHintParams(docId, viewPortRange);
		List<LSPLineContentCodeMining> inlayHintResults = Collections.synchronizedList(new ArrayList<>());
		return LanguageServers.forDocument(document).withCapability(ServerCapabilities::getInlayHintProvider)
				.collectAll((w, ls) -> ls.getTextDocumentService() //
				.inlayHint(param).exceptionally((ex -> {
					if (!(ex instanceof CancellationException || CancellationUtil.isRequestCancelledException(ex))) {
						LanguageServerPlugin.logError(ex);
					}
					return Collections.emptyList();
				})) //
				.thenAcceptAsync(inlayHints -> {
					// textDocument/inlayHint may return null
					if (inlayHints != null) {
						inlayHints.stream().filter(Objects::nonNull)
								.map(inlayHint -> toCodeMining(document, w, inlayHint))
								.filter(Objects::nonNull)
								.forEach(inlayHintResults::add);
					}
				})).thenApplyAsync(theVoid -> inlayHintResults);
	}

	private @Nullable LSPLineContentCodeMining toCodeMining(IDocument document, LanguageServerWrapper languageServerWrapper,
			InlayHint inlayHint) {
		try {
			return new LSPLineContentCodeMining(inlayHint, document, languageServerWrapper, InlayHintProvider.this);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}
	}
}
