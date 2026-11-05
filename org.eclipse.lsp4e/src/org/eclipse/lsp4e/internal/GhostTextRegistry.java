/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.jface.text.codemining.LineHeaderCodeMining;

/**
 * Tells LSP4E's other code mining providers where ghost text is shown, so that they leave room for it.
 * <p>
 * JFace merges code minings at the same position into one annotation and lets header minings of the
 * same line fight over its vertical indent, so an inlay hint at the caret or a code lens above the line
 * the ghost text continues on would be drawn into the ghost text. While ghost text is shown, such
 * minings are left out; they come back when it is dismissed.
 */
public final class GhostTextRegistry {

	private record Occupied(int contentOffset, int headerLine) {
	}

	private static final Map<IDocument, Occupied> OCCUPIED = Collections.synchronizedMap(new WeakHashMap<>());

	private static final ThreadLocal<IDocument> GHOST_TEXT_REFRESH = new ThreadLocal<>();

	private GhostTextRegistry() {
	}

	/**
	 * Runs the given refresh of the code minings of the document, made only to show or hide ghost text:
	 * JFace has no way to refresh a single provider's minings, and it asks all providers while it runs.
	 */
	public static void refreshForGhostText(IDocument document, Runnable refresh) {
		GHOST_TEXT_REFRESH.set(document);
		try {
			refresh.run();
		} finally {
			GHOST_TEXT_REFRESH.remove();
		}
	}

	/** Returns whether the code minings of the document are being refreshed only for ghost text. */
	public static boolean isRefreshForGhostText(IDocument document) {
		return GHOST_TEXT_REFRESH.get() == document;
	}

	/**
	 * Records the ghost text shown in the document.
	 *
	 * @param contentOffset the offset of the ghost text's first line, or -1
	 * @param headerLine the line the ghost text's further lines are drawn above, or -1
	 */
	public static void set(IDocument document, int contentOffset, int headerLine) {
		OCCUPIED.put(document, new Occupied(contentOffset, headerLine));
	}

	public static void clear(IDocument document) {
		OCCUPIED.remove(document);
	}

	/**
	 * Returns the given minings without those that would collide with the ghost text shown in the
	 * document.
	 */
	public static List<? extends ICodeMining> filter(IDocument document, List<? extends ICodeMining> minings) {
		final Occupied occupied = OCCUPIED.get(document);
		if (occupied == null) {
			return minings;
		}
		return minings.stream().filter(mining -> !collides(document, occupied, mining)).toList();
	}

	private static boolean collides(IDocument document, Occupied occupied, ICodeMining mining) {
		final int offset = mining.getPosition().getOffset();
		if (mining instanceof LineHeaderCodeMining) {
			try {
				return occupied.headerLine >= 0 && document.getLineOfOffset(offset) == occupied.headerLine;
			} catch (BadLocationException e) {
				return false;
			}
		}
		// a line content mining is drawn by widening the character it covers, which is the character
		// after its offset or, for most providers, the one before: either may be the ghost text's
		return occupied.contentOffset >= 0 && (offset == occupied.contentOffset || offset == occupied.contentOffset + 1);
	}
}
