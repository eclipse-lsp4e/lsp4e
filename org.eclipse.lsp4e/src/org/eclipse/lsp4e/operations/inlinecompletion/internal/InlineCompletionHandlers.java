/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import java.util.function.Consumer;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.InlineCompletionPreferencePage;
import org.eclipse.lsp4e.ui.UI;

/**
 * The handlers of the inline completion commands, acting on the controller of the active editor.
 */
public final class InlineCompletionHandlers {

	private InlineCompletionHandlers() {
	}

	private abstract static class ControllerHandler extends AbstractHandler {
		private final Consumer<InlineCompletionController> action;

		ControllerHandler(Consumer<InlineCompletionController> action) {
			this.action = action;
		}

		@Override
		public @Nullable Object execute(ExecutionEvent event) {
			if (UI.getActiveTextViewer() instanceof ISourceViewer viewer) {
				final InlineCompletionController controller = InlineCompletionController.get(viewer);
				if (controller != null) {
					action.accept(controller);
				}
			}
			return null;
		}
	}

	/** Switches between requesting inline completions automatically and only when asked to. */
	public static final class ToggleTriggerMode extends AbstractHandler {
		@Override
		public @Nullable Object execute(ExecutionEvent event) {
			toggleTriggerMode();
			return null;
		}
	}

	static void toggleTriggerMode() {
		final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue(InlineCompletionPreferencePage.PREF_AUTOMATIC,
				!store.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC));
	}

	/** Requests inline completions explicitly. */
	public static final class Trigger extends ControllerHandler {
		public Trigger() {
			super(InlineCompletionController::trigger);
		}
	}

	/** Shows the next inline completion. */
	public static final class Next extends ControllerHandler {
		public Next() {
			super(controller -> controller.cycle(true));
		}
	}

	/** Shows the previous inline completion. */
	public static final class Previous extends ControllerHandler {
		public Previous() {
			super(controller -> controller.cycle(false));
		}
	}

	/** Accepts the inline completion shown. */
	public static final class Accept extends ControllerHandler {
		public Accept() {
			super(InlineCompletionController::accept);
		}
	}

	/** Accepts the next word of the inline completion shown. */
	public static final class AcceptNextWord extends ControllerHandler {
		public AcceptNextWord() {
			super(InlineCompletionController::acceptNextWord);
		}
	}

	/** Accepts the next line of the inline completion shown. */
	public static final class AcceptNextLine extends ControllerHandler {
		public AcceptNextLine() {
			super(InlineCompletionController::acceptNextLine);
		}
	}

	/** Dismisses the inline completion shown. */
	public static final class Dismiss extends ControllerHandler {
		public Dismiss() {
			super(InlineCompletionController::dismiss);
		}
	}
}
