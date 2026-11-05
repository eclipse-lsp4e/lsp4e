/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4j.Command;

/**
 * A validated inline completion item, with its range converted to document offsets of the document
 * version the request was made for.
 *
 * @param source the language server that proposed the item, or {@code null} in tests
 * @param start the start offset of the range to replace
 * @param end the end offset of the range to replace, on the same line as {@code start}
 * @param text the plain text that is previewed (a snippet is expanded), with line delimiters
 *            normalized to the document's
 * @param snippet the snippet to insert on accept, or {@code null} for plain text
 * @param filterText the text the typed text must be a prefix of for the item to be shown
 * @param command the command to execute after inserting the item, or {@code null}
 */
public record InlineCompletionCandidate(@Nullable LanguageServerWrapper source, int start, int end, String text,
		@Nullable String snippet, String filterText, @Nullable Command command) {

	/**
	 * Returns whether this candidate replaces the same text as the other one with the same content, so
	 * that showing both would be redundant.
	 */
	boolean isDuplicateOf(InlineCompletionCandidate other) {
		return start == other.start && end == other.end && text.equals(other.text)
				&& Objects.equals(snippet, other.snippet);
	}
}
