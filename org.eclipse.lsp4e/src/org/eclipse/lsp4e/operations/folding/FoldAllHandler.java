/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation.
 *******************************************************************************/
package org.eclipse.lsp4e.operations.folding;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.source.projection.ProjectionViewer;
import org.eclipse.lsp4e.ui.UI;

public class FoldAllHandler extends AbstractHandler {

	@Override
	public @Nullable Object execute(final ExecutionEvent event) {
		if (UI.getActiveTextViewer() instanceof final ProjectionViewer viewer && viewer.isProjectionMode()) {
			UI.runOnUIThread(() -> viewer.doOperation(ProjectionViewer.COLLAPSE_ALL));
		}
		return null;
	}
}
