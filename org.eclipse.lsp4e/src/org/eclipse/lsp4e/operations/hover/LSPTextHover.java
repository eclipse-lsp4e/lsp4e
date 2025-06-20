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
 *  Lucas Bullen (Red Hat Inc.) - Bug 508458 - Add support for codelens
 *  Angelo Zerr <angelo.zerr@gmail.com> - Bug 525602 - LSBasedHover must check if LS have codelens capability
 *  Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *  Alex Boyko (VMware) - [Bug 566164] fix for NPE in LSPTextHover
 *******************************************************************************/
package org.eclipse.lsp4e.operations.hover;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextHoverExtension;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.EditorsUI;

/**
 * LSP implementation of {@link org.eclipse.jface.text.ITextHover}
 *
 */
@SuppressWarnings("restriction")
public class LSPTextHover implements ITextHover, ITextHoverExtension {

	private static final int GET_TIMEOUT_MS = 1000;

	private @Nullable IRegion lastRegion;
	private @Nullable ITextViewer lastViewer;
	private @Nullable CompletableFuture<List<Hover>> request;

	@Override
	public @Nullable String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		CompletableFuture<@Nullable String> hoverInfoFuture = getHoverInfoFuture(textViewer, hoverRegion);
		try {
			return hoverInfoFuture.get(GET_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (ExecutionException e) {
			LanguageServerPlugin.logError(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (TimeoutException e) {
			LanguageServerPlugin.logWarning("Could not get hover information due to timeout after " + GET_TIMEOUT_MS + " milliseconds", e); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return null;
	}

	public CompletableFuture<@Nullable String> getHoverInfoFuture(ITextViewer textViewer, IRegion hoverRegion) {
		if (this.request == null || !textViewer.equals(this.lastViewer) || !hoverRegion.equals(this.lastRegion)) {
			initiateHoverRequest(textViewer, hoverRegion.getOffset());
		}
		return castNonNull(request).thenApply(hoversList -> {
			String result = hoversList.stream()
				.filter(Objects::nonNull)
				.map(LSPTextHover::getHoverString)
				.filter(Objects::nonNull)
				.collect(Collectors.joining("\n\n")) //$NON-NLS-1$
				.trim();
			if (!result.isEmpty()) {
				Parser parser = Parser.builder().build();
				Node document = parser.parse(result);
				HtmlRenderer renderer = HtmlRenderer.builder().build();
				return renderer.render(document);
			} else {
				return null;
			}
		});
	}

	protected static @Nullable String getHoverString(Hover hover) {
		Either<List<Either<String, MarkedString>>, MarkupContent> hoverContent = hover.getContents();
		if (hoverContent.isLeft()) {
			List<Either<String, MarkedString>> contents = hoverContent.getLeft();
			if (contents.isEmpty()) {
				return null;
			}
			return contents.stream().map(content -> {
				if (content.isLeft()) {
					return content.getLeft();
				} else if (content.isRight()) {
					MarkedString markedString = content.getRight();
					// TODO this won't work fully until markup parser will support syntax
					// highlighting but will help display
					// strings with language tags, e.g. without it things after <?php tag aren't
					// displayed
					if (markedString.getLanguage() != null && !markedString.getLanguage().isEmpty()) {
						return String.format("```%s%n%s%n```", markedString.getLanguage(), markedString.getValue()); //$NON-NLS-1$
					} else {
						return markedString.getValue();
					}
				} else {
					return ""; //$NON-NLS-1$
				}
			}).filter(((Predicate<String>) String::isEmpty).negate()).collect(Collectors.joining("\n\n")); //$NON-NLS-1$ )
		} else {
			return hoverContent.getRight().getValue();
		}
	}

	@Override
	public @Nullable IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		final var lastRegion = this.lastRegion;
		if (this.request == null || lastRegion == null || !textViewer.equals(this.lastViewer)
				|| offset < lastRegion.getOffset() || offset > lastRegion.getOffset() + lastRegion.getLength()) {
			initiateHoverRequest(textViewer, offset);
		}
		try {
			final IDocument document = textViewer.getDocument();
			if (document == null) {
				return null;
			}
			final var oneHoverAtLeast = new boolean[] { false };
			final var regionStartOffset = new int[] { 0 };
			final var regionEndOffset = new int[] { document.getLength() };
			castNonNull(this.request).get(GET_TIMEOUT_MS, TimeUnit.MILLISECONDS).stream()
				.filter(Objects::nonNull)
				.map(Hover::getRange)
				.filter(Objects::nonNull)
				.forEach(range -> {
					try {
							regionStartOffset[0] = Math.max(regionStartOffset[0],
									LSPEclipseUtils.toOffset(range.getStart(), document));
							regionEndOffset[0] = Math.min(regionEndOffset[0],
									LSPEclipseUtils.toOffset(range.getEnd(), document));
						oneHoverAtLeast[0] = true;
					} catch (BadLocationException e) {
						LanguageServerPlugin.logError(e);
					}
				});
			if (oneHoverAtLeast[0]) {
				this.lastRegion = new Region(regionStartOffset[0], regionEndOffset[0] - regionStartOffset[0]);
				return this.lastRegion;
			}
		} catch (ExecutionException e) {
			LanguageServerPlugin.logError(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (TimeoutException e) {
			LanguageServerPlugin.logWarning("Could not get hover region due to timeout after " + GET_TIMEOUT_MS + " milliseconds", e); //$NON-NLS-1$ //$NON-NLS-2$
		}
		this.lastRegion = new Region(offset, 0);
		return this.lastRegion;
	}

	/**
	 * Initialize hover requests with hover (if available) and codelens (if
	 * available).
	 *
	 * @param viewer
	 *            the text viewer.
	 * @param offset
	 *            the hovered offset.
	 */
	private void initiateHoverRequest(ITextViewer viewer, int offset) {
		final IDocument document = viewer.getDocument();
		if (document == null) {
			return;
		}
		this.lastViewer = viewer;
		try {
			HoverParams params = LSPEclipseUtils.toHoverParams(offset, document);

			this.request = LanguageServers.forDocument(document)
				.withCapability(ServerCapabilities::getHoverProvider)
				.collectAll(server -> server.getTextDocumentService().hover(params));
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	@Override
	public @Nullable IInformationControlCreator getHoverControlCreator() {
		return new AbstractReusableInformationControlCreator() {
			@Override
			protected IInformationControl doCreateInformationControl(Shell parent) {
				if (BrowserInformationControl.isAvailable(parent)) {
					return new FocusableBrowserInformationControl(parent);
				} else {
					return new DefaultInformationControl(parent, EditorsUI.getTooltipAffordanceString());
				}
			}
		};
	}

}
