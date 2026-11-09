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
package org.eclipse.lsp4e.operations.inlinecompletion;

import org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionController;

/**
 * Access to the state of inline completion.
 *
 * @since 0.20.0
 */
public final class InlineCompletions {

	private InlineCompletions() {
	}

	/**
	 * Returns the suggestion currently shown. Must be called on the UI thread.
	 *
	 * @return the suggestion, or {@link CurrentCompletionInfo#NONE} if none is
	 *         shown
	 */
	public static CurrentCompletionInfo getCurrentCompletionInfo() {
		return InlineCompletionController.getCurrentCompletionInfo();
	}
}
