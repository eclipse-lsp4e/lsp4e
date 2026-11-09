/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Joao Dinis Ferreira - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4e.internal.CancellationSupport;
import org.junit.jupiter.api.Test;

public class CancellationSupportTest {

	@Test
	public void cancelCancelsTheRequestsSent() {
		final var cancellation = new CancellationSupport();
		final CompletableFuture<String> request = cancellation.execute(new CompletableFuture<>());
		final CompletableFuture<String> done = cancellation.execute(CompletableFuture.completedFuture("done"));

		cancellation.cancel();

		assertTrue(request.isCancelled());
		assertFalse(done.isCancelled());
		assertThrows(CancellationException.class, cancellation::checkCanceled);
	}

	@Test
	public void aRequestSentAfterTheCancellationIsCancelledRightAway() {
		// as when a request is sent from a language server's thread while the UI thread cancels
		final var cancellation = new CancellationSupport();
		cancellation.cancel();

		final CompletableFuture<String> request = cancellation.execute(new CompletableFuture<>());
		assertTrue(request.isCancelled());
	}

	@Test
	public void aRequestSentWhileAnotherThreadCancelsIsCancelled() {
		for (int i = 0; i < 1_000; i++) {
			final var cancellation = new CancellationSupport();
			final CompletableFuture<CompletableFuture<String>> sent = CompletableFuture
					.supplyAsync(() -> cancellation.execute(new CompletableFuture<>()));
			cancellation.cancel();
			assertTrue(sent.join().isCancelled());
		}
	}
}
