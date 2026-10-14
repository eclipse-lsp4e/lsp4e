/*******************************************************************************
 * Copyright (c) 2026 Cocotec Ltd and others.
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
package org.eclipse.lsp4e.internal;

/**
 * LSP method names, as used in e.g. {@code Registration.method} of dynamic capability registrations.
 */
public final class LSPMethods {

	public static final String TEXT_DOCUMENT_CODE_ACTION = "textDocument/codeAction"; //$NON-NLS-1$
	public static final String TEXT_DOCUMENT_COMPLETION = "textDocument/completion"; //$NON-NLS-1$
	public static final String TEXT_DOCUMENT_DIAGNOSTIC = "textDocument/diagnostic"; //$NON-NLS-1$
	public static final String TEXT_DOCUMENT_FORMATTING = "textDocument/formatting"; //$NON-NLS-1$
	public static final String TEXT_DOCUMENT_ON_TYPE_FORMATTING = "textDocument/onTypeFormatting"; //$NON-NLS-1$
	public static final String TEXT_DOCUMENT_RANGE_FORMATTING = "textDocument/rangeFormatting"; //$NON-NLS-1$
	public static final String TEXT_DOCUMENT_SELECTION_RANGE = "textDocument/selectionRange"; //$NON-NLS-1$
	public static final String TEXT_DOCUMENT_TYPE_HIERARCHY = "textDocument/typeHierarchy"; //$NON-NLS-1$

	public static final String WORKSPACE_DID_CHANGE_WATCHED_FILES = "workspace/didChangeWatchedFiles"; //$NON-NLS-1$
	public static final String WORKSPACE_DID_CHANGE_WORKSPACE_FOLDERS = "workspace/didChangeWorkspaceFolders"; //$NON-NLS-1$
	public static final String WORKSPACE_EXECUTE_COMMAND = "workspace/executeCommand"; //$NON-NLS-1$
	public static final String WORKSPACE_SYMBOL = "workspace/symbol"; //$NON-NLS-1$

	private LSPMethods() {
	}
}
