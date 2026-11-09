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
 * How a document change relates to the caret, which decides whether a visible inline completion
 * survives it and whether it triggers a new request.
 */
public enum EditKind {
	/** Text inserted at the caret, as by typing or pasting. */
	TYPE_AT_CARET,
	/** Text deleted right before the caret, as by Backspace. */
	BACKSPACE_AT_CARET,
	/** Text deleted right after the caret, as by Delete. */
	DELETE_AT_CARET,
	/**
	 * Any other change: a replacement (including typing in overwrite mode), or a change away from the
	 * caret.
	 */
	OTHER;

	/**
	 * @param caret the caret offset before the change
	 * @param offset the offset of the change
	 * @param length the number of characters replaced
	 * @param text the text inserted
	 */
	public static EditKind classify(int caret, int offset, int length, String text) {
		if (length == 0 && !text.isEmpty() && offset == caret) {
			return TYPE_AT_CARET;
		}
		if (length > 0 && text.isEmpty()) {
			if (offset + length == caret) {
				return BACKSPACE_AT_CARET;
			}
			if (offset == caret) {
				return DELETE_AT_CARET;
			}
		}
		return OTHER;
	}

	/** Returns whether a change of this kind made by the user may trigger an automatic request. */
	public boolean triggersAutomatically() {
		return this != OTHER;
	}
}
