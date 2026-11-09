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

/**
 * The document range an inline completion candidate replaces, tracked across edits. Unlike a
 * {@link org.eclipse.jface.text.Position} updated by the default position updater, the start stays put
 * when text is inserted at it and the end grows when text is inserted at it, so text typed at the
 * request offset becomes part of the range.
 */
public record TrackedRange(int start, int end) {

	/**
	 * Returns the range after replacing {@code deletedLength} characters at {@code offset} with
	 * {@code insertedLength} characters.
	 *
	 * @return the updated range, or {@code null} if the change removed the start of the range
	 */
	public @Nullable TrackedRange apply(int offset, int deletedLength, int insertedLength) {
		final int changeEnd = offset + deletedLength;
		final int delta = insertedLength - deletedLength;
		if (offset > end || (offset == end && deletedLength > 0)) {
			// entirely after the range
			return this;
		}
		if (changeEnd < start || (changeEnd == start && offset < start)) {
			// entirely before the range
			return new TrackedRange(start + delta, end + delta);
		}
		if (offset < start) {
			// removes the start of the range
			return null;
		}
		if (changeEnd <= end) {
			// inside the range, or an insertion at its start or end
			return new TrackedRange(start, end + delta);
		}
		// removes the end of the range
		return new TrackedRange(start, offset + insertedLength);
	}
}
