/*******************************************************************************
 * Copyright (c) 2024 Advantest GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Dietrich Travkin (Solunar GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.WorkspaceSymbol;

public class SymbolsUtil {

	public static SymbolKind getKind(SymbolInformation symbolInformation) {
		return symbolInformation.getKind();
	}

	public static SymbolKind getKind(WorkspaceSymbol workspaceSymbol) {
		return workspaceSymbol.getKind();
	}

	public static SymbolKind getKind(DocumentSymbol documentSymbol) {
		return documentSymbol.getKind();
	}

	public static SymbolKind getKind(DocumentSymbolWithURI documentSymbolWithUri) {
		return getKind(documentSymbolWithUri.symbol);
	}

	public static List<SymbolTag> getSymbolTags(SymbolInformation symbolInformation) {
		if (symbolInformation.getTags() != null) {
			return symbolInformation.getTags();
		}

		return Collections.emptyList();
	}

	public static List<SymbolTag> getSymbolTags(WorkspaceSymbol workspaceSymbol) {
		if (workspaceSymbol.getTags() != null) {
			return workspaceSymbol.getTags();
		}

		return Collections.emptyList();
	}

	public static List<SymbolTag> getSymbolTags(DocumentSymbol documentSymbol) {
		if (documentSymbol.getTags() != null) {
			return documentSymbol.getTags();
		}

		return Collections.emptyList();
	}

	public static List<SymbolTag> getSymbolTags(DocumentSymbolWithURI documentSymbolWithUri) {
		return getSymbolTags(documentSymbolWithUri.symbol);
	}

	public static boolean isDeprecated(SymbolInformation symbolInformation) {
		boolean deprecated = isDeprecated(getSymbolTags(symbolInformation));
		return deprecated || Boolean.TRUE.equals(symbolInformation.getDeprecated());
	}

	public static boolean isDeprecated(WorkspaceSymbol workspaceSymbol) {
		return isDeprecated(getSymbolTags(workspaceSymbol));
	}

	public static boolean isDeprecated(DocumentSymbol documentSymbol) {
		boolean deprecated = isDeprecated(getSymbolTags(documentSymbol));
		return deprecated || Boolean.TRUE.equals(documentSymbol.getDeprecated());
	}

	public static boolean isDeprecated(DocumentSymbolWithURI documentSymbolWithUri) {
		return isDeprecated(documentSymbolWithUri.symbol);
	}

	public static boolean isDeprecated(List<SymbolTag> tags) {
		return tags.contains(SymbolTag.Deprecated);
	}

}
