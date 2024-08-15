/*******************************************************************************
 * Copyright (c) 2022, 2024 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 *******************************************************************************/
package org.eclipse.lsp4e.operations.semanticTokens;

import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.lsp4e.internal.StyleUtil;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokenModifiers;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;

/**
 * The Class SemanticTokensDataStreamProcessor translates a stream of integers
 * as defined by the LSP SemanticTokenRequests into a list of StyleRanges.
 */
public class SemanticTokensDataStreamProcessor extends AbstractcSemanticTokensDataStreamProcessor<IToken, StyleRange> {

	/**
	 * Creates a new instance of {@link SemanticTokensDataStreamProcessor}.
	 *
	 * @param tokenTypeMapper
	 * @param offsetMapper
	 */
	public SemanticTokensDataStreamProcessor(final Function<String, @Nullable IToken> tokenTypeMapper,
			final Function<Position, Integer> offsetMapper) {
		super(offsetMapper, tokenTypeMapper);
	}

	@Override
	protected @Nullable StyleRange createTokenData(@Nullable IToken tokenType, int offset, int length, List<String> tokenModifiers) {
		StyleRange styleRange = getStyleRange(offset, length, textAttribute(tokenType));
		if (tokenModifiers.stream().anyMatch(x -> x.equals(SemanticTokenModifiers.Deprecated))) {
			if (styleRange == null) {
				styleRange = new StyleRange();
				styleRange.start = offset;
				styleRange.length = length;
			}
			StyleUtil.DEPRECATE.applyStyles(styleRange);
		}
		return styleRange;
	}

	private @Nullable TextAttribute textAttribute(final @Nullable IToken tokenType) {
		if (tokenType != null) {
			Object data = tokenType.getData();
			if (data instanceof final TextAttribute textAttribute) {
				return textAttribute;
			}
		}
		return null;
	}

	/**
	 * Gets a style range for the given inputs.
	 *
	 * @param offset
	 *            the offset of the range to be styled
	 * @param length
	 *            the length of the range to be styled
	 * @param attr
	 *            the attribute describing the style of the range to be styled
	 */
	private @Nullable StyleRange getStyleRange(final int offset, final int length, final @Nullable TextAttribute attr) {
		if (attr != null) {
			final int style = attr.getStyle();
			final int fontStyle = style & (SWT.ITALIC | SWT.BOLD | SWT.NORMAL);
			final var styleRange = new StyleRange(offset, length, attr.getForeground(), attr.getBackground(), fontStyle);
			styleRange.strikeout = (style & TextAttribute.STRIKETHROUGH) != 0;
			styleRange.underline = (style & TextAttribute.UNDERLINE) != 0;
			styleRange.font = attr.getFont();
			return styleRange;
		}
		return null;
	}
}