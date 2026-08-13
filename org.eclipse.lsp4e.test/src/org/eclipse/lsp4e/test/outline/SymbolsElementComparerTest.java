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
package org.eclipse.lsp4e.test.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;

import org.eclipse.lsp4e.outline.SymbolsElementComparer;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.Test;

/**
 * The fixture mirrors what vscode-json-languageserver returns for a nested
 * array-of-objects document, where (name, kind) pairs repeat at several depths.
 */
public class SymbolsElementComparerTest {

	private static final URI URI_A = URI.create("file:///a.json");
	private static final URI URI_B = URI.create("file:///b.json");

	private final SymbolsElementComparer comparer = new SymbolsElementComparer();

	private static DocumentSymbol symbol(String name, SymbolKind kind, int line, int character) {
		final var position = new Position(line, character);
		// a deliberately wide full range, so tests that vary it prove it is not part of the key
		return new DocumentSymbol(name, kind, new Range(position, new Position(line + 50, 0)),
				new Range(position, new Position(line, character + name.length())));
	}

	/** items[0].id and items[1].id share name and kind, and differ only in position. */
	@Test
	public void testSameNameAndKindAtDifferentPositionsAreDistinct() {
		final DocumentSymbol itemsZeroId = symbol("id", SymbolKind.Number, 3, 6);
		final DocumentSymbol itemsOneId = symbol("id", SymbolKind.Number, 7, 6);

		assertFalse(comparer.equals(itemsZeroId, itemsOneId));
		assertNotEquals(comparer.hashCode(itemsZeroId), comparer.hashCode(itemsOneId));
	}

	/** items[0] and other[0] are both name="0" kind=Module, at different depths of the tree. */
	@Test
	public void testSameNameAndKindAtDifferentDepthsAreDistinct() {
		final DocumentSymbol itemsZero = symbol("0", SymbolKind.Module, 2, 4);
		final DocumentSymbol otherZero = symbol("0", SymbolKind.Module, 12, 4);

		assertFalse(comparer.equals(itemsZero, otherZero));
		assertNotEquals(comparer.hashCode(itemsZero), comparer.hashCode(otherZero));
	}

	/**
	 * The whole point of the comparer: the key must not depend on the subtree, or
	 * hashing recurses into every descendant.
	 */
	@Test
	public void testChildrenAreNotPartOfTheKey() {
		final DocumentSymbol withoutChildren = symbol("0", SymbolKind.Module, 2, 4);
		final DocumentSymbol withChildren = symbol("0", SymbolKind.Module, 2, 4);
		withChildren.setChildren(List.of(symbol("id", SymbolKind.Number, 3, 6), //
				symbol("name", SymbolKind.String, 4, 6)));

		assertTrue(comparer.equals(withoutChildren, withChildren));
		assertEquals(comparer.hashCode(withoutChildren), comparer.hashCode(withChildren));
	}

	/**
	 * The full range grows while typing inside the symbol body, so it must not be
	 * part of the key, otherwise the edited symbol rehashes on every keystroke.
	 */
	@Test
	public void testFullRangeIsNotPartOfTheKey() {
		final DocumentSymbol before = symbol("items", SymbolKind.Array, 1, 2);
		final DocumentSymbol afterTyping = symbol("items", SymbolKind.Array, 1, 2);
		afterTyping.setRange(new Range(new Position(1, 2), new Position(999, 0)));

		assertTrue(comparer.equals(before, afterTyping));
		assertEquals(comparer.hashCode(before), comparer.hashCode(afterTyping));
	}

	/** The server returns fresh instances on every refresh, so identity must not be used. */
	@Test
	public void testStructurallyIdenticalSymbolsFromASecondRefreshMatch() {
		final DocumentSymbol firstRefresh = symbol("items", SymbolKind.Array, 1, 2);
		final DocumentSymbol secondRefresh = symbol("items", SymbolKind.Array, 1, 2);

		assertTrue(comparer.equals(firstRefresh, secondRefresh));
		assertEquals(comparer.hashCode(firstRefresh), comparer.hashCode(secondRefresh));
	}

	@Test
	public void testDifferentKindsAreDistinct() {
		final DocumentSymbol asNumber = symbol("id", SymbolKind.Number, 3, 6);
		final DocumentSymbol asString = symbol("id", SymbolKind.String, 3, 6);

		assertFalse(comparer.equals(asNumber, asString));
	}

	@Test
	public void testWrappedSymbolsFromDifferentDocumentsAreDistinct() {
		final DocumentSymbol symbol = symbol("items", SymbolKind.Array, 1, 2);
		final var inA = new DocumentSymbolWithURI(symbol, URI_A);
		final var inB = new DocumentSymbolWithURI(symbol, URI_B);

		assertFalse(comparer.equals(inA, inB));
		assertNotEquals(comparer.hashCode(inA), comparer.hashCode(inB));
	}

	@Test
	public void testWrappedSymbolsAreMatchedByKey() {
		final var firstRefresh = new DocumentSymbolWithURI(symbol("items", SymbolKind.Array, 1, 2), URI_A);
		final var secondRefresh = new DocumentSymbolWithURI(symbol("items", SymbolKind.Array, 1, 2), URI_A);

		assertTrue(comparer.equals(firstRefresh, secondRefresh));
		assertEquals(comparer.hashCode(firstRefresh), comparer.hashCode(secondRefresh));
	}

	@Test
	public void testUnwrappedAndWrappedSymbolsDoNotMatch() {
		final DocumentSymbol symbol = symbol("items", SymbolKind.Array, 1, 2);

		assertFalse(comparer.equals(symbol, new DocumentSymbolWithURI(symbol, URI_A)));
	}

	/** The tree also holds SymbolInformation and the outline input object. */
	@Test
	public void testUnknownElementTypesFallBackToTheirOwnEquality() {
		assertTrue(comparer.equals("same", "same"));
		assertFalse(comparer.equals("one", "other"));
		assertEquals("same".hashCode(), comparer.hashCode("same"));
	}

	/**
	 * The setters reject null, but gson writes the fields directly, so a malformed
	 * server response can still produce a symbol without a selection range.
	 */
	private static DocumentSymbol deserializedWithoutSelectionRange(String name, SymbolKind kind) {
		final var symbol = new DocumentSymbol();
		symbol.setName(name);
		symbol.setKind(kind);
		return symbol;
	}

	@Test
	public void testNullsAreTolerated() {
		final DocumentSymbol withoutSelectionRange = deserializedWithoutSelectionRange("items", SymbolKind.Array);

		assertTrue(comparer.equals(null, null));
		assertFalse(comparer.equals(null, withoutSelectionRange));
		assertFalse(comparer.equals(withoutSelectionRange, null));
		assertEquals(0, comparer.hashCode(null));
		// must not throw
		comparer.hashCode(withoutSelectionRange);
	}

	@Test
	public void testSymbolsWithoutSelectionRangeFallBackToNameAndKind() {
		final DocumentSymbol one = deserializedWithoutSelectionRange("items", SymbolKind.Array);
		final DocumentSymbol copy = deserializedWithoutSelectionRange("items", SymbolKind.Array);
		final DocumentSymbol other = deserializedWithoutSelectionRange("other", SymbolKind.Array);

		assertTrue(comparer.equals(one, copy));
		assertEquals(comparer.hashCode(one), comparer.hashCode(copy));
		assertFalse(comparer.equals(one, other));
		assertFalse(comparer.equals(one, symbol("items", SymbolKind.Array, 1, 2)));
	}
}
