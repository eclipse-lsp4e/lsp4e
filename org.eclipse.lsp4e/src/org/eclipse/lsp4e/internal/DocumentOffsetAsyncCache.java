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

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;

/**
 * Generic, per-document+offset cache for asynchronous results that avoids
 * starting the same work twice by sharing a single running task.
 *
 * <p>
 * Features:
 * <li>Weakly keys by {@link IDocument} to avoid memory leaks.
 * <li>Per-document concurrent maps for thread-safe access from UI and
 * background.
 * <li>Eviction: TTL-based using {@link System#nanoTime()} and document-change
 * invalidation when a stable modification stamp is available.
 * <li>In-flight de-duplication: only one running task per document+offset;
 * a task started for an older document stamp is not handed out anymore.
 * <li>Stale-result protection: if the document changes while a value is being
 * computed, the result is delivered to callers but is not cached.
 */
public final class DocumentOffsetAsyncCache<V> {

	private record Entry<V>(V value, long createdNanos, long docModStamp) {
		boolean stale(final long ttlNanos, final long currentDocStamp) {
			return System.nanoTime() - createdNanos > ttlNanos //
					// Invalidate when we can confidently detect a document change
					|| (docModStamp != IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP
							&& currentDocStamp != IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP
							&& docModStamp != currentDocStamp);
		}
	}

	private record InFlight<V>(CompletableFuture<V> future, long docModStamp) {
		boolean stale(final long currentDocStamp) {
			return docModStamp != IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP
					&& currentDocStamp != IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP
					&& docModStamp != currentDocStamp;
		}
	}

	private final Map<IDocument, ConcurrentMap<Integer, Entry<V>>> cache = Collections
			.synchronizedMap(new WeakHashMap<>());
	private final Map<IDocument, ConcurrentMap<Integer, InFlight<V>>> inFlight = Collections
			.synchronizedMap(new WeakHashMap<>());

	private final long ttlNanos;

	public DocumentOffsetAsyncCache(final Duration ttl) {
		this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(ttl.toMillis());
	}

	/**
	 * Returns cached value if present and valid; otherwise returns the single
	 * running task or starts one via {@code supplier}. A value is valid if it has
	 * not expired by TTL and (when stamps are available) matches the current
	 * document stamp. Results computed for an older stamp are not cached.
	 */
	public CompletableFuture<V> computeIfAbsent(final IDocument doc, final int offset,
			final Supplier<CompletableFuture<V>> supplier) {
		// Fast path: return a completed future if a fresh value is already cached
		final @Nullable V cachedNow = getNow(doc, offset);
		if (cachedNow != null)
			return CompletableFuture.completedFuture(cachedNow);

		final ConcurrentMap<Integer, InFlight<V>> byOffset = inFlight.computeIfAbsent(doc,
				d -> new ConcurrentHashMap<>());
		final long startStamp = DocumentUtil.getDocumentModificationStamp(doc);
		final var started = new boolean[1];
		final InFlight<V> entry = castNonNull(byOffset.compute(offset, (k, running) -> {
			if (running != null && !running.stale(startStamp))
				return running;
			started[0] = true;
			return new InFlight<>(supplier.get(), startStamp);
		}));
		if (started[0]) {
			// registered outside compute() since an already completed future runs the callback inline
			entry.future().whenComplete((v, t) -> {
				byOffset.remove(offset, entry);
				if (t == null && v != null && !entry.stale(DocumentUtil.getDocumentModificationStamp(doc))) {
					put(doc, offset, v);
				}
			});
		}
		return entry.future();
	}

	/**
	 * @return the cached value if present and valid; removes and returns null if
	 *         TTL expired or the document stamp changed.
	 */
	public @Nullable V getNow(final IDocument doc, final int offset) {
		final ConcurrentMap<Integer, Entry<V>> byOffset = cache.get(doc);
		if (byOffset == null)
			return null;

		final Entry<V> e = byOffset.get(offset);
		if (e == null)
			return null;

		final long nowStamp = DocumentUtil.getDocumentModificationStamp(doc);
		if (e.stale(ttlNanos, nowStamp)) {
			byOffset.remove(offset, e);
			return null;
		}
		return e.value;
	}

	public void invalidate(final IDocument doc) {
		cache.remove(doc); // synchronizedMap handles its own locking
		final var map = inFlight.remove(doc); // remove returns the per-doc map, if any
		if (map != null) {
			map.values().forEach(f -> f.future().cancel(true));
		}
	}

	/**
	 * Stores a value tagged with the current document modification stamp.
	 */
	public void put(final IDocument doc, final int offset, final V value) {
		cache.compute(doc, (d, byOffset) -> {
			final ConcurrentMap<Integer, Entry<V>> map = byOffset != null ? byOffset : new ConcurrentHashMap<>();
			final long stamp = DocumentUtil.getDocumentModificationStamp(doc);
			map.put(offset, new Entry<>(value, System.nanoTime(), stamp));
			return map;
		});
	}
}
