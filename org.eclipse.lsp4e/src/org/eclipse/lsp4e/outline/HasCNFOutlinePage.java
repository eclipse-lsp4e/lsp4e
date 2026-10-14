/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class HasCNFOutlinePage extends PropertyTester {

	@Override
	public boolean test(@Nullable Object receiver, String property, Object[] args, @Nullable Object expectedValue) {
		if (receiver instanceof ContentOutline outline) {
			return outline.getCurrentPage() instanceof CNFOutlinePage;
		}
		if (receiver instanceof IEditorPart editor) {
			// the test runs in the UI thread each time the active editor changes, for every editor, and asking an
			// editor for its outline page may be slow: some editors create it, or even reconcile their document.
			// EditorToOutlineAdapterFactory gives a CNFOutlinePage only for an input that a language server can
			// support. A multi-page editor asks its active page, whose input may differ: it is still asked.
			if (!(editor instanceof MultiPageEditorPart)) {
				IEditorInput editorInput = editor.getEditorInput();
				if (editorInput == null || !LanguageServersRegistry.getInstance().canUseLanguageServer(editorInput)) {
					return false;
				}
			}
			IContentOutlinePage outlinePage = editor.getAdapter(IContentOutlinePage.class);
			return outlinePage instanceof CNFOutlinePage;
		}
		return false;
	}

}
