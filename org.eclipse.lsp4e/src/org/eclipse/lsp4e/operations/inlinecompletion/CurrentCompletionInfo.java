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

import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Read-only information about the inline completion currently shown, for
 * example to let users report a bad suggestion.
 *
 * @param requestUuid
 *            the request that produced the suggestion, or {@code null} if no
 *            request is active
 * @param proposedCompletion
 *            the suggested text, or {@code null} if no suggestion is shown
 * @since 0.20.0
 */
public record CurrentCompletionInfo(@Nullable UUID requestUuid, @Nullable String proposedCompletion) {

	/** No suggestion is shown. */
	public static final CurrentCompletionInfo NONE = new CurrentCompletionInfo(null, null);

	/**
	 * Returns whether a suggestion is shown.
	 *
	 * @return {@code true} if both the request UUID and the proposed completion
	 *         are known
	 */
	public boolean hasActiveCompletion() {
		return requestUuid != null && proposedCompletion != null;
	}
}
