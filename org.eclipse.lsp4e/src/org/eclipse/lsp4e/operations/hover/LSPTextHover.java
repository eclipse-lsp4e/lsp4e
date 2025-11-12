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
 *  Sebastian Thomschke (Vegard IT GmbH) - Prevent UI freezes through non-blocking hover rendering
 *******************************************************************************/
package org.eclipse.lsp4e.operations.hover;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.util.List;
import java.util.NoSuchElementException;
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
import org.eclipse.jface.text.ITextHoverExtension2;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.editors.text.EditorsUI;

/**
 * LSP implementation of {@link org.eclipse.jface.text.ITextHover}
 */
@SuppressWarnings("restriction")
public class LSPTextHover implements ITextHover, ITextHoverExtension, ITextHoverExtension2 {

	private static final int GET_HOVER_REGION_TIMEOUT_MS = 100;

	private @Nullable IRegion lastRegion;
	private @Nullable ITextViewer lastViewer;
	private @Nullable CompletableFuture<List<Hover>> request;
	private @Nullable CompletableFuture<@Nullable String> hoverInfoFuture;

	@Override
	public @Nullable String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		// Non-blocking: only return immediately available content.
		final var hoverInfoRequest_ = this.hoverInfoFuture = getHoverInfoFuture(textViewer, hoverRegion);
		if (hoverInfoRequest_.isDone()) {
			try {
				return hoverInfoRequest_.getNow(null);
			} catch (Exception e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return null;
	}

	@Override
	public @Nullable Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
		final var hoverInfoRequest_ = this.hoverInfoFuture = getHoverInfoFuture(textViewer, hoverRegion);
		final String placeholder = "<html><body>Loading…</body></html>"; //$NON-NLS-1$
		return new AsyncHtmlHoverInput(hoverInfoRequest_, placeholder);
	}

	public CompletableFuture<@Nullable String> getHoverInfoFuture(ITextViewer textViewer, IRegion hoverRegion) {
		if (this.request == null || !textViewer.equals(this.lastViewer) || !hoverRegion.equals(this.lastRegion)) {
			initiateHoverRequest(textViewer, hoverRegion.getOffset());
		}
		return castNonNull(request).thenApply(hoversList -> {
			String result = hoversList.stream() //
					.filter(Objects::nonNull) //
					.map(LSPTextHover::getHoverString) //
					.filter(Objects::nonNull) //
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

		final IDocument document = textViewer.getDocument();
		if (document == null) {
			return null;
		}

		try {
			// Wait shortly for hover region result, fallback to heuristics if LS is laggy
			Range range = castNonNull(this.request).get(GET_HOVER_REGION_TIMEOUT_MS, TimeUnit.MILLISECONDS).stream() //
					.filter(Objects::nonNull) //
					.map(Hover::getRange) //
					.filter(Objects::nonNull) //
					.reduce((first, second) -> second) //
					.get();
			int regionStartOffset = Math.max(0,
					LSPEclipseUtils.toOffset(range.getStart(), document));
			int regionEndOffset = Math.min(document.getLength(),
					LSPEclipseUtils.toOffset(range.getEnd(), document));
			return this.lastRegion = new Region(regionStartOffset, regionEndOffset - regionStartOffset);
		} catch (ExecutionException | BadLocationException e) {
			LanguageServerPlugin.logError("Cannot get hover region for offset " + offset, e); //$NON-NLS-1$
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (NoSuchElementException | TimeoutException e) {
			// Fallback to heuristic region without blocking.
		}

		return this.lastRegion = computeHeuristicRegion(document, offset);
	}

	private static Region computeHeuristicRegion(final IDocument document, final int offset) {
		try {
			final int length = document.getLength();
			if (offset < 0 || offset > length) {
				return new Region(Math.max(0, Math.min(offset, length)), 0);
			}
			int start = offset;
			int end = offset;
			while (start > 0 && isWordPart(document.getChar(start - 1))) {
				start--;
			}
			while (end < length && isWordPart(document.getChar(end))) {
				end++;
			}
			return new Region(start, Math.max(0, end - start));
		} catch (final BadLocationException ex) {
			return new Region(offset, 0);
		}
	}

	private static boolean isWordPart(char c) {
		return Character.isLetterOrDigit(c) || c == '_' || c == '$';
	}

	/**
	 * Cancel the last call of 'hover'.
	 */
	private void cancel() {
		if (request != null) {
			request.cancel(true);
			request = null;
		}
		if (hoverInfoFuture != null) {
			hoverInfoFuture.cancel(true);
			hoverInfoFuture = null;
		}
	}

	/**
	 * Initialize hover requests with hover (if available).
	 *
	 * @param viewer
	 *            the text viewer.
	 * @param offset
	 *            the hovered offset.
	 */
	private void initiateHoverRequest(ITextViewer viewer, int offset) {
		cancel();
		final IDocument document = viewer.getDocument();
		if (document == null) {
			return;
		}
		this.lastViewer = viewer;
		try {
			HoverParams params = LSPEclipseUtils.toHoverParams(offset, document);

			this.request = LanguageServers.forDocument(document) //
					.withCapability(ServerCapabilities::getHoverProvider) //
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
