/*******************************************************************************
 * Copyright (c) 2016, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.) - hyperlink range detection
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *  Sebastian Thomschke (Vegard IT GmbH) - avoid UI freeze when working with slow language servers
 *******************************************************************************/
package org.eclipse.lsp4e.operations.declaration;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.DocumentOffsetAsyncCache;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

public class OpenDeclarationHyperlinkDetector extends AbstractHyperlinkDetector {

	private static final long UI_BLOCKING_BUDGET_MS = 200;

	@NonNullByDefault({})
	private static record LabeledLocations(String label,
			@Nullable Either<List<? extends Location>, List<? extends LocationLink>> locations) {
	}

	private static final DocumentOffsetAsyncCache<List<LSBasedHyperlink>> CACHE = new DocumentOffsetAsyncCache<>(
			Duration.ofSeconds(10));

	@Override
	public IHyperlink @Nullable [] detectHyperlinks(ITextViewer textViewer, IRegion region,
			boolean canShowMultipleHyperlinks) {
		final IDocument document = textViewer.getDocument();
		if (document == null) {
			return null;
		}
		TextDocumentPositionParams params;
		try {
			params = LSPEclipseUtils.toTextDocumentPosistionParams(region.getOffset(), document);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}

		// Normalize cache key to the start of the word to avoid cache misses when the
		// mouse moves within the same symbol.
		final int cacheKeyOffset = findWord(document, region).getOffset();

		final CompletableFuture<List<LSBasedHyperlink>> request = CACHE.computeIfAbsent(document, cacheKeyOffset, () -> {
			final var definitions = LanguageServers.forDocument(document)
					.withCapability(ServerCapabilities::getDefinitionProvider)
					.collectAll(ls -> ls.getTextDocumentService().definition(LSPEclipseUtils.toDefinitionParams(params))
							.thenApply(l -> new LabeledLocations(Messages.definitionHyperlinkLabel, l))
							.exceptionally(err -> new LabeledLocations(Messages.definitionHyperlinkLabel, null)));
			final var declarations = LanguageServers.forDocument(document)
					.withCapability(ServerCapabilities::getDeclarationProvider)
					.collectAll(ls -> ls.getTextDocumentService()
							.declaration(LSPEclipseUtils.toDeclarationParams(params))
							.thenApply(l -> new LabeledLocations(Messages.declarationHyperlinkLabel, l))
							.exceptionally(err -> new LabeledLocations(Messages.declarationHyperlinkLabel, null)));
			final var typeDefinitions = LanguageServers.forDocument(document)
					.withCapability(ServerCapabilities::getTypeDefinitionProvider)
					.collectAll(ls -> ls.getTextDocumentService()
							.typeDefinition(LSPEclipseUtils.toTypeDefinitionParams(params))
							.thenApply(l -> new LabeledLocations(Messages.typeDefinitionHyperlinkLabel, l))
							.exceptionally(err -> new LabeledLocations(Messages.typeDefinitionHyperlinkLabel, null)));
			final var implementations = LanguageServers.forDocument(document)
					.withCapability(ServerCapabilities::getImplementationProvider)
					.collectAll(ls -> ls.getTextDocumentService()
							.implementation(LSPEclipseUtils.toImplementationParams(params))
							.thenApply(l -> new LabeledLocations(Messages.implementationHyperlinkLabel, l))
							.exceptionally(err -> new LabeledLocations(Messages.implementationHyperlinkLabel, null)));

			final CompletableFuture<List<LabeledLocations>> combined = LanguageServers.addAll(
					LanguageServers.addAll(LanguageServers.addAll(definitions, declarations), typeDefinitions),
					implementations);
			return combined.thenApply(locations -> toHyperlinks(document, region, locations));
		});

		try {
			// Try to get a quick result within the UI budget; keep UI responsive.
			final List<LSBasedHyperlink> links = request.get(UI_BLOCKING_BUDGET_MS, TimeUnit.MILLISECONDS);
			return links.isEmpty() ? null : links.toArray(IHyperlink[]::new);
		} catch (final ExecutionException ex) {
			LanguageServerPlugin.logError(ex);
		} catch (final InterruptedException ex) {
			LanguageServerPlugin.logError(ex);
			Thread.currentThread().interrupt();
		} catch (final TimeoutException ex) {
			if (canShowMultipleHyperlinks) {
				return new IHyperlink[] { new DeferredOpenMultiDeclarationHyperlink(textViewer, document,
						findWord(document, region), request) };
			} else {
				final CompletableFuture<@Nullable IHyperlink> firstLink = request
						.thenApply(links -> !links.isEmpty() ? links.get(0) : null);
				return new IHyperlink[] { new DeferredOpenDeclarationHyperlink(textViewer, document,
						findWord(document, region), firstLink) };
			}
		}

		return null;
	}

