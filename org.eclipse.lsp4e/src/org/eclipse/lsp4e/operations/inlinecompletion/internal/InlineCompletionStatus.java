/*******************************************************************************
 * Copyright (c) 2025, 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.ui.Messages;

/**
 * The status of inline completion shown to the user. The inline completion
 * engine sets it from the language servers: {@link State#INITIALIZING} until a
 * server for the document advertises {@code inlineCompletionProvider},
 * {@link State#READY} once one does, {@link State#FETCHING} while a
 * {@code textDocument/inlineCompletion} request is in flight and
 * {@link State#ERROR} when a request fails, until the next one succeeds.
 */
public final class InlineCompletionStatus {

	/**
	 * The states of inline completion.
	 */
	public enum State {
		INITIALIZING(Messages.InlineCompletion_status_initializing),
		READY(Messages.InlineCompletion_status_ready),
		FETCHING(Messages.InlineCompletion_status_fetching),
		ERROR(Messages.InlineCompletion_status_error);

		private final String tooltip;

		State(String tooltip) {
			this.tooltip = tooltip;
		}

		public String getTooltip() {
			return tooltip;
		}
	}

	private static final class Holder {
		static final InlineCompletionStatus INSTANCE = new InlineCompletionStatus();
	}

	private volatile State state = State.INITIALIZING;
	private volatile @Nullable Runnable listener;

	private InlineCompletionStatus() {
	}

	public static InlineCompletionStatus getInstance() {
		return Holder.INSTANCE;
	}

	/**
	 * Sets the listener notified on every state change, on the thread changing the
	 * state.
	 *
	 * @param listener
	 *            the listener, or {@code null} to remove it
	 */
	public void setListener(@Nullable Runnable listener) {
		this.listener = listener;
	}

	public State getState() {
		return state;
	}

	/**
	 * Sets the state and notifies the listener.
	 *
	 * @param state
	 *            the new state
	 */
	public void setState(State state) {
		this.state = state;
		final Runnable current = listener;
		if (current != null) {
			current.run();
		}
	}
}
