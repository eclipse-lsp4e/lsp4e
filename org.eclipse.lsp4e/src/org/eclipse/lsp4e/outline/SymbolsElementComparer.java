/*******************************************************************************
 * Copyright (c) 2026 Vogella GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lars Vogel (Vogella GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.IElementComparer;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

/**
 * Identifies outline elements by name, kind and selection start instead of by
 * value.
 * <p>
 * {@link DocumentSymbol#hashCode()} and {@link DocumentSymbol#equals(Object)}
 * include the {@code children} list, so they walk the entire subtree. Tree
 * viewers call both per element on every refresh, which makes a full outline
 * refresh cost the sum of all subtree sizes.
 */
public final class SymbolsElementComparer implements IElementComparer {

	@Override
	public int hashCode(final @Nullable Object element) {
		if (element instanceof DocumentSymbolWithURI symbolWithURI)
			return 31 * hashCode(symbolWithURI.symbol) + symbolWithURI.uri.hashCode();
		if (element instanceof DocumentSymbol symbol) {
			final Position start = selectionStartOf(symbol);
			return Objects.hash(symbol.getName(), symbol.getKind(), start);
		}
		return element == null ? 0 : element.hashCode();
	}

	@Override
	public boolean equals(final @Nullable Object a, final @Nullable Object b) {
		if (a == b)
			return true;
		if (a == null || b == null)
			return false;
		if (a instanceof DocumentSymbolWithURI symbolA && b instanceof DocumentSymbolWithURI symbolB)
			return symbolA.uri.equals(symbolB.uri) && equals(symbolA.symbol, symbolB.symbol);
		if (a instanceof DocumentSymbol symbolA && b instanceof DocumentSymbol symbolB)
			return symbolA.getKind() == symbolB.getKind() //
					&& Objects.equals(symbolA.getName(), symbolB.getName())
					&& Objects.equals(selectionStartOf(symbolA), selectionStartOf(symbolB));
		return a.equals(b);
	}

	/**
	 * Uses the selection range rather than the full range, because the full range
	 * grows while typing inside the symbol body.
	 */
	private static @Nullable Position selectionStartOf(final DocumentSymbol symbol) {
		final Range selectionRange = symbol.getSelectionRange();
		return selectionRange == null ? null : selectionRange.getStart();
	}
}
