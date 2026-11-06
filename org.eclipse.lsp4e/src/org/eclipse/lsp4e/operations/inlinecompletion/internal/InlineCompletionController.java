/*******************************************************************************
 * Copyright (c) 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import org.eclipse.lsp4e.operations.inlinecompletion.CurrentCompletionInfo;

/**
 * Placeholder for the inline completion engine, which is not part of this
 * contribution yet: triggering, sending {@code textDocument/inlineCompletion}
 * requests, rendering the suggestion as ghost text, cycling through, accepting
 * and dismissing suggestions, together with its commands, key bindings,
 * trigger preferences and status bar widget.
 * <p>
 * The engine fires the {@link org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEventKind
 * events} with {@link InlineCompletionExtensions#fireEvent}, sets the
 * {@link InlineCompletionStatus} and uses {@link InlineCompletionUtil} and
 * {@link InlineCompletionPopup}.
 */
public final class InlineCompletionController {

	private InlineCompletionController() {
	}

	/**
	 * Returns the suggestion currently shown.
	 *
	 * @return the suggestion, or {@link CurrentCompletionInfo#NONE} if none is
	 *         shown
	 */
	public static CurrentCompletionInfo getCurrentCompletionInfo() {
		// TODO return the suggestion shown by the inline completion engine
		return CurrentCompletionInfo.NONE;
	}
}
