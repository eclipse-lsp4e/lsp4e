/*******************************************************************************
 * Copyright (c) 2016, 2024 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.notifications.AbstractNotificationPopup;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.ISharedImages;

public class ServerMessageHandler {

	private ServerMessageHandler() {
		// this class shouldn't be instantiated
	}

	private static final class LSPNotification extends AbstractNotificationPopup {

		private final String label;
		private final MessageParams messageParams;

		public LSPNotification(String label, MessageParams messageParams) {
			super(UI.getDisplay());
			setParentShell(UI.getActiveShell());
			this.label = label;
			this.messageParams = messageParams;
		}

		@Override
		public String getPopupShellTitle() {
			return label;
		}

		@Override
		protected void createContentArea(Composite parent) {
			final var messageLabel = new Label(parent, SWT.WRAP);
			messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
			messageLabel.setText(messageParams.getMessage());
		}

		@Override
		public @Nullable Image getPopupShellImage(int maximumHeight) {
			return switch (messageParams.getType()) {
			case Error -> LSPImages.getSharedImage(ISharedImages.IMG_OBJS_ERROR_TSK);
			case Warning -> LSPImages.getSharedImage(ISharedImages.IMG_OBJS_WARN_TSK);
			case Info -> LSPImages.getSharedImage(ISharedImages.IMG_OBJS_INFO_TSK);
			default -> null;
			};
		}

	}

	public static void logMessage(LanguageServerWrapper wrapper, MessageParams params) {
		final var log = new StringBuilder();
		log.append("[LS:"); //$NON-NLS-1$
		log.append(wrapper.serverDefinition.label);
		log.append("]["); //$NON-NLS-1$
		log.append(params.getType());
		log.append("]\t"); //$NON-NLS-1$
		log.append(params.getMessage());

		switch(params.getType()) {
		case Error:
			LanguageServerPlugin.logError(log.toString(), null);
			break;
		case Warning:
			LanguageServerPlugin.logWarning(log.toString());
			break;
		default:
			LanguageServerPlugin.logInfo(log.toString());
		}
	}

	public static void showMessage(String title, MessageParams params) {
		Display.getDefault().asyncExec(() -> {
			final var notification = new LSPNotification(String.format("LSP (%s)", title), //$NON-NLS-1$
					params);
			notification.open();
		});
	}

	public static CompletableFuture<MessageActionItem> showMessageRequest(LanguageServerWrapper wrapper, ShowMessageRequestParams params) {
		String[] options = params.getActions().stream().map(MessageActionItem::getTitle).toArray(String[]::new);
		final var future = new CompletableFuture<MessageActionItem>();

		Display.getDefault().asyncExec(() -> {
			final var dialog = new MessageDialog(UI.getActiveShell(), wrapper.serverDefinition.label,
					null, params.getMessage(), MessageDialog.INFORMATION, 0, options);
			final var result = new MessageActionItem();
			int dialogResult = dialog.open();
			if (dialogResult != SWT.DEFAULT) { // the dialog was not dismissed without pressing a button (ESC key, close box, etc.)
				result.setTitle(options[dialogResult]);
			}
			// according to https://github.com/Microsoft/language-server-protocol/issues/230
			// the right thing to do is to return the res
			future.complete(result);
		});
		return future;
	}

}
