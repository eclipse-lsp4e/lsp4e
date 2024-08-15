/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.ui.text.java.ISemanticTokensProvider;
import org.eclipse.lsp4e.operations.semanticTokens.AbstractcSemanticTokensDataStreamProcessor;
import org.eclipse.lsp4j.Position;

class JavaSemanticTokensProcessor extends AbstractcSemanticTokensDataStreamProcessor<ISemanticTokensProvider.TokenType, ISemanticTokensProvider.SemanticToken> {
	
	public JavaSemanticTokensProcessor(final Function<String, ISemanticTokensProvider.@Nullable TokenType> tokenTypeMapper,
			final Function<Position, Integer> offsetMapper) {
		super(offsetMapper, tokenTypeMapper);
	}

	@Override
	protected ISemanticTokensProvider.@Nullable SemanticToken createTokenData(
			ISemanticTokensProvider.@Nullable TokenType tt, int offset, int length, List<String> tokenModifiers) {
		if (tt != null) {
			return new ISemanticTokensProvider.SemanticToken(offset, length, tt);
		}
		return null;
	}
}
