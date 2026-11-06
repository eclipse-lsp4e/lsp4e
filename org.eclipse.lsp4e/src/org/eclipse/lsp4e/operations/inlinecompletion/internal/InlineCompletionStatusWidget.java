/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import org.eclipse.core.commands.common.CommandException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.InlineCompletionPreferencePage;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.menus.WorkbenchWindowControlContribution;

/**
 * Shows the {@link InlineCompletionStatus} in the status bar of a workbench window, while there is
 * anything to show: inline completion is enabled and the active editor has a server with the
 * capability. Clicking it switches between the trigger modes, and its context menu offers the rest.
 */
public class InlineCompletionStatusWidget extends WorkbenchWindowControlContribution {

	private static final String PREFERENCE_PAGE_ID = "org.eclipse.lsp4e.preferences.inlineCompletion"; //$NON-NLS-1$
	private static final String CUSTOMIZE_KEY_BINDINGS_COMMAND_ID = "org.eclipse.lsp4e.inlineCompletion.customizeKeyBindings"; //$NON-NLS-1$

	private @Nullable CLabel label;
	private @Nullable InlineCompletionStatusSpinner spinner;

	private final Runnable statusListener = this::updateOnUiThread;
	private final IPropertyChangeListener preferenceListener = event -> {
		if (InlineCompletionPreferencePage.PREF_AUTOMATIC.equals(event.getProperty())) {
			updateOnUiThread();
		}
	};
	private final IPartListener2 partListener = new IPartListener2() {
		@Override
		public void partActivated(IWorkbenchPartReference partRef) {
			InlineCompletionController.publishActiveEditorStatus();
			update();
		}

		@Override
		public void partBroughtToTop(IWorkbenchPartReference partRef) {
			InlineCompletionController.publishActiveEditorStatus();
			update();
		}
	};

	/** Returns the shared image of the given state, which must not be disposed. */
	static Image getImage(InlineCompletionStatus.State state) {
		return PlatformUI.getWorkbench().getSharedImages().getImage(
				state == InlineCompletionStatus.State.ERROR ? ISharedImages.IMG_OBJS_ERROR_TSK : ISharedImages.IMG_OBJS_INFO_TSK);
	}

	@Override
	protected Control createControl(Composite parent) {
		final var control = new CLabel(parent, SWT.NONE);
		label = control;
		control.setMargins(2, 0, 2, 0);
		control.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseUp(MouseEvent event) {
				if (event.button == 1) {
					InlineCompletionHandlers.toggleTriggerMode();
				}
			}
		});
		control.setMenu(createMenuManager().createContextMenu(control));

		final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		InlineCompletionStatus.getInstance().addListener(statusListener);
		store.addPropertyChangeListener(preferenceListener);
		final IWorkbenchWindow window = getWorkbenchWindow();
		if (window != null) {
			window.getPartService().addPartListener(partListener);
		}
		control.addDisposeListener(event -> {
			InlineCompletionStatus.getInstance().removeListener(statusListener);
			store.removePropertyChangeListener(preferenceListener);
			if (window != null) {
				window.getPartService().removePartListener(partListener);
			}
			final InlineCompletionStatusSpinner current = spinner;
			if (current != null) {
				current.dispose();
			}
		});
		update();
		return control;
	}

	private MenuManager createMenuManager() {
		final var manager = new MenuManager();
		manager.setRemoveAllWhenShown(true);
		manager.addMenuListener(menu -> {
			menu.add(new InlineCompletionStatusContribution());
			menu.add(new Separator());
			final var automatic = new Action(Messages.InlineCompletion_menu_automatic, IAction.AS_CHECK_BOX) {
				@Override
				public void run() {
					InlineCompletionHandlers.toggleTriggerMode();
				}
			};
			automatic.setChecked(LanguageServerPlugin.getDefault().getPreferenceStore()
					.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC));
			menu.add(automatic);
			menu.add(new Action(Messages.InlineCompletion_menu_preferences) {
				@Override
				public void run() {
					final CLabel control = label;
					if (control != null && !control.isDisposed()) {
						PreferencesUtil.createPreferenceDialogOn(control.getShell(), PREFERENCE_PAGE_ID, null, null).open();
					}
				}
			});
			menu.add(new Action(Messages.InlineCompletion_menu_keyBindings) {
				@Override
				public void run() {
					final IHandlerService handlers = PlatformUI.getWorkbench().getService(IHandlerService.class);
					if (handlers != null) {
						try {
							handlers.executeCommand(CUSTOMIZE_KEY_BINDINGS_COMMAND_ID, null);
						} catch (CommandException e) {
							LanguageServerPlugin.logError(e);
						}
					}
				}
			});
		});
		return manager;
	}

	private void updateOnUiThread() {
		final CLabel control = label;
		if (control != null && !control.isDisposed()) {
			final Display display = control.getDisplay();
			if (Display.getCurrent() == display) {
				update();
			} else {
				display.asyncExec(this::update);
			}
		}
	}

	@Override
	public void update() {
		final CLabel control = label;
		if (control == null || control.isDisposed()) {
			return;
		}
		final InlineCompletionStatus.State state = InlineCompletionStatus.getInstance().getState();
		control.setVisible(state != InlineCompletionStatus.State.INITIALIZING
				&& InlineCompletionExtensions.getInstance().isEnabled());
		control.setToolTipText(state.getTooltip());
		final InlineCompletionStatusSpinner current = spinner;
		if (state == InlineCompletionStatus.State.FETCHING) {
			if (current == null) {
				spinner = new InlineCompletionStatusSpinner(control);
			} else {
				current.start();
			}
		} else {
			if (current != null) {
				current.stop();
			}
			control.setImage(getImage(state));
		}
	}
}
