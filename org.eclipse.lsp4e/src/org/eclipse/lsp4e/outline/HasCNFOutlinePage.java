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
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class HasCNFOutlinePage extends PropertyTester {

	@Override
	public boolean test(@Nullable Object receiver, String property, Object[] args, @Nullable Object expectedValue) {
		if (receiver instanceof ContentOutline outline) {
			return outline.getCurrentPage() instanceof CNFOutlinePage;
		}
		if (receiver instanceof IEditorPart editor) {
			IContentOutlinePage outlinePage = editor.getAdapter(IContentOutlinePage.class);
			return outlinePage instanceof CNFOutlinePage;
		}
		return false;
	}

}
