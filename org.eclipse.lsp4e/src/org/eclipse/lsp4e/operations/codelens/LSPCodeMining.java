/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codelens;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.LineHeaderCodeMining;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.command.CommandExecutor;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.swt.events.MouseEvent;

public class LSPCodeMining extends LineHeaderCodeMining {
	private CodeLens codeLens;

	private final LanguageServerWrapper languageServerWrapper;
	private final IDocument document;

	public LSPCodeMining(CodeLens codeLens, IDocument document, LanguageServerWrapper languageServerWrapper,
			CodeLensProvider provider) throws BadLocationException {
		super(codeLens.getRange().getStart().getLine(), document, provider, null);
		this.codeLens = codeLens;
		this.languageServerWrapper = languageServerWrapper;
		this.document = document;
		setLabel(getCodeLensString(codeLens));
	}

	protected static @Nullable String getCodeLensString(CodeLens codeLens) {
		Command command = codeLens.getCommand();
		if (command == null || command.getTitle().isEmpty()) {
			return null;
		}
		return command.getTitle();
	}

	@Override
	protected CompletableFuture<@Nullable Void> doResolve(ITextViewer viewer, IProgressMonitor monitor) {
		return languageServerWrapper.getServerCapabilitiesAsync().thenCompose(capabilities -> {
			if (capabilities == null) {
				return CompletableFuture.completedFuture(null);
			}
			final Boolean resolveProvider = capabilities.getCodeLensProvider().getResolveProvider();
			if (resolveProvider == null || !resolveProvider) {
				return CompletableFuture.completedFuture(null);
			}

			return languageServerWrapper.execute(languageServer -> languageServer.getTextDocumentService().resolveCodeLens(this.codeLens))
					.thenAccept(resolvedCodeLens -> {
						if (resolvedCodeLens != null) {
							codeLens = resolvedCodeLens;
							setLabel(getCodeLensString(resolvedCodeLens));
						}
					});
		});
	}

	@Override
	public final @Nullable Consumer<MouseEvent> getAction() {
		final Command command = codeLens.getCommand();
		if(command != null && command.getCommand() != null && !command.getCommand().isEmpty()) {
			return this::performAction;
		} else {
			return null;
		}
	}

	private void performAction(MouseEvent mouseEvent) {
		final ServerCapabilities serverCapabilities = languageServerWrapper.getServerCapabilities();
		ExecuteCommandOptions provider = serverCapabilities == null ? null : serverCapabilities.getExecuteCommandProvider();
		Command command = codeLens.getCommand();
		if (provider != null && provider.getCommands().contains(command.getCommand())) {
			languageServerWrapper.execute(ls -> ls.getWorkspaceService()
					.executeCommand(new ExecuteCommandParams(command.getCommand(), command.getArguments())));
		} else  {
			CommandExecutor.executeCommandClientSide(command, document);
		}
	}

}
