/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.internal;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.codemining.ICodeMining;

/**
 * A code mining that {@link AbstractLSPCodeMiningProvider} can show again without asking the language
 * server for it again. JFace disposes a mining when it replaces it, so what is shown again is a copy.
 */
public interface ReusableCodeMining extends ICodeMining {

	/**
	 * Returns a new code mining for the same result of the language server, in the document as it was
	 * when this one was created.
	 */
	ICodeMining copy() throws BadLocationException;
}
