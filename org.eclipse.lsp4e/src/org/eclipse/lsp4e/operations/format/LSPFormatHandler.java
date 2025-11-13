/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *  Sebastian Thomschke (Vegard IT GmbH)
 *******************************************************************************/
package org.eclipse.lsp4e.operations.format;

import java.util.ConcurrentModificationException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ServerMessageHandler;
import org.eclipse.lsp4e.internal.LSPDocumentAbstractHandler;
import org.eclipse.lsp4e.internal.ResourceUtil;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorExtension;

public class LSPFormatHandler extends LSPDocumentAbstractHandler {

	private final LSPFormatter formatter = new LSPFormatter();

	static boolean setWritable(IFile file) {
		final var shell = UI.getActiveShell();

		if (!MessageDialog.openQuestion(shell, Messages.LSPFormatHandler_ReadOnlyEditor_title,
				NLS.bind(Messages.LSPFormatHandler_ReadOnlyEditor_fileReadonly, file.getLocation().toFile()))) {
			return false; // user aborted
		}

		try {
			ResourceUtil.setWritable(file);
			return true;
		} catch (final CoreException ex) {
			MessageDialog.openError(UI.getActiveShell(), Messages.LSPFormatHandler_ReadOnlyEditor_title,
					NLS.bind(Messages.LSPFormatHandler_ReadOnlyEditor_makingWritableFailed, file.getLocation().toFile(),
							ex.getStatus().getMessage()));
		}
		return false;
	}

	@Override
	protected void execute(ExecutionEvent event, ITextEditor textEditor) {
		// If the editor input is read-only, prompt to make writable
		final var editorExt = Adapters.adapt(textEditor, ITextEditorExtension.class);
		if (editorExt != null && editorExt.isEditorInputReadOnly()) {
			final IEditorInput input = textEditor.getEditorInput();
			final IFile file = input instanceof FileEditorInput fileInput ? fileInput.getFile() : null;
			if (file == null) {
				MessageDialog.openInformation(HandlerUtil.getActiveShell(event),
						Messages.LSPFormatHandler_ReadOnlyEditor_title,
						NLS.bind(Messages.LSPFormatHandler_ReadOnlyEditor_inputReadonly, input.getToolTipText()));
				return;
			}

			if (!setWritable(file))
				return;
		}

		final ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof final ITextSelection textSelection && !textSelection.isEmpty()) {

			final IDocument doc = LSPEclipseUtils.getDocument(textEditor);
			if (doc == null)
				return;

			try {
				formatter.requestFormatting(doc, textSelection)
					.thenAcceptAsync(result -> {
						result.ifPresent(edits -> {
							try {
								edits.apply();
							} catch (final ConcurrentModificationException ex) {
								ServerMessageHandler.showMessage(Messages.LSPFormatHandler_DiscardedFormat,
										new MessageParams(MessageType.Error,
												Messages.LSPFormatHandler_DiscardedFormatResponse));
							} catch (BadLocationException e) {
								LanguageServerPlugin.logError(e);
							}
						});
					}, UI.getDisplay());
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	@Override
	public void setEnabled(@Nullable Object evaluationContext) {
		setEnabled(LSPFormatter::supportsFormatting, this::hasSelection);
	}
}
