/*******************************************************************************
 * Copyright (c) 2018, 2021 Angelo Zerr and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - [code mining] Support 'textDocument/documentColor' with CodeMining - Bug 533322
 */
package org.eclipse.lsp4e.operations.color;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.AbstractLSPCodeMiningProvider;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Display;

/**
 * Consume the 'textDocument/documentColor' request to decorate color references
 * in the editor.
 */
public class DocumentColorProvider extends AbstractLSPCodeMiningProvider {

	private final Map<RGBA, Color> colorTable;

	public DocumentColorProvider() {
		colorTable = new HashMap<>();
	}

	@Override
	protected @Nullable CompletableFuture<List<? extends ICodeMining>> doProvideCodeMinings(IDocument document,
			TextDocumentIdentifier docId) {
		final var param = new DocumentColorParams(docId);
		return LanguageServers.forDocument(document)
			.withCapability(ServerCapabilities::getColorProvider)
			.collectAll(
				// Need to do some of the result processing inside the function we supply to collectAll(...)
				// as need the LSW to construct the ColorInformationMining
				(wrapper, ls) -> ls.getTextDocumentService().documentColor(param)
							.thenApply(colors -> LanguageServers.streamSafely(colors)
									.map(color -> toMining(color, document, docId, wrapper))))
			.thenApply(res -> res.stream().flatMap(Function.identity()).filter(Objects::nonNull).toList());
	}

	private @Nullable ColorInformationMining toMining(ColorInformation color, IDocument document, TextDocumentIdentifier textDocumentIdentifier, LanguageServerWrapper wrapper) {
		try {
			return new ColorInformationMining(color, document,
					textDocumentIdentifier, wrapper,
					DocumentColorProvider.this);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
		return null;
	}

	/**
	 * Returns the color from the given rgba.
	 *
	 * @param rgba
	 *            the rgba declaration
	 * @param display
	 *            the display to use to create a color instance
	 * @return the color from the given rgba.
	 */
	public Color getColor(RGBA rgba, Display display) {
		return colorTable.computeIfAbsent(rgba, key -> new Color(display, rgba));
	}
}
