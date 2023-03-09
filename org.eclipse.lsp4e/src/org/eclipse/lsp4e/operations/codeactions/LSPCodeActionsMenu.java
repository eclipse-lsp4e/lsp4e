/*******************************************************************************
 * Copyright (c) 2016, 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.action.ContributionItem;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.menus.IWorkbenchContribution;
import org.eclipse.ui.progress.UIJob;
import org.eclipse.ui.services.IServiceLocator;
import org.eclipse.ui.texteditor.ITextEditor;

public class LSPCodeActionsMenu extends ContributionItem implements IWorkbenchContribution {

	private IDocument document;
	private Range range;

	@Override
	public void initialize(IServiceLocator serviceLocator) {
		IEditorPart editor = UI.getActiveTextEditor();
		if (editor != null) {
			final var textEditor = (ITextEditor) editor;
			IDocument document = LSPEclipseUtils.getDocument(textEditor);
			if (document == null) {
				return;
			}
			this.document = document;
			final var selection = (ITextSelection) textEditor.getSelectionProvider().getSelection();
			try {
				this.range = new Range(LSPEclipseUtils.toPosition(selection.getOffset(), document),
						LSPEclipseUtils.toPosition(selection.getOffset() + selection.getLength(), document));
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
	}

	@Override
	public void fill(final Menu menu, int index) {
		final var item = new MenuItem(menu, SWT.NONE, index);
		item.setEnabled(false);

		item.setText(Messages.computing);
		final @NonNull IDocument document = this.document;
		final URI fileUri = LSPEclipseUtils.toUri(document);

		final var context = new CodeActionContext(Collections.emptyList());
		final var params = new CodeActionParams();
		params.setTextDocument(LSPEclipseUtils.toTextDocumentIdentifier(fileUri));
		params.setRange(this.range);
		params.setContext(context);

		final @NonNull List<@NonNull CompletableFuture<List<Either<Command, CodeAction>>>> actions
			= LanguageServers.forDocument(document).withFilter(LSPCodeActionMarkerResolution::providesCodeActions)
					.computeAll((w, ls) -> ls.getTextDocumentService().codeAction(params)
					.whenComplete((codeActions, t) -> scheduleMenuUpdate(menu, item, index, document, w, t, codeActions)));

		if (actions.isEmpty()) {
			item.setText(Messages.notImplemented);
			return;
		}

		super.fill(menu, index);
	}

	private void scheduleMenuUpdate(final Menu menu, final MenuItem placeHolder, final int index, final IDocument document, final LanguageServerWrapper wrapper, final Throwable u, final List<Either<Command, CodeAction>> codeActions) {
		final var job = new UIJob(menu.getDisplay(), Messages.updateCodeActions_menu) {
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				if (u != null) {
					final var item = new MenuItem(menu, SWT.NONE, index);
					item.setText(u.getMessage());
					item.setImage(LSPImages.getSharedImage(ISharedImages.IMG_DEC_FIELD_ERROR));
					item.setEnabled(false);
				} else if (codeActions != null) {
					for (Either<Command, CodeAction> command : codeActions) {
						if (command != null) {
							final var item = new MenuItem(menu, SWT.NONE, index);
							final var proposal = new CodeActionCompletionProposal(command, wrapper);
							item.setText(proposal.getDisplayString());
							item.addSelectionListener(new SelectionAdapter() {
								@Override
								public void widgetSelected(SelectionEvent e) {
									proposal.apply(document);
								}
							});
						}
					}
				}
				if (menu.getItemCount() == 1) {
					placeHolder.setText(Messages.codeActions_emptyMenu);
				} else {
					placeHolder.dispose();
				}
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
}
