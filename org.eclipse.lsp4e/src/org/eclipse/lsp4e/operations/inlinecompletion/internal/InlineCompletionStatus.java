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

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.InlineCompletionPreferencePage;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;

/**
 * The status of inline completion shown to the user. The inline completion
 * engine sets it from the language servers: {@link State#INITIALIZING} until a
 * server for the document advertises {@code inlineCompletionProvider},
 * {@link State#READY_AUTOMATIC} or {@link State#READY_MANUAL} once one does, {@link State#FETCHING} while a
 * {@code textDocument/inlineCompletion} request is in flight and
 * {@link State#ERROR} when a request fails, until the next one succeeds.
 */
public final class InlineCompletionStatus {

	/**
	 * The states of inline completion.
	 */
	public enum State {
		INITIALIZING(Messages.InlineCompletion_status_initializing),
		/** Ready, with requests sent automatically while typing. */
		READY_AUTOMATIC(Messages.InlineCompletion_status_ready_automatic),
		/** Ready, with requests sent only when the user asks for them. */
		READY_MANUAL(Messages.InlineCompletion_status_ready_manual),
		FETCHING(Messages.InlineCompletion_status_fetching),
		ERROR(Messages.InlineCompletion_status_error);

		private final String tooltip;

		State(String tooltip) {
			this.tooltip = tooltip;
		}

		public String getTooltip() {
			return tooltip;
		}

		/**
		 * Returns the ready state for how requests are triggered at present, by the
		 * preference.
		 *
		 * @return {@link #READY_AUTOMATIC} or {@link #READY_MANUAL}
		 */
		public static State ready() {
			return LanguageServerPlugin.getDefault().getPreferenceStore()
					.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC) ? READY_AUTOMATIC : READY_MANUAL;
		}

		public boolean isReady() {
			return this == READY_AUTOMATIC || this == READY_MANUAL;
		}

		/**
		 * Returns the image of the state.
		 *
		 * @return the shared image, which must not be disposed, or {@code null} if it
		 *         is not available
		 */
		public @Nullable Image getImage() {
			return switch (this) {
			case INITIALIZING -> LSPImages.getImage(LSPImages.IMG_INLINE_COMPLETION_PROGRESS);
			case READY_AUTOMATIC -> LSPImages.getImage(LSPImages.IMG_INLINE_COMPLETION_AUTOMATIC);
			case READY_MANUAL -> LSPImages.getImage(LSPImages.IMG_INLINE_COMPLETION_MANUAL);
			// the status bar pulses it, see InlineCompletionStatusSpinner
			case FETCHING -> ready().getImage();
			case ERROR -> LSPImages.getSharedImage(ISharedImages.IMG_OBJS_ERROR_TSK);
			};
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

	/**
	 * Makes a ready state the one for how requests are triggered at present, after
	 * that has changed.
	 */
	public void updateReadyState() {
		if (state.isReady()) {
			setState(State.ready());
		}
	}
}
