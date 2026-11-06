/*******************************************************************************
 * Copyright (c) 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion;

/**
 * Decides whether inline completion is available, for example based on the
 * user's entitlement. Contributed with the {@code enablement} element of the
 * {@code org.eclipse.lsp4e.inlineCompletion} extension point. Without a
 * contribution, inline completion is enabled.
 *
 * @since 0.20.0
 */
@FunctionalInterface
public interface InlineCompletionEnablement {

	/**
	 * Returns whether inline completion is enabled. May be called on any thread.
	 *
	 * @return {@code true} if inline completion is enabled
	 */
	boolean isEnabled();
}
