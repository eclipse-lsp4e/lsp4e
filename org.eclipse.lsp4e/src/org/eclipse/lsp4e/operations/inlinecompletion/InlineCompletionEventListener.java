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
 * Receives {@link InlineCompletionEvent}s, for example to collect usage
 * metrics. Contributed with the {@code eventListener} element of the
 * {@code org.eclipse.lsp4e.inlineCompletion} extension point.
 * <p>
 * This API is provisional: it may still change while inline completion matures.
 *
 * @since 0.20.0
 */
@FunctionalInterface
public interface InlineCompletionEventListener {

	/**
	 * Handles an event. Events are delivered in the order they occur, on a
	 * background thread shared by all listeners, so implementations should
	 * return quickly.
	 *
	 * @param event
	 *            the event
	 */
	void handle(InlineCompletionEvent event);
}
