/*******************************************************************************
 * Copyright (c) 2026 Advantest Europe GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * 				Raghunandana Murthappa
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.EditorPart;

/**
 * A simple editor that uses a custom document provider which does not register
 * the document with the FileBuffers.
 */
public class NonBufferEditor extends EditorPart {

	private TextViewer viewer;
	private IDocument document;

	@Override
	public void doSave(IProgressMonitor monitor) {
		// no-op
	}

	@Override
	public void doSaveAs() {
		// no-op
	}

	@Override
	public void init(IEditorSite site, IEditorInput input) throws PartInitException {
		setSite(site);
		setInput(input);
		// create the document using the NonBufferDocumentProvider which does
		// NOT register the document with FileBuffers. This mirrors Xtext-like
		// editors which own documents outside of the FileBuffer manager.
		document = NonBufferDocumentProvider.createDocument(input);
	}

	@Override
	public boolean isDirty() {
		return false;
	}

	@Override
	public boolean isSaveAsAllowed() {
		return false;
	}

	@Override
	public void createPartControl(Composite parent) {
		viewer = new TextViewer(parent, SWT.NONE);
		if (document == null) {
			// create a default document if none was created in init
			// In real usage, the document should be created in init() by the document
			// provider, but this fallback allows the editor to be used without a proper
			// input for testing purposes.
			document = NonBufferDocumentProvider.createDocument(getEditorInput());
		}
		viewer.setDocument(document);
	}

	@Override
	public void setFocus() {
		Control c = viewer.getControl();
		if (c != null && !c.isDisposed())
			c.setFocus();
	}

	public IDocument getDocument() {
		return document;
	}
}
