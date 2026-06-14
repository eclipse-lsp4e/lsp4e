/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.tests.mock;

/**
 * The states of the MockLanguageServer
 */
public enum MockServerState {
	/**
	 * The initial state when the language server was created
	 */
	RUNNING,
	/**
	 * The state after shutdown was called.
	 */
	SHUTDOWN,
	/**
	 * The state after exit was called.
	 */
	EXIT
}
