/*******************************************************************************
 * Copyright (c) 2025, 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Handler of the command showing the inline completion status in menus. It is
 * always disabled and does nothing.
 */
public class DisplayStatus extends AbstractHandler {

	public static final String ID = "org.eclipse.lsp4e.inlineCompletion.displayStatus"; //$NON-NLS-1$

	@Override
	public @Nullable Object execute(ExecutionEvent event) {
		return null;
	}

	@Override
	public boolean isEnabled() {
		return false;
	}
}
