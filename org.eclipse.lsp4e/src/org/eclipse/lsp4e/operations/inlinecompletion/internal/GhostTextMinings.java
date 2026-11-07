/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.codemining.LineContentCodeMining;
import org.eclipse.jface.text.codemining.LineHeaderCodeMining;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextLayout.MiningSpec;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.graphics.TextStyle;

/**
 * The code minings that draw ghost text. They draw with the editor font, so that the ghost text is as
 * wide as the text it previews, only into the editor showing the inline completion (the annotation
 * model may be shared with another editor on the same document), and their labels can be updated in
 * place while the user types through the ghost text.
 * <p>
 * The text that accepting moves to the end of the last line (see {@link MiningSpec#moved()}) is not
 * ghost text: it is drawn as the editor draws it, with its colors and font styles.
 */
final class GhostTextMinings {

	private static final char ELLIPSIS = '\u2026';

	interface GhostMining extends ICodeMining {
		/** Updates what the mining draws to the given specification of the same kind and position. */
		void update(MiningSpec spec);
	}

	private GhostTextMinings() {
	}

	static GhostMining create(MiningSpec spec, Position position, ICodeMiningProvider provider,
			InlineCompletionController controller) throws BadLocationException {
		return switch (spec.kind()) {
		case CONTENT -> new Content(position, provider, controller, spec);
		case HEADER -> new Header(position, provider, controller, spec);
		case END_OF_DOCUMENT -> new EndOfDocument(position, provider, controller, spec);
		};
	}

	/** Draws the first line of the ghost text at the caret. */
	static final class Content extends LineContentCodeMining implements GhostMining {
		private final InlineCompletionController controller;

		Content(Position position, ICodeMiningProvider provider, InlineCompletionController controller, MiningSpec spec) {
			super(position, true, provider);
			this.controller = controller;
			update(spec);
		}

		@Override
		public void update(MiningSpec spec) {
			setLabel(spec.firstRow());
		}

		@Override
		public Point draw(GC gc, StyledText textWidget, Color color, int x, int y) {
			if (!controller.isOwnWidget(textWidget)) {
				return new Point(0, 0);
			}
			gc.setFont(textWidget.getFont());
			final String text = getLabel();
			final String label = fitToWidth(gc, textWidget, text == null ? "" : text, x); //$NON-NLS-1$
			gc.drawString(label, x, y, true);
			controller.ghostPainted();
			final Point extent = gc.stringExtent(label);
			extent.y += textWidget.getLineSpacing();
			return extent;
		}
	}

	/** Draws the further lines of the ghost text above the next visible line. */
	static final class Header extends LineHeaderCodeMining implements GhostMining {
		private final InlineCompletionController controller;
		private MiningSpec spec;

		Header(Position position, ICodeMiningProvider provider, InlineCompletionController controller, MiningSpec spec)
				throws BadLocationException {
			super(position, provider, null);
			this.controller = controller;
			this.spec = spec;
			update(spec);
		}

		@Override
		public void update(MiningSpec spec) {
			this.spec = spec;
			// JFace makes room for as many lines as the label has
			setLabel(String.join("\n", spec.rows())); //$NON-NLS-1$
		}

		@Override
		public Point draw(GC gc, StyledText textWidget, Color color, int x, int y) {
			if (!controller.isOwnWidget(textWidget)) {
				return new Point(0, 0);
			}
			gc.setFont(textWidget.getFont());
			final MiningSpec drawn = spec;
			final List<String> rows = drawn.rows();
			final List<List<TextStyle>> movedStyles = movedStyles(controller, drawn);
			final var extent = new Point(0, 0);
			int rowY = y;
			for (int i = 0; i < rows.size(); i++) {
				final int width = drawRow(gc, textWidget, color, rows.get(i), movedStyles.get(i), x, rowY);
				final int height = gc.stringExtent(rows.get(i)).y + textWidget.getLineSpacing();
				extent.x = Math.max(extent.x, width);
				extent.y += height;
				rowY += height;
			}
			controller.ghostPainted();
			return extent;
		}
	}

	/**
	 * Draws ghost text at the end of the document: its first line at the end of the last line, its
	 * further lines below. JFace reserves no room below the last line, so the controller enlarges the
	 * editor's bottom margin while such ghost text is shown.
	 */
	static final class EndOfDocument extends LineContentCodeMining implements GhostMining {
		private final InlineCompletionController controller;
		private MiningSpec spec;
		private @Nullable StyledText widget;

		EndOfDocument(Position position, ICodeMiningProvider provider, InlineCompletionController controller, MiningSpec spec) {
			super(position, true, provider);
			this.controller = controller;
			this.spec = spec;
			update(spec);
		}

		@Override
		public void update(MiningSpec spec) {
			this.spec = spec;
			// the label must not be empty for the mining to be drawn
			setLabel(spec.firstRow().isEmpty() ? " " : spec.firstRow()); //$NON-NLS-1$
		}

