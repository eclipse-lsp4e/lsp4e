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

/**
 * The kinds of {@link InlineCompletionEvent}s emitted while inline completions
 * are requested, shown, accepted and dismissed.
 *
 * @since 0.20.0
 */
public enum InlineCompletionEventKind {

	/**
	 * The user typed the shown suggestion instead of accepting it, exactly or
	 * nearly. The text is the typed text.
	 */
	ACCEPT_EDIT,

	/** The whole suggestion was accepted. The text is the inserted text. */
	ACCEPT_FULL,

	/** The next line of the suggestion was accepted. The text is the inserted text. */
	ACCEPT_LINE,

	/** The next word of the suggestion was accepted. The text is the inserted text. */
	ACCEPT_WORD,

	/**
	 * The suggestion was hidden because its editor lost the focus or was closed.
	 * The text is {@code null}.
	 */
	DISABLED,

	/** The user dismissed the suggestion. The text is the whole suggestion. */
	DISMISS,

	/**
	 * More suggestions were requested for the same position. The event has the
	 * UUID of the first request; the text and elapsed time are {@code null}.
	 */
	FETCH_ADDITIONAL,

	/**
	 * Suggestions were requested for a new position. The text and elapsed time
	 * are {@code null}.
	 */
	FETCH_FIRST,

	/**
	 * A suggestion was shown. The text is {@code null}; the elapsed time is
	 * measured from the request.
	 */
	VIEW
}
