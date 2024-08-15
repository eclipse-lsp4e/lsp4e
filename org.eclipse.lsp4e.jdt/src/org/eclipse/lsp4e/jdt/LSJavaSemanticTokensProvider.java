/*******************************************************************************
 * Copyright (c) 2024 Broadcom Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Alex Boyko (Broadcom Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.text.java.ISemanticTokensProvider;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.CancellationUtil;
import org.eclipse.lsp4e.jdt.preferences.PreferenceConstants;
import org.eclipse.lsp4e.operations.semanticTokens.SemanticHighlightReconcilerStrategy;
import org.eclipse.lsp4e.operations.semanticTokens.SemanticTokensClient;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensLegend;

public class LSJavaSemanticTokensProvider implements ISemanticTokensProvider {
	
	private static final int TIMEOUT_SEMANTIC_TOKENS = 300;
	
	@SuppressWarnings("null")
	@Override
	public Collection<ISemanticTokensProvider.SemanticToken> computeSemanticTokens(CompilationUnit ast) {
		IPreferenceStore prefStore = LanguageServerPlugin.getDefault().getPreferenceStore();
		LanguageServerJdtPlugin plugin = LanguageServerJdtPlugin.getDefault();
		if (plugin == null) {
			throw new IllegalStateException("Plugin hasn't been started!");
		}
		IPreferenceStore jstPrefStore = plugin.getPreferenceStore();
		
		if (prefStore.getBoolean(SemanticHighlightReconcilerStrategy.SEMANTIC_HIGHLIGHT_RECONCILER_DISABLED)
				|| !jstPrefStore.getBoolean(PreferenceConstants.PREF_SEMANTIC_TOKENS_SWITCH)) {
			return Collections.emptyList();
		}
		
		IResource resource = ast.getTypeRoot().getResource();
		if (resource == null) {
			return Collections.emptyList();
		}
		
		final IDocument theDocument = LSPEclipseUtils.getDocument(resource);
		if (theDocument == null) {
			return Collections.emptyList();
		}
		
		try {
			return SemanticTokensClient.DEFAULT.requestFullSemanticTokens(theDocument, (legend, semanticTokens) -> convertTokens(legend, theDocument, semanticTokens))
				.thenApply(o -> o.orElse(Collections.emptyList())).get(TIMEOUT_SEMANTIC_TOKENS, TimeUnit.MILLISECONDS);
		} catch (TimeoutException e) {
			LanguageServerPlugin.logWarning("Timed out after waiting for %dms for semantic tokens from Language Servers".formatted(TIMEOUT_SEMANTIC_TOKENS), e);
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			if (!CancellationUtil.isRequestCancelledException(e)) { // do not report error if the server has cancelled the request
				LanguageServerPlugin.logError("Failed to fetch semantic tokens for '%s' from Language Servers".formatted(resource.getLocation()), e);
			}
		}		
		return Collections.emptyList();
	}
	
	private List<ISemanticTokensProvider.SemanticToken> convertTokens(@Nullable SemanticTokensLegend legend, IDocument theDocument, @Nullable SemanticTokens semanticTokens) {
		if (semanticTokens == null) {
			return Collections.emptyList();
		}
		if (legend == null) {
			return Collections.emptyList();
		}
		return new JavaSemanticTokensProcessor(this::mapToTokenType, p -> {
			try {
				return LSPEclipseUtils.toOffset(p, theDocument);
			} catch (BadLocationException e) {
				throw new RuntimeException(e);
			}
		}).getTokensData(semanticTokens.getData(), legend);
	}
	
	private ISemanticTokensProvider.TokenType mapToTokenType(String tokeTypeStr) {
		return switch (tokeTypeStr) {
			case "method" -> ISemanticTokensProvider.TokenType.METHOD;
			case "comment" -> ISemanticTokensProvider.TokenType.SINGLE_LINE_COMMENT;
			case "variable" -> ISemanticTokensProvider.TokenType.LOCAL_VARIABLE;
			case "type" -> ISemanticTokensProvider.TokenType.CLASS;
			case "property" -> ISemanticTokensProvider.TokenType.FIELD;
			case "keyword" -> ISemanticTokensProvider.TokenType.KEYWORD;
			case "operator" -> ISemanticTokensProvider.TokenType.OPERATOR;
			case "number" -> ISemanticTokensProvider.TokenType.NUMBER;
			case "string" -> ISemanticTokensProvider.TokenType.STRING;
			case "enum" -> ISemanticTokensProvider.TokenType.ENUM;
			case "class" -> ISemanticTokensProvider.TokenType.CLASS;
			case "macro" -> ISemanticTokensProvider.TokenType.STATIC_METHOD_INVOCATION;
			case "parameter" -> ISemanticTokensProvider.TokenType.PARAMETER_VARIABLE;
			default -> ISemanticTokensProvider.TokenType.DEFAULT;
		};
	}


}
