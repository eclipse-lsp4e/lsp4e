/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import org.eclipse.swt.SWT;

/**
 * Decides whether the Tab key accepts the visible inline completion.
 */
public final class KeyPolicy {

	private KeyPolicy() {
	}

	/**
	 * @param character the typed character
	 * @param stateMask the modifier keys pressed
	 * @param ghostPainted whether the ghost text has been drawn at least once, so that the user has seen
	 *            what Tab accepts
	 * @param linePrefix the text of the caret line before the caret
	 * @param ghostText the ghost text shown at the caret
	 * @param tabWidth the editor's tab width
	 * @return {@code true} if Tab must accept, {@code false} if it must keep its usual meaning
	 */
	public static boolean tabAccepts(char character, int stateMask, boolean ghostPainted, String linePrefix,
			String ghostText, int tabWidth) {
		if (character != SWT.TAB || (stateMask & SWT.MODIFIER_MASK) != 0 || !ghostPainted) {
			return false;
		}
		// In the indentation, when the suggestion starts with whitespace reaching the next tab stop, Tab
		// indents (typing through the suggestion) instead of accepting.
		if (linePrefix.isBlank() && tabWidth > 0) {
			int whitespace = 0;
			while (whitespace < ghostText.length() && (ghostText.charAt(whitespace) == ' ' || ghostText.charAt(whitespace) == '\t')) {
				whitespace++;
			}
			final int startColumn = GhostTextLayout.visualColumn(linePrefix, tabWidth);
			final int nextTabStop = (startColumn / tabWidth + 1) * tabWidth;
			if (whitespace > 0 && GhostTextLayout.visualColumn(linePrefix + ghostText.substring(0, whitespace), tabWidth) >= nextTabStop) {
				return false;
			}
		}
		return true;
	}
}
