/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

/**
 * What to show for an inline completion candidate in the current document state.
 *
 * @param anchor the document offset at which the ghost text is shown; the caret offset, or the end of
 *            the replaced range when the text after the caret is already part of the suggestion
 * @param text the text shown, possibly spanning several lines
 * @param replaceStart the start of the document range replaced on accept
 * @param replaceEnd the end of the document range replaced on accept
 * @param candidate the candidate this ghost text is for
 */
public record GhostText(int anchor, String text, int replaceStart, int replaceEnd, InlineCompletionCandidate candidate) {
}
