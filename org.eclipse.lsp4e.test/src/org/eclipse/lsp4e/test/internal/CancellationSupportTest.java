/*******************************************************************************
 * Copyright (c) 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * João Dinis Ferreira (Avaloq Group AG) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
			// both threads start together, so that sending and cancelling overlap
			final var start = new CyclicBarrier(2);
			final CompletableFuture<CompletableFuture<String>> sent = CompletableFuture.supplyAsync(() -> {
				await(start);
				return cancellation.execute(new CompletableFuture<>());
			});
			await(start);
			cancellation.cancel();
			assertTrue(sent.join().isCancelled());
		}
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		} catch (BrokenBarrierException | TimeoutException e) {
			throw new IllegalStateException(e);
		}
	}
}
