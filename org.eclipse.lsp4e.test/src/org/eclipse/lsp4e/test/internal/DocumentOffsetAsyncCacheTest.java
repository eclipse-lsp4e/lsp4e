/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.test.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.text.Document;
import org.eclipse.lsp4e.internal.DocumentOffsetAsyncCache;
import org.junit.jupiter.api.Test;

public class DocumentOffsetAsyncCacheTest {

	private final DocumentOffsetAsyncCache<String> cache = new DocumentOffsetAsyncCache<>(Duration.ofMinutes(1));

	@Test
	public void testRunningRequestIsShared() {
		final var doc = new Document();
		doc.set("foo");
		final var request = new CompletableFuture<String>();

		assertSame(request, cache.computeIfAbsent(doc, 0, () -> request));
		assertSame(request, cache.computeIfAbsent(doc, 0, CompletableFuture::new));

		request.complete("result");
		assertEquals("result", cache.getNow(doc, 0));
		assertEquals("result", cache.computeIfAbsent(doc, 0, CompletableFuture::new).join());
	}

	@Test
	public void testRunningRequestIsNotReusedAfterDocumentChange() throws Exception {
		final var doc = new Document();
		doc.set("foo");
		final var oldRequest = new CompletableFuture<String>();
		cache.computeIfAbsent(doc, 0, () -> oldRequest);

		doc.replace(0, 0, " ");
		final var newRequest = new CompletableFuture<String>();
		assertSame(newRequest, cache.computeIfAbsent(doc, 0, () -> newRequest));

		oldRequest.complete("old");
		assertNull(cache.getNow(doc, 0));
		assertSame(newRequest, cache.computeIfAbsent(doc, 0, CompletableFuture::new));

		newRequest.complete("new");
		assertEquals("new", cache.getNow(doc, 0));
	}

	@Test
	public void testAlreadyCompletedRequestIsCached() {
		final var doc = new Document();
		doc.set("foo");

		assertEquals("done", cache.computeIfAbsent(doc, 0, () -> CompletableFuture.completedFuture("done")).join());
		assertEquals("done", cache.getNow(doc, 0));
	}
}
