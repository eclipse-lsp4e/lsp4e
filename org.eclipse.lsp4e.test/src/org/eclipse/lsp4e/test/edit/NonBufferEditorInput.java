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

import java.net.URI;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IPersistableElement;

/**
 * Editor input that carries a {@link URI}. The document for this input is
 * created by {@link NonBufferDocumentProvider#createDocument(IEditorInput)} and
 * therefore is not connected to the Eclipse FileBuffers.
 */
public class NonBufferEditorInput implements IEditorInput {

	private final URI uri;

	public NonBufferEditorInput(URI uri) {
		this.uri = uri;
	}

	public URI getUri() {
		return uri;
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		// The actual IDocument is created by the document provider and the
		// created document is adaptable to the URI. Nothing to provide here.
		return null;
	}

	@Override
	public boolean exists() {
		return false;
	}

	@Override
	public IPersistableElement getPersistable() {
		return null;
	}

	@Override
	public String getName() {
		return "NonBufferEditorInput";
	}

	@Override
	public String getToolTipText() {
		return "NonBufferEditorInput";
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return null;
	}
}
