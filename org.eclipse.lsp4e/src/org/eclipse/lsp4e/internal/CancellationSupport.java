/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Angelo ZERR (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.jsonrpc.CancelChecker;

/**
 * LSP cancellation support hosts the list of LSP requests to cancel when a
 * process is cancelled (ex: when completion is re-triggered, when hover is give
 * up, etc)
 *
 * @see <a href=
 *      "https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#cancelRequest">https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#cancelRequest</a>
 */
public class CancellationSupport implements CancelChecker {

	private final List<CompletableFuture<?>> futuresToCancel;

	private boolean cancelled;

	public CancellationSupport() {
		this.futuresToCancel = new ArrayList<>();
		this.cancelled = false;
	}

	public <T> CompletableFuture<T> execute(CompletableFuture<T> future) {
		if (castNullable(future) == null) {
			// Although 'future' is expected to be non-null per contract, we perform a null
			// check to catch potential issues in asynchronous code and eagerly log an error
			// if null is encountered.
			LanguageServerPlugin.logError(new NullPointerException("[future] must not be null")); //$NON-NLS-1$
		} else {
			this.futuresToCancel.add(future);
		}
		return future;
	}

	/**
	 * Cancel all LSP requests.
	 */
	public void cancel() {
		this.cancelled = true;
		for (CompletableFuture<?> futureToCancel : futuresToCancel) {
			if (!futureToCancel.isDone()) {
				futureToCancel.cancel(true);
			}
		}
		futuresToCancel.clear();
	}

	@Override
	public void checkCanceled() {
		// When LSP requests are called (ex : 'textDocument/completion') the LSP
		// response items are used to compose some UI item (ex : LSP CompletionItem are
		// translate to Eclipse ICompletionProposal).
		// If the cancel occurs after the call of those LSP requests, the component
		// which uses the LSP responses can call checkCanceled to stop the UI creation.
		if (cancelled) {
			throw new CancellationException();
		}
	}
}
