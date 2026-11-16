/*******************************************************************************
 * Copyright (c) 2022-24 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlayhint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.LineContentCodeMining;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.command.CommandExecutor;
import org.eclipse.lsp4e.internal.NullSafetyHelper;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintRegistrationOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;

public class LSPLineContentCodeMining extends LineContentCodeMining {

	private InlayHint inlayHint;
	private final LanguageServerWrapper wrapper;
	private final IDocument document;

	private @Nullable Point location;
	private FontData @Nullable [] fontData;

	public LSPLineContentCodeMining(InlayHint inlayHint, IDocument document,
			LanguageServerWrapper languageServerWrapper, InlayHintProvider provider) throws BadLocationException {
		super(toPosition(inlayHint.getPosition(), document), isAfterPosition(inlayHint.getPosition(), document),
				provider);
		this.inlayHint = inlayHint;
		this.wrapper = languageServerWrapper;
		this.document = document;
		setLabel(getInlayHintString(inlayHint));
	}

	/**
	 * Tells if the hint belongs to the text before it (for example {@code x: i32})
	 * or to the text after it (for example a parameter name {@code foo(a: 1)}). For
	 * a hint that belongs to the text before it, the caret can stop between that
	 * text and the hint.
	 * <p>
	 * This uses a similar rule as VS Code (see {@code InlayHintsFragments} in
	 * {@code inlayHints.ts}), because language servers are made to work with
	 * VS Code. VS Code attaches the hint to the word at the hint position. If no
	 * word touches the position, VS Code attaches the hint to the token before it,
	 * unless the token at the position is longer than one character. LSP4E has no
	 * tokens, so it finds only some of these tokens: a number, a string literal,
	 * {@code ...} and {@code ::}. In the future, this logic could use TM models of
	 * TM4E and have language specific rules (in the same way that VS Code does).
	 */
	static boolean isAfterPosition(Position position, IDocument document) {
		try {
			int offset = LSPEclipseUtils.toOffset(position, document);
			IRegion line = document.getLineInformationOfOffset(offset);
			int lineStart = line.getOffset();
			int lineEnd = lineStart + line.getLength();
			if (offset > lineStart && isWordCharacter(document.getChar(offset - 1))) {
				return true; // a word ends at the hint, for example x|: i32
			}
			if (offset >= lineEnd) {
				// an empty line, or a hint after the last token, for example }| // main
				return offset > lineStart;
			}
			String rest = document.get(offset, lineEnd - offset);
			if (isWordCharacter(rest.charAt(0))) {
				return false; // a word starts at the hint, for example foo(|a: 1)
			}
			return offset > lineStart && !LONG_TOKEN_START.matcher(rest).lookingAt();
		} catch (BadLocationException e) {
			return false;
		}
	}

	/**
	 * The default word definition of VS Code: all characters except white space and
	 * the separators below.
	 */
	private static boolean isWordCharacter(char c) {
		return !Character.isWhitespace(c) && !Character.isSpaceChar(c)
				&& "`~!@#$%^&*()-=+[{]}\\|;:'\",.<>/?".indexOf(c) < 0; //$NON-NLS-1$
	}

	/**
	 * The start of a token that is longer than one character and does not start
	 * with a word character: a number such as {@code -1.5} or {@code .5} (as in the
	 * VS Code word definition), a string literal, {@code ...} or {@code ::}.
	 */
	private static final Pattern LONG_TOKEN_START = Pattern.compile("-?\\d*\\.\\d|[@$]?[\"'`]|\\.\\.\\.|::"); //$NON-NLS-1$

	@Override
	public void setLabel(final @Nullable String label) {
		if (label == null) {
			super.setLabel(label);
		} else {
			StringBuilder sb = new StringBuilder();
			if (Boolean.TRUE.equals(inlayHint.getPaddingLeft())) {
				sb.append(' ');
			}
			sb.append(label);
			if (Boolean.TRUE.equals(inlayHint.getPaddingRight())) {
				sb.append(' ');
			}
			super.setLabel(sb.toString());
		}
	}

	protected static @Nullable String getInlayHintString(InlayHint inlayHint) {
		return inlayHint.getLabel().map(Function.identity(), parts -> parts == null //
				? null
				: parts.stream().map(InlayHintLabelPart::getValue).collect(Collectors.joining()));
	}

	@Override
	protected CompletableFuture<@Nullable Void> doResolve(ITextViewer viewer, IProgressMonitor monitor) {
		if (!wrapper.isActive()) // TODO is this check required? if so is it missing in LSPCodeMining.doResolve()?
			return CompletableFuture.completedFuture(null);

		return wrapper.getServerCapabilitiesAsync(LSPEclipseUtils.toUri(document)).thenCompose(capabilities -> {
			if (!canResolveInlayHint(capabilities)) {
				return CompletableFuture.completedFuture(null);
			}
			return wrapper.execute(
					ls -> ls.getTextDocumentService().resolveInlayHint(inlayHint).thenAcceptAsync(resolvedInlayHint -> {
						if (resolvedInlayHint != null) {
							inlayHint = resolvedInlayHint;
							setLabel(getInlayHintString(resolvedInlayHint));
						}
					}));
		});
	}

	private static boolean canResolveInlayHint(@Nullable ServerCapabilities capabilities) {
		if (capabilities == null)
			return false;
		Either<Boolean, InlayHintRegistrationOptions> inlayProvider = capabilities.getInlayHintProvider();
		if (inlayProvider != null && inlayProvider.isRight()) {
			InlayHintRegistrationOptions options = inlayProvider.getRight();
			return Boolean.TRUE.equals(options.getResolveProvider());
		}
		return false;
	}

	/**
	 * Returns the Eclipse position from the given LSP position.
	 *
	 * @param position
	 *            the LSP position to convert
	 * @param document
	 * @return the Eclipse position from the given LSP position.
	 *
	 * @throws BadLocationException
	 */
	private static org.eclipse.jface.text.Position toPosition(Position position, IDocument document)
			throws BadLocationException {
		int start = LSPEclipseUtils.toOffset(position, document);
		return new org.eclipse.jface.text.Position(start, 1);
	}

	@Override
	public final @Nullable Consumer<MouseEvent> getAction() {
		List<InlayHintLabelPart> parts = inlayHint.getLabel().map(l -> null, r -> r);
		boolean hasPartWithCommand = parts != null
				&& parts.stream().map(InlayHintLabelPart::getCommand).anyMatch(Objects::nonNull);
		boolean hasTextEdits = inlayHint.getTextEdits() != null && !inlayHint.getTextEdits().isEmpty();

		if (!(hasPartWithCommand || hasTextEdits)) {
			// Neither command nor text edits -> no action
			return null;
		}

		return evt -> {
			/*
			 * LSP does not define if commands or text edits take precedence. So for the
			 * sake of backwards compatibility, we first check for commands and then for
			 * text edits
			 */
			if (hasPartWithCommand && handlePartWithCommand(evt, NullSafetyHelper.castNonNull(parts))) {
				return;
			}
			if (hasTextEdits) {
				// Apply text edits
				try {
					LSPEclipseUtils.applyEdits(document, inlayHint.getTextEdits());
					// The text of the inlay hint is integrated into the document.
					// To avoid a brief moment where both the inserted text and the inlay hint are
					// rendered, we set an empty label for the hint.
					setLabel(""); //$NON-NLS-1$
				} catch (BadLocationException e) {
					// Location to modify document was no longer valid -> Ignore
				}
			}
		};
	}

	/**
	 * Returns true if the mouse event hit a label part with a command.
	 */
	private boolean handlePartWithCommand(MouseEvent evt, List<InlayHintLabelPart> parts) {
		// Figure out if the user clicked on a specific label part which has a command
		Optional<InlayHintLabelPart> clickedPart = findLabelPart(evt, NullSafetyHelper.castNonNull(parts));
		Optional<Command> commandOpt = clickedPart.map(InlayHintLabelPart::getCommand);
		if (commandOpt.isPresent()) {
			Command command = commandOpt.get();
			ServerCapabilities serverCapabilities = wrapper.getServerCapabilities();
			ExecuteCommandOptions provider = serverCapabilities == null ? null
					: serverCapabilities.getExecuteCommandProvider();
			String commandId = command.getCommand();
			if (provider != null && provider.getCommands().contains(commandId)) {
				LanguageServers.forDocument(document).computeAll((w, ls) -> {
					if (w == wrapper) {
						return ls.getWorkspaceService()
								.executeCommand(new ExecuteCommandParams(commandId, command.getArguments()));
					}
					return CompletableFuture.completedFuture(null);
				});
			} else {
				CommandExecutor.executeCommandClientSide(command, document);
			}
			return true;
		}
		return false;
	}

	private Optional<InlayHintLabelPart> findLabelPart(MouseEvent me, List<InlayHintLabelPart> labelParts) {
		if (labelParts.size() == 1) {
			return Optional.of(labelParts.get(0));
		}
		final var location = this.location;
		final var fontData = this.fontData;
		if (location != null && fontData != null) {
			Point relativeLocation = new Point(me.x - location.x, me.y - location.y);
			Display display = Display.getCurrent();
			Image image = null;
			GC gc = null;
			Font font = null;
			try {
				image = new Image(display, 1, 1);
				gc = new GC(image);
				font = new Font(display, fontData);
				gc.setFont(font);
				final var origin = new Point(0, 0);
				for (InlayHintLabelPart labelPart : labelParts) {
					Point size = gc.stringExtent(labelPart.getValue());
					final var bounds = new Rectangle(origin.x, origin.y, size.x, size.y);
					if (bounds.contains(relativeLocation)) {
						return Optional.of(labelPart);
					} else {
						origin.x += size.x;
					}
				}
			} finally {
				if (font != null && !font.isDisposed()) {
					font.dispose();
				}
				if (gc != null && !gc.isDisposed()) {
					gc.dispose();
				}
				if (image != null && !image.isDisposed()) {
					image.dispose();
				}
			}
		}
		return Optional.empty();
	}

	@Override
	public Point draw(GC gc, StyledText textWidget, Color color, int x, int y) {
		this.location = new Point(x, y);
		Point size = super.draw(gc, textWidget, color, x, y);
		this.fontData = gc.getFont().getFontData();
		return size;
	}

}
