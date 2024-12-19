/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.semanticTokens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SemanticTokensLegend;
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions;
import org.eclipse.swt.graphics.Color;

public class SemanticTokensTestUtil {
	public static final String keywordText =
			"type foo {\n" +
			"	\n" +
			"}\n" +
			"type bar extends foo {\n" +
			"	\n" +
			"}\n";

	public static List<Integer> keywordSemanticTokens() {
		final var expectedTokens = new ArrayList<List<Integer>>();
		expectedTokens.add(List.of(0,0,4,0,0));
		expectedTokens.add(List.of(3,0,4,0,0));
		expectedTokens.add(List.of(0,9,7,0,0));

		return expectedTokens.stream().flatMap(List::stream).toList();
	}

	public static final Color GREEN = new Color(133, 153, 0, 255);
	public static final Color RED = new Color(255, 0, 0);

	public static final IToken GREEN_TOKEN = new IToken() {
		@Override
		public boolean isWhitespace() {
			return false;
		}

		@Override
		public boolean isUndefined() {
			return false;
		}

		@Override
		public boolean isOther() {
			return false;
		}

		@Override
		public boolean isEOF() {
			return false;
		}

		@Override
		public Object getData() {
			return new TextAttribute(GREEN);
		}
	};

	public static final IToken RED_TOKEN = new IToken() {
		@Override
		public boolean isWhitespace() {
			return false;
		}

		@Override
		public boolean isUndefined() {
			return false;
		}

		@Override
		public boolean isOther() {
			return false;
		}

		@Override
		public boolean isEOF() {
			return false;
		}

		@Override
		public Object getData() {
			return new TextAttribute(RED);
		}
	};

	public static Function<String, IToken> keywordTokenTypeMapper(final IToken token) {
		return t -> "keyword".equals(t) ? token : null;
	}

	public static @NonNull Function<Position, Integer> offsetMapper(IDocument document) {
		return p -> {
			try {
				return LSPEclipseUtils.toOffset(p, document);
			} catch (BadLocationException e) {
				throw new RuntimeException(e);
			}
		};
	}
	public static void setSemanticTokensLegend(final List<String> tokenTypes, List<String> tokenModifiers) {
		final var legend = new SemanticTokensLegend(tokenTypes, tokenModifiers);
		final var semanticTokensWithRegistrationOptions = new SemanticTokensWithRegistrationOptions(legend);
		semanticTokensWithRegistrationOptions.setFull(true);
		semanticTokensWithRegistrationOptions.setRange(false);

		MockLanguageServer.INSTANCE.getInitializeResult().getCapabilities().setSemanticTokensProvider(semanticTokensWithRegistrationOptions);
	}
}
