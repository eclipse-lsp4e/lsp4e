/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.jdt.LanguageServerJdtPlugin;

public final class LspJdtPrefsInitializer extends AbstractPreferenceInitializer {
	
	public LspJdtPrefsInitializer() {
	}

	@Override
	public void initializeDefaultPreferences() {
		LanguageServerJdtPlugin plugin = LanguageServerJdtPlugin.getDefault();
		if (plugin == null) {
			throw new IllegalStateException("Plugin hasn't been started!");
		}
		IPreferenceStore store = plugin.getPreferenceStore();
		
		store.setDefault(PreferenceConstants.PREF_SEMANTIC_TOKENS_SWITCH, false);
	}

}
