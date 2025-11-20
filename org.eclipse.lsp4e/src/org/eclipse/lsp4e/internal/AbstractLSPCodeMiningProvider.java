/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation.
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.AbstractCodeMiningProvider;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.TextDocumentIdentifier;

/**
 * Base class for LSP-backed code mining providers that:
 * <ul>
 * <li>compute code minings asynchronously per document using LSP requests</li>
 * <li>track at most one in-flight request per document and cancel the previous
 * one when a new computation starts</li>
 * </ul>
 */
public abstract class AbstractLSPCodeMiningProvider extends AbstractCodeMiningProvider {

	private final ConcurrentMap<IDocument, CompletableFuture<List<? extends ICodeMining>>> pendingRequests = new ConcurrentHashMap<>();

	/**
	 * Computes code minings for the given document.
	 *
	 * @return a future producing the list of code minings, or {@code null} if no
	 *         code minings are available
	 */
	protected abstract @Nullable CompletableFuture<List<? extends ICodeMining>> doProvideCodeMinings(IDocument doc,
			TextDocumentIdentifier docId);

	@Override
	public final @Nullable CompletableFuture<List<? extends ICodeMining>> provideCodeMinings(final ITextViewer viewer,
			final IProgressMonitor monitor) {
		final IDocument document = viewer.getDocument();
		if (document == null) {
			return null;
		}

		final URI docURI = LSPEclipseUtils.toUri(document);
		if (docURI == null)
			return null;

		final TextDocumentIdentifier docId = LSPEclipseUtils.toTextDocumentIdentifier(docURI);

		final var current = doProvideCodeMinings(document, docId);
		final CompletableFuture<List<? extends ICodeMining>> previous;
		if (current == null) {
			previous = pendingRequests.remove(document);
		} else {
			previous = pendingRequests.put(document, current);
		}
		if (previous != null && !previous.isDone()) {
			previous.cancel(true);
		}

		return current;
	}
}
