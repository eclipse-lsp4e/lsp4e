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
package org.eclipse.lsp4e.debug.presentation;

import java.util.Collections;
import java.util.Set;

import org.eclipse.core.runtime.Adapters;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.ui.IDetailPane;
import org.eclipse.debug.ui.IDetailPaneFactory;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.lsp4e.debug.breakpoints.DSPLineBreakpoint;

/**
 * Contributes a detail pane for LSP4E breakpoints in the Breakpoints view.
 */
public class DSPBreakpointDetailPaneFactory implements IDetailPaneFactory {

	static final String PANE_ID = "org.eclipse.lsp4e.debug.detailPane.breakpoint";
	static final String PANE_NAME = "LSP4E Breakpoint";
	static final String PANE_DESCRIPTION = "Edit condition and column for LSP4E breakpoints";

	@Override
	public Set<String> getDetailPaneTypes(final IStructuredSelection selection) {
		return isDSPBreakpointSelection(selection) ? Set.of(PANE_ID) : Collections.emptySet();
	}

	@Override
	public @Nullable String getDefaultDetailPane(final IStructuredSelection selection) {
		return isDSPBreakpointSelection(selection) ? PANE_ID : null;
	}

	@Override
	public @Nullable IDetailPane createDetailPane(final String paneID) {
		return PANE_ID.equals(paneID) ? new DSPBreakpointDetailPane() : null;
	}

	@Override
	public @Nullable String getDetailPaneName(final String id) {
		return PANE_ID.equals(id) ? PANE_NAME : null;
	}

	@Override
	public @Nullable String getDetailPaneDescription(final String id) {
		return PANE_ID.equals(id) ? PANE_DESCRIPTION : null;
	}

	private boolean isDSPBreakpointSelection(final IStructuredSelection selection) {
		if (selection.size() != 1) {
			return false;
		}
		final Object element = selection.getFirstElement();
		if (element == null)
			return false;

		return element instanceof DSPLineBreakpoint
				|| Adapters.adapt(element, IBreakpoint.class) instanceof DSPLineBreakpoint;
	}
}
