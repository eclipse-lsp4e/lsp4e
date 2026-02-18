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

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;

/**
 * A fake provider that does not register the document with the FileBuffers. It
 * can create an IDocument adapted to a URI for inputs of type
 * {@link NonBufferEditorInput}.
 */
public class NonBufferDocumentProvider {

	public static IDocument createDocument(IEditorInput input) {
		if (input instanceof NonBufferEditorInput nbi) {
			URI uri = nbi.getUri();
			return new DocWithAdapter(uri);
		}
		return new Document();
	}

	private static final class DocWithAdapter extends Document implements org.eclipse.core.runtime.IAdaptable {
		private static final long serialVersionUID = 1L;
		private final URI uri;

		DocWithAdapter(URI uri) {
			super();
			this.uri = uri;
		}

		@Override
		public <T> T getAdapter(Class<T> adapter) {
			if (adapter == URI.class) {
				@SuppressWarnings("unchecked") T t = (T) uri;
				return t;
			}
			return null;
		}
	}
}