		@Override
		public Point draw(GC gc, StyledText textWidget, Color color, int x, int y) {
			if (!controller.isOwnWidget(textWidget)) {
				return new Point(0, 0);
			}
			gc.setFont(textWidget.getFont());
			final MiningSpec drawn = spec;
			final List<String> rows = drawn.rows();
			final String first = fitToWidth(gc, textWidget, drawn.firstRow(), x);
			gc.drawString(first, x, y, true);
			final int lineHeight = textWidget.getLineHeight() + textWidget.getLineSpacing();
			final int left = textWidget.getLeftMargin() - textWidget.getHorizontalPixel();
			int rowY = y + lineHeight;
			final List<List<TextStyle>> movedStyles = movedStyles(controller, drawn);
			for (int i = 0; i < rows.size(); i++) {
				drawRow(gc, textWidget, color, rows.get(i), movedStyles.get(i), left, rowY);
				rowY += lineHeight;
			}
			widget = textWidget;
			// Only the last line's area is repainted when the mining is drawn, which clips the lines
			// below it: repaint from the last line down to the end of the ghost text.
			// (no paint reaches below the client area: asking for it would repaint without end)
			final Rectangle clipping = gc.getClipping();
			final int bottom = Math.min(rowY, textWidget.getClientArea().height);
			if (!rows.isEmpty() && clipping.y + clipping.height < bottom) {
				final int top = y;
				final int height = bottom - y;
				textWidget.getDisplay().asyncExec(() -> {
					if (!textWidget.isDisposed()) {
						textWidget.redraw(0, top, textWidget.getClientArea().width, height, false);
					}
				});
			}
			controller.ghostPainted();
			return new Point(gc.stringExtent(first).x, lineHeight);
		}

		@Override
		public void dispose() {
			super.dispose();
			// what was drawn below the last line is outside any line, so nothing else repaints it
			final StyledText drawnOn = widget;
			if (drawnOn != null && !drawnOn.isDisposed()) {
				drawnOn.getDisplay().asyncExec(() -> {
					if (!drawnOn.isDisposed()) {
						drawnOn.redraw();
					}
				});
			}
		}
	}

	/**
	 * Returns, for each row of the given specification, the styles of the moved text at its end: the
	 * moved text ends the last row and may start on rows above it.
	 */
	static List<List<TextStyle>> movedStyles(InlineCompletionController controller, MiningSpec spec) {
		final List<String> rows = spec.rows();
		final var result = new ArrayList<List<TextStyle>>(rows.size());
		final List<TextStyle> styles = spec.moved() > 0 ? controller.movedTextStyles(spec.moved(), spec.movedColumn())
				: List.of();
		int end = styles.size();
		for (int i = rows.size() - 1; i >= 0; i--) {
			final int start = Math.max(0, end - rows.get(i).length());
			result.add(styles.subList(start, end));
			end = start;
		}
		Collections.reverse(result);
		return result;
	}

	/**
	 * Draws a row below the anchor's line, the characters at its end that are the moved text with the
	 * given styles, and returns its width. With word wrap on, the row is cut to the width of the editor.
	 */
	static int drawRow(GC gc, StyledText textWidget, Color color, String row, List<TextStyle> movedStyles, int x, int y) {
		final String label = fitToWidth(gc, textWidget, row, x);
		final int moved = movedStyles.size();
		final int ghostLength = row.length() - moved;
		// cut, the row ends with an ellipsis drawn as ghost text
		final int movedShown = label.length() == row.length() ? moved : Math.max(0, label.length() - 1 - ghostLength);
		if (movedShown == 0) {
			gc.drawString(label, x, y, true);
			return gc.stringExtent(label).x;
		}
		final var layout = new TextLayout(gc.getDevice());
		try {
			layout.setFont(textWidget.getFont());
			layout.setText(label);
			layout.setStyle(new TextStyle(textWidget.getFont(), color, null), 0, label.length() - 1);
			int start = 0;
			for (int i = 1; i <= movedShown; i++) {
				if (i == movedShown || !movedStyles.get(i).equals(movedStyles.get(start))) {
					layout.setStyle(movedStyles.get(start), ghostLength + start, ghostLength + i - 1);
					start = i;
				}
			}
			layout.draw(gc, x, y);
			return layout.getBounds().width;
		} finally {
			layout.dispose();
		}
	}

	/**
	 * With word wrap on, the widened character the first line is drawn on cannot wrap, so the line is
	 * cut to the space left on the visual line.
	 */
	static String fitToWidth(GC gc, StyledText textWidget, String label, int x) {
		if (!textWidget.getWordWrap()) {
			return label;
		}
		final int available = textWidget.getClientArea().width - textWidget.getRightMargin() - x;
		// as in the editor, spaces ending a wrapped line take no room
		if (gc.stringExtent(label.stripTrailing()).x <= available) {
			return label;
		}
		int length = label.length();
		while (length > 0 && gc.stringExtent(label.substring(0, length) + ELLIPSIS).x > available) {
			length--;
		}
		return label.substring(0, length) + ELLIPSIS;
	}
}
