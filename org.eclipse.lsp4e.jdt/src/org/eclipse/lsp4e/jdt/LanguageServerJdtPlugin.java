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
package org.eclipse.lsp4e.jdt;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.lsp4e.jdt.preferences.PreferenceConstants;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class LanguageServerJdtPlugin extends AbstractUIPlugin {
	
	private static @Nullable LanguageServerJdtPlugin plugin;
	
	private final IPropertyChangeListener prefsLisetner = new IPropertyChangeListener() {
		
		@SuppressWarnings("restriction")
		@Override
		public void propertyChange(PropertyChangeEvent event) {
			if (PreferenceConstants.PREF_SEMANTIC_TOKENS_SWITCH.equals(event.getProperty())) {
				for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
					for (IWorkbenchPage page : window.getPages()) {
						for (IEditorReference editorRef : page.getEditorReferences()) {
							IEditorPart editor = editorRef.getEditor(false);
							if (editor instanceof JavaEditor je) {
								je.refreshSemanticHighlighting();
							}
						}
					}
				}
			}
		}
	};
	
	public static final @Nullable LanguageServerJdtPlugin getDefault() {
		return plugin;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		getPreferenceStore().addPropertyChangeListener(prefsLisetner);
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		getPreferenceStore().removePropertyChangeListener(prefsLisetner);
		plugin = null;
		super.stop(context);
	}

}