	/**
	 * Returns a list of {@link LSBasedHyperlink} using the given LSP locations
	 *
	 * @param document
	 *            the document
	 * @param linkRegion
	 *            the region
	 * @param locations
	 *            the LSP locations
	 */
	private static List<LSBasedHyperlink> toHyperlinks(final IDocument doc, final IRegion region,
			final List<LabeledLocations> locations) {
		final var allLinks = new LinkedHashMap<Either<Location, LocationLink>, LSBasedHyperlink>();
		for (final LabeledLocations locs : locations) {
			final var either = locs.locations();
			if (either == null)
				continue;
			if (either.isLeft()) {
				either.getLeft().stream().filter(Objects::nonNull)
						.map(loc -> new LSBasedHyperlink(loc, findWord(doc, region), locs.label()))
						.forEach(h -> allLinks.putIfAbsent(h.getLocation(), h));
			} else {
				either.getRight().stream().filter(Objects::nonNull).map(
						locLink -> new LSBasedHyperlink(locLink, getSelectedRegion(doc, region, locLink), locs.label()))
						.forEach(h -> allLinks.putIfAbsent(h.getLocation(), h));
			}
		}
		return allLinks.values().stream().toList();
	}

	/**
	 * Returns the selection region, or if that fails , fallback to
	 * {@link #findWord(IDocument, IRegion)}
	 */
	private static IRegion getSelectedRegion(IDocument document, IRegion region, LocationLink locationLink) {
		Range originSelectionRange = locationLink.getOriginSelectionRange();
		if (originSelectionRange != null) {
			try {
				int offset = LSPEclipseUtils.toOffset(originSelectionRange.getStart(), document);
				int endOffset = LSPEclipseUtils.toOffset(originSelectionRange.getEnd(), document);
				return new Region(offset, endOffset - offset);
			} catch (BadLocationException e) {
				LanguageServerPlugin.logError(e.getMessage(), e);
			}
		}
		return findWord(document, region);
	}

	/**
	 * Fallback for missing range value (which can be used to highlight hyperlink)
	 * in LSP 'definition' response.
	 */
	private static IRegion findWord(IDocument document, IRegion region) {
		int start = -2;
		int end = -1;
		int offset = region.getOffset();

		try {

			int pos = offset;
			char c;

			while (pos >= 0 && pos < document.getLength()) {
				c = document.getChar(pos);
				if (!Character.isUnicodeIdentifierPart(c)) {
					break;
				}
				--pos;
			}

			start = pos;

			pos = offset;
			int length = document.getLength();

			while (pos < length) {
				c = document.getChar(pos);
				if (!Character.isUnicodeIdentifierPart(c))
					break;
				++pos;
			}

			end = pos;

		} catch (BadLocationException x) {
			LanguageServerPlugin.logWarning(x.getMessage(), x);
		}

		if (start >= -1 && end > -1) {
			if (start == offset && end == offset)
				return new Region(offset, 0);
			else if (start == offset)
				return new Region(start, end - start);
			else
				return new Region(start + 1, end - start - 1);
		}

		return region;
	}

}
