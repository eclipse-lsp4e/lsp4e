/*******************************************************************************
 * Copyright (c) 2022 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *                              - [Bug 528848] Formatting Request should include FormattingOptions
 *******************************************************************************/
package org.eclipse.lsp4e.operations.format;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.VersionedEdits;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;

public class LSPFormatter {
	public CompletableFuture<Optional<VersionedEdits>> requestFormatting(IDocument document, ITextSelection textSelection) throws BadLocationException {
		URI uri = LSPEclipseUtils.toUri(document);
		if (uri == null) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document).withFilter(LSPFormatter::supportsFormatting);
		FormattingOptions formatOptions = getFormatOptions();
		final var docId = new TextDocumentIdentifier(uri.toString());

		DocumentRangeFormattingParams rangeParams = getRangeFormattingParams(document, textSelection, formatOptions,
				docId);

		DocumentFormattingParams params = getFullFormatParams(formatOptions, docId);

		// **NOTE:** We let LanguageServers.computeFirst() see the *raw* edit lists so that servers which
		// advertise formatting but return no edits (empty list) are treated as "no result" and formatting
		// can fall through to the next server (e.g. Vue LS after TS LS on .vue files).
		long modificationStamp = DocumentUtil.getDocumentModificationStamp(document);
		return executor.computeFirst((w, ls) -> w.getServerCapabilitiesAsync().thenCompose(capabilities -> {
			if (isDocumentRangeFormattingSupported(capabilities) && (textSelection.getLength() > 0 || !isDocumentFormattingSupported(capabilities))) {
				return (CompletableFuture<@Nullable List<? extends TextEdit>>) ls.getTextDocumentService()
						.rangeFormatting(rangeParams);
			} else if (isDocumentFormattingSupported(capabilities)) {
				return (CompletableFuture<@Nullable List<? extends TextEdit>>) ls.getTextDocumentService()
						.formatting(params);
			}
			return CompletableFuture.completedFuture(null);
		})).thenApply(
				optionalEdits -> optionalEdits.map(edits -> new VersionedEdits(modificationStamp, edits, document)));
	}

	public static DocumentFormattingParams getFullFormatParams(FormattingOptions formatOptions,
			TextDocumentIdentifier docId) {
		final var params = new DocumentFormattingParams();
		params.setTextDocument(docId);
		params.setOptions(formatOptions);
		return params;
	}

	public static DocumentRangeFormattingParams getRangeFormattingParams(IDocument document, ITextSelection textSelection,
			FormattingOptions formatOptions, TextDocumentIdentifier docId) throws BadLocationException {
		final var rangeParams = new DocumentRangeFormattingParams();
		rangeParams.setTextDocument(docId);
		rangeParams.setOptions(formatOptions);
		final boolean isFullFormat = textSelection.isEmpty() || textSelection.getLength() == 0;
		final int startAt = isFullFormat ? 0 : textSelection.getOffset();
		final int endAt = isFullFormat ? document.getLength() : startAt + textSelection.getLength();
		rangeParams.setRange(LSPEclipseUtils.toRange(startAt, endAt, document));
		return rangeParams;
	}

	public static FormattingOptions getFormatOptions() {
		IPreferenceStore store = EditorsUI.getPreferenceStore();
		int tabWidth = store.getInt(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_TAB_WIDTH);
		boolean insertSpaces = store.getBoolean(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_SPACES_FOR_TABS);
		return new FormattingOptions(tabWidth, insertSpaces);
	}

	public static boolean isDocumentRangeFormattingSupported(ServerCapabilities capabilities) {
		return LSPEclipseUtils.hasCapability(capabilities.getDocumentRangeFormattingProvider());
	}

	public static boolean isDocumentFormattingSupported(ServerCapabilities capabilities) {
		return LSPEclipseUtils.hasCapability(capabilities.getDocumentFormattingProvider());
	}

	public static boolean supportsFormatting(ServerCapabilities capabilities) {
		return isDocumentFormattingSupported(capabilities)
				|| isDocumentRangeFormattingSupported(capabilities);
	}

}
