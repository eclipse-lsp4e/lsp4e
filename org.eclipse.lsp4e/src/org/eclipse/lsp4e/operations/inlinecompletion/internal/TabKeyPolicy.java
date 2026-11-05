/*******************************************************************************
 * Copyright (c) 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   João Dinis Ferreira (Avaloq Group AG) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import org.eclipse.swt.SWT;

/**
 * Decides whether the Tab key accepts the visible inline completion.
 */
public final class TabKeyPolicy {

	private TabKeyPolicy() {
	}

	/**
	 * @param character the typed character
	 * @param stateMask the modifier keys pressed
	 * @param ghostPainted whether the ghost text has been drawn at least once, so that the user has seen
	 *            what Tab accepts
	 * @param linePrefix the text of the caret line before the caret
	 * @param ghostText the ghost text shown at the caret
	 * @param tabInsertion the text the editor's Tab key would insert at the caret, see
	 *            {@link #tabInsertion(String, int, boolean)}
	 * @return {@code true} if Tab must accept, {@code false} if it must keep its usual meaning
	 */
	public static boolean tabAccepts(char character, int stateMask, boolean ghostPainted, String linePrefix,
			String ghostText, String tabInsertion) {
		if (character != SWT.TAB || (stateMask & SWT.MODIFIER_MASK) != 0 || !ghostPainted) {
			return false;
		}
		// In the indentation, when the suggestion starts with exactly what Tab inserts, Tab indents (typing
		// through the suggestion) instead of accepting. Otherwise indenting would not match the suggestion,
		// which would disappear, so Tab accepts.
		return !(linePrefix.isBlank() && !tabInsertion.isEmpty() && ghostText.startsWith(tabInsertion));
	}

	/**
	 * @param linePrefix the text of the caret line before the caret
	 * @param tabWidth the editor's tab width
	 * @param spacesForTabs whether the editor inserts spaces for tabs
	 * @return the text the editor's Tab key inserts at the caret: a tab character, or spaces up to the next
	 *         tab stop
	 */
	public static String tabInsertion(String linePrefix, int tabWidth, boolean spacesForTabs) {
		if (!spacesForTabs || tabWidth <= 0) {
			return "\t"; //$NON-NLS-1$
		}
		final int column = GhostTextLayout.visualColumn(linePrefix, tabWidth);
		return " ".repeat(tabWidth - column % tabWidth); //$NON-NLS-1$
	}
}
