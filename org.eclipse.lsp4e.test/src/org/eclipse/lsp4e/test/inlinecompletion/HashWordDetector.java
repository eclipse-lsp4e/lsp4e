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
package org.eclipse.lsp4e.test.inlinecompletion;

import org.eclipse.jface.text.rules.IWordDetector;

/**
 * Words of letters, digits, underscores and {@code #}.
 */
public class HashWordDetector implements IWordDetector {

	@Override
	public boolean isWordStart(char c) {
		return isWordPart(c);
	}

	@Override
	public boolean isWordPart(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '#';
	}
}
