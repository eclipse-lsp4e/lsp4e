/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.lsp4e.internal.NullSafetyHelper;
import org.eclipse.lsp4e.jdt.LanguageServerJdtPlugin;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

public final class LspJdtPreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(NullSafetyHelper.castNonNull(LanguageServerJdtPlugin.getDefault()).getPreferenceStore());
	}

	@Override
	protected void createFieldEditors() {
		Composite fieldEditorParent = getFieldEditorParent();
		
		addField(new BooleanFieldEditor(PreferenceConstants.PREF_SEMANTIC_TOKENS_SWITCH, "Embedded languages syntax highlighting in Java Editor", fieldEditorParent));
	}

}
