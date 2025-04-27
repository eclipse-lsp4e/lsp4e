/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.format;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentCommand;
import org.eclipse.jface.text.IAutoEditStrategy;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.operations.format.LSPFormatter;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class DocumentFormatOnTypeAutoEditStrategy implements IAutoEditStrategy {

	@Override
	public void customizeDocumentCommand(@Nullable IDocument document, @Nullable DocumentCommand command) {
		if (document == null || command == null || command.getCommandCount() > 1 || command.text.isEmpty()) {
			return;
		}
		var executor = LanguageServers.forDocument(document)
			.withCapability(capabilities -> Either.forLeft(capabilities.getDocumentOnTypeFormattingProvider() != null
				&& (command.text.equals(capabilities.getDocumentOnTypeFormattingProvider().getFirstTriggerCharacter()) ||
					(capabilities.getDocumentOnTypeFormattingProvider().getMoreTriggerCharacter() != null && capabilities.getDocumentOnTypeFormattingProvider().getMoreTriggerCharacter().contains(command.text)))));
		if (!executor.anyMatching()) {
			return;
		}
		FormattingOptions formattingOptions = LSPFormatter.getFormatOptions();
		TextDocumentIdentifier textDocumentIdentifier = LSPEclipseUtils.toTextDocumentIdentifier(document);
		if (textDocumentIdentifier == null) {
			return;
		}
		try {
			DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocumentIdentifier, formattingOptions, LSPEclipseUtils.toPosition(command.offset, document), command.text);
			executor.computeFirst(ls -> ls.getTextDocumentService().onTypeFormatting(params)).join()
				.ifPresent(edits -> {
					edits.forEach(edit -> {
						try {
							int fromOffset = LSPEclipseUtils.toOffset(edit.getRange().getStart(), document);
							int toOffset = LSPEclipseUtils.toOffset(edit.getRange().getEnd(), document);
							if (fromOffset == command.offset) {
								command.text += edit.getNewText();
							} else {
								command.addCommand(fromOffset, toOffset - fromOffset, edit.getNewText(), null);
							}
						} catch (BadLocationException ex) {
							LanguageServerPlugin.logError(ex);
						}
					});
				});
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
	}

}
