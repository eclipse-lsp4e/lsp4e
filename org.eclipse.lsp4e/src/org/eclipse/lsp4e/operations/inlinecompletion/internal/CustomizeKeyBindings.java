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
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.PreferencesUtil;

/**
 * Opens the Keys preference page filtered to the inline completion commands.
 */
public class CustomizeKeyBindings extends AbstractHandler {

	private static final String KEYS_PREFERENCE_PAGE_ID = "org.eclipse.ui.preferencePages.Keys"; //$NON-NLS-1$
	private static final String CATEGORY_FILTER_TREE = "CategoryFilterTree"; //$NON-NLS-1$

	@Override
	public @Nullable Object execute(ExecutionEvent event) {
		final PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(UI.getActiveShell(),
				KEYS_PREFERENCE_PAGE_ID, null, null);
		if (dialog != null) {
			dialog.setBlockOnOpen(false);
			dialog.open();
			setFilterText(dialog, Messages.InlineCompletion_keyBindingsFilter);
		}
		return null;
	}

	private static void setFilterText(PreferenceDialog dialog, String filterText) {
		final Shell shell = dialog.getShell();
		if (shell == null || shell.isDisposed()) {
			return;
		}
		final Text filterControl = findFilterTextControl(shell);
		if (filterControl == null) {
			return;
		}
		shell.getDisplay().asyncExec(() -> {
			if (!filterControl.isDisposed()) {
				filterControl.setText(filterText);
				filterControl.notifyListeners(SWT.Modify, new Event());
				filterControl.setFocus();
			}
		});
	}

	private static @Nullable Text findFilterTextControl(Control control) {
		if (control.getClass().getName().contains(CATEGORY_FILTER_TREE)) {
			return findFirstEditableText(control);
		}
		if (control instanceof Composite composite) {
			for (Control child : composite.getChildren()) {
				final Text result = findFilterTextControl(child);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	private static @Nullable Text findFirstEditableText(Control control) {
		if (control instanceof Text text && text.getEditable()) {
			return text;
		}
		if (control instanceof Composite composite) {
			for (Control child : composite.getChildren()) {
				final Text result = findFirstEditableText(child);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}
}
