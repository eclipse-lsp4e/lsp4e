/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Multi-page editor like the PDE or m2e form editors: a source page backed by a file buffer, while the editor itself
 * does not adapt to {@link ITextEditor}.
 */
public class MultiPageTestEditor extends MultiPageEditorPart {

	@Override
	protected void createPages() {
		addPage(new Label(getContainer(), SWT.NONE));
		try {
			addPage(new TextEditor(), getEditorInput());
		} catch (PartInitException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		return adapter == ITextEditor.class ? null : super.getAdapter(adapter);
	}

	@Override
	public void doSave(IProgressMonitor monitor) {
	}

	@Override
	public void doSaveAs() {
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}
}
