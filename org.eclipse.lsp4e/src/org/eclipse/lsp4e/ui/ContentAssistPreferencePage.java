/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class ContentAssistPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	public static final String INVOKE_AFTER_SEMICOLON_ENABLED = "invokeContentAssistOnSemicolon.enabled"; //$NON-NLS-1$

	public static final class PreferenceInitializer extends AbstractPreferenceInitializer {
		@Override
		public void initializeDefaultPreferences() {
			final var store = LanguageServerPlugin.getDefault().getPreferenceStore();
			store.setDefault(INVOKE_AFTER_SEMICOLON_ENABLED, false);
		}
	}

	public ContentAssistPreferencePage() {
		super(GRID);
		setPreferenceStore(LanguageServerPlugin.getDefault().getPreferenceStore());
	}

	@Override
	public void createFieldEditors() {
		final Composite parent = getFieldEditorParent();

		/*
		 * check box to globally enable/disable on type formatting
		 */
		final var foldingEnabled = new BooleanFieldEditor( //
				INVOKE_AFTER_SEMICOLON_ENABLED, //
				Messages.PreferencesPage_enableTriggerContentAssistOnSemicolon, //
				parent);
		addField(foldingEnabled);

	}

	@Override
	public void init(IWorkbench workbench) {
	}
}
