/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

/**
 * Decides whether and how a candidate is shown for the current document and caret.
 * <p>
 * The text between the start of the replaced range and the caret is what the user has typed. As the
 * specification requires, the candidate is shown only if that text is a prefix of its filter text.
 * What is shown is the candidate's text minus the typed text, and minus the text after the caret that
 * is still inside the range when the candidate's text ends with it (for example an auto-closed
 * {@code )}). A candidate that would need text after the caret to be replaced by something else cannot
 * be shown as an insertion and is hidden.
 */
public final class GhostTextComputer {

	private GhostTextComputer() {
	}

	/**
	 * @return what to show, or {@code null} if the candidate is not shown
	 */
	public static @Nullable GhostText compute(IDocument document, int caret, TrackedRange range,
			InlineCompletionCandidate candidate) {
		if (caret < range.start() || caret > range.end() || range.end() > document.getLength()) {
			return null;
		}
		try {
			final String typed = document.get(range.start(), caret - range.start());
			final String text = candidate.text();
			if (!candidate.filterText().startsWith(typed) || !text.startsWith(typed)) {
				return null;
			}
			String remaining = text.substring(typed.length());
			int anchor = caret;
			final String tail = document.get(caret, range.end() - caret);
			if (!tail.isEmpty()) {
				if (remaining.endsWith(tail)) {
					remaining = remaining.substring(0, remaining.length() - tail.length());
				} else if (remaining.startsWith(tail)) {
					remaining = remaining.substring(tail.length());
					anchor = range.end();
				} else {
					return null;
				}
			}
			if (remaining.isEmpty()) {
				return null;
			}
			return new GhostText(anchor, remaining, range.start(), range.end(), candidate);
		} catch (BadLocationException e) {
			return null;
		}
	}
}
