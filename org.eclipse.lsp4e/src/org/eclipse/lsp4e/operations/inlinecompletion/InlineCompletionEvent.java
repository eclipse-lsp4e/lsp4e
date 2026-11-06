/*******************************************************************************
 * Copyright (c) 2025, 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion;

import java.net.URI;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;

/**
 * An event in the life cycle of an inline completion, delivered to the
 * {@link InlineCompletionEventListener}s.
 * <p>
 * This API is provisional: it may still change while inline completion matures.
 *
 * @param uuid
 *            the completion request; all events of a request share it
 * @param kind
 *            the kind of event
 * @param uri
 *            the document, or {@code null} if unknown
 * @param text
 *            the text inserted, typed or dismissed as described for each
 *            {@link InlineCompletionEventKind}, or {@code null}
 * @param elapsedTime
 *            the milliseconds from the request to
 *            {@link InlineCompletionEventKind#VIEW}, or from {@code VIEW} to an
 *            accept, dismiss or disable event; {@code null} for fetch events
 *            and once the time since {@code VIEW} has been reported
 * @since 0.20.0
 */
public record InlineCompletionEvent(UUID uuid, InlineCompletionEventKind kind, @Nullable URI uri,
		@Nullable String text, @Nullable Long elapsedTime) {
}
