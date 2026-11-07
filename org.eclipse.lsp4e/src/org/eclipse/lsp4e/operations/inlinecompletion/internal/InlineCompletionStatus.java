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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.InlineCompletionPreferencePage;
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
		READY(Messages.InlineCompletion_status_ready_automatic),
		FETCHING(Messages.InlineCompletion_status_fetching),
		ERROR(Messages.InlineCompletion_status_error);

		private final String tooltip;

		State(String tooltip) {
			this.tooltip = tooltip;
		}

		public String getTooltip() {
			if (this == READY && !LanguageServerPlugin.getDefault().getPreferenceStore()
					.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC)) {
				return Messages.InlineCompletion_status_ready_manual;
			}
			return tooltip;
		}
	}

	private static final class Holder {
		static final InlineCompletionStatus INSTANCE = new InlineCompletionStatus();
	}

	private volatile State state = State.INITIALIZING;
	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	private InlineCompletionStatus() {
	}

	public static InlineCompletionStatus getInstance() {
		return Holder.INSTANCE;
	}

	/**
	 * Adds a listener notified on every state change, on the thread changing the
	 * state.
	 *
	 * @param listener
	 *            the listener
	 */
	public void addListener(Runnable listener) {
		listeners.add(listener);
	}

	/**
	 * Removes a listener.
	 *
	 * @param listener
	 *            the listener
	 */
	public void removeListener(Runnable listener) {
		listeners.remove(listener);
	}

	public State getState() {
		return state;
	}

	/**
	 * Sets the state and, if it changes, notifies the listeners.
	 *
	 * @param state
	 *            the new state
	 */
	public void setState(State state) {
		if (this.state != state) {
			this.state = state;
			listeners.forEach(Runnable::run);
		}
	}
}
