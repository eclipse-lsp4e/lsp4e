/*******************************************************************************
 * Copyright (c) 2026 Avaloq Group AG.
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
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public class InlineCompletionPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	/** Whether inline completions are requested automatically while typing. */
	public static final String PREF_AUTOMATIC = "inlineCompletion.automatic"; //$NON-NLS-1$
	/** How long to wait after typing before requesting inline completions, in milliseconds. */
	public static final String PREF_DELAY = "inlineCompletion.delay"; //$NON-NLS-1$
	/** Whether the next inline completion is requested as soon as one is accepted or typed to its end. */
	public static final String PREF_FOLLOW_UP = "inlineCompletion.followUp"; //$NON-NLS-1$

	public static final class PreferenceInitializer extends AbstractPreferenceInitializer {
		@Override
		public void initializeDefaultPreferences() {
			final var store = LanguageServerPlugin.getDefault().getPreferenceStore();
			store.setDefault(PREF_AUTOMATIC, true);
			store.setDefault(PREF_DELAY, 150);
			store.setDefault(PREF_FOLLOW_UP, true);
		}
	}

	public InlineCompletionPreferencePage() {
		super(GRID);
		setPreferenceStore(LanguageServerPlugin.getDefault().getPreferenceStore());
	}

	@Override
	public void createFieldEditors() {
		final Composite parent = getFieldEditorParent();
		addField(new BooleanFieldEditor(PREF_AUTOMATIC, Messages.PreferencesPage_inlineCompletionAutomatic, parent));
		addField(new BooleanFieldEditor(PREF_FOLLOW_UP, Messages.PreferencesPage_inlineCompletionFollowUp, parent));
		final var delay = new IntegerFieldEditor(PREF_DELAY, Messages.PreferencesPage_inlineCompletionDelay, parent);
		delay.setValidRange(0, 5000);
		addField(delay);
	}

	@Override
	public void init(IWorkbench workbench) {
	}

}
