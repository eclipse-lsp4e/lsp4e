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

import org.eclipse.jface.action.IContributionItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.CompoundContributionItem;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;

/**
 * Dynamic menu contribution showing the inline completion status as a disabled
 * menu item.
 */
public class InlineCompletionStatusContribution extends CompoundContributionItem {

	@Override
	protected IContributionItem[] getContributionItems() {
		final var parameters = new CommandContributionItemParameter(PlatformUI.getWorkbench(), null,
				DisplayStatus.ID, CommandContributionItem.STYLE_PUSH);
		parameters.label = InlineCompletionStatus.getInstance().getState().getTooltip();
		// TODO show the icon of the state, as the status bar widget of the inline completion engine does
		return new IContributionItem[] { new CommandContributionItem(parameters) };
	}
}
