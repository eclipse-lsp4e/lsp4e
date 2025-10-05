/*******************************************************************************
 * Copyright (c) 2022-3 Cocotec Ltd and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ahmed Hussain (Cocotec Ltd) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.ConcurrentModificationException;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4j.TextEdit;

/**
 * Specialization of <code>Versioned</code> for document edits specifically
 *
 */
public class VersionedEdits extends Versioned<List<? extends TextEdit>> {

	public VersionedEdits(long version, List<? extends TextEdit> data, IDocument document) {
		super(document, version, data);
	}

	/**
	 * Apply the edits from the server, provided the document is unchanged since the request used
	 * to compute the edits
	 *
	 * @throws BadLocationException
	 * @throws ConcurrentModificationException if the document has changed since the server
	 * received the request
	 */
	public void apply() throws BadLocationException, ConcurrentModificationException {
		apply(0);
	}


	/**
	 * Apply the edits from the server, provided the document is unchanged since the request used
	 * to compute the edits
	 *
	 * @param caretOffset the current caret offset, used to adjust the caret position if necessary
	 * @return the new caret offset, or current caret offset if no adjustment is necessary
	 * @throws BadLocationException
	 * @throws ConcurrentModificationException if the document has changed since the server
	 * received the request
	 */
	public int apply(int caretOffset) throws BadLocationException, ConcurrentModificationException {
		if (this.sourceDocumentVersion != DocumentUtil.getDocumentModificationStamp(this.document)) {
			throw new ConcurrentModificationException();
		} else {
			return LSPEclipseUtils.applyEdits(this.document, data, caretOffset);
		}
	}
}
