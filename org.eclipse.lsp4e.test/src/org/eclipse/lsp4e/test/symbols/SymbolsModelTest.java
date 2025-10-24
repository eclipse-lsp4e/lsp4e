/*******************************************************************************
 * Copyright (c) 2016 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Sebastian Thomschke (Vegard IT GmbH) - Cleanup test cases and add test cases for update change detection
 *******************************************************************************/
package org.eclipse.lsp4e.test.symbols;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.lsp4e.outline.SymbolsModel;
import org.eclipse.lsp4e.test.utils.AbstractTest;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;

public class SymbolsModelTest extends AbstractTest {

	private final SymbolsModel symbolsModel = new SymbolsModel();

	@Test
	public void testSymbolInformationHierarchy() {
		final SymbolInformation[] items = {
				newSymbolInformation("Namespace", SymbolKind.Namespace, newRange(0, 0, 10, 0)),
				newSymbolInformation("Class", SymbolKind.Class, newRange(1, 0, 9, 0)),
				newSymbolInformation("Method", SymbolKind.Method, newRange(2, 0, 8, 0)) };
		symbolsModel_update(items);

		assertEquals(1, symbolsModel.getElements().length);
		assertEquals(items[0], symbolsModel.getElements()[0]);
		Object[] children = symbolsModel.getChildren(symbolsModel.getElements()[0]);
		assertEquals(1, children.length);
		assertEquals(items[1], children[0]);
		children = symbolsModel.getChildren(children[0]);
		assertEquals(1, children.length);
		assertEquals(items[2], children[0]);

		Object parent = symbolsModel.getParent(children[0]);
		assertEquals(items[1], parent);
		parent = symbolsModel.getParent(parent);
		assertEquals(items[0], parent);
	}

	/**
	 * When a symbol and its child have matching starting points, ensure that the
	 * child is marked as such and not a new parent
	 */
	@Test
	public void testSymbolsMatchingStartingPositions() {
		final SymbolInformation[] items = {
				newSymbolInformation("Namespace", SymbolKind.Namespace, newRange(0, 0, 10, 0)),
				newSymbolInformation("Class", SymbolKind.Class, newRange(0, 0, 9, 0)),
				newSymbolInformation("Method", SymbolKind.Method, newRange(1, 0, 8, 0)) };
		symbolsModel_update(items);

		assertEquals(1, symbolsModel.getElements().length);
		assertEquals(items[0], symbolsModel.getElements()[0]);
		assertTrue(symbolsModel.hasChildren(symbolsModel.getElements()[0]));
		Object[] children = symbolsModel.getChildren(symbolsModel.getElements()[0]);
		assertEquals(1, children.length);
		assertEquals(items[1], children[0]);
		assertTrue(symbolsModel.hasChildren(children[0]));
		children = symbolsModel.getChildren(children[0]);
		assertEquals(1, children.length);
		assertEquals(items[2], children[0]);

		Object parent = symbolsModel.getParent(children[0]);
		assertEquals(items[1], parent);
		parent = symbolsModel.getParent(parent);
		assertEquals(items[0], parent);
	}

	/**
	 * Confirms that duplicate items do not become children of themselves
	 */
	@Test
	public void testDuplicateSymbols() {
		final var range = newRange(0, 0, 0, 0);
		final SymbolInformation[] items = { //
				newSymbolInformation("Duplicate", SymbolKind.Namespace, range),
				newSymbolInformation("Duplicate", SymbolKind.Namespace, range) };
		symbolsModel_update(items);

		assertEquals(2, symbolsModel.getElements().length);
		assertFalse(symbolsModel.hasChildren(symbolsModel.getElements()[0]));
		assertFalse(symbolsModel.hasChildren(symbolsModel.getElements()[1]));
		assertEquals(0, symbolsModel.getChildren(symbolsModel.getElements()[0]).length);
		assertEquals(0, symbolsModel.getChildren(symbolsModel.getElements()[1]).length);
	}

	@Test
	public void testGetElementsEmptyResponse() {
		symbolsModel.update(Collections.emptyList());
		assertEquals(0, symbolsModel.getElements().length);
	}

	@Test
	public void testGetElementsNullResponse() {
		symbolsModel.update(null);
		assertEquals(0, symbolsModel.getElements().length);
	}

	@Test
	public void testGetParentEmptyResponse() {
		symbolsModel.update(Collections.emptyList());
		assertEquals(null, symbolsModel.getParent(null));
	}

	@Test
	public void testGetParentNullResponse() {
		symbolsModel.update(null);
		assertEquals(null, symbolsModel.getParent(null));
	}

	@Test
	public void testUpdateChangeDetection_EmptyResponse() {
		// empty to empty -> no change
		assertFalse(symbolsModel.update(null));
		assertEquals(0, symbolsModel.getElements().length);
		assertFalse(symbolsModel.update(Collections.emptyList()));
		assertEquals(0, symbolsModel.getElements().length);
	}

	@Test
	public void testUpdateChangeDetection_DocumentSymbolsResponse() {

		// populate with one symbol (A) -> change
		final var symbolA = new DocumentSymbol("A", SymbolKind.Class, newRange(0, 0, 10, 0), newRange(0, 0, 0, 1),
				null);
		final var symbolAClone = new DocumentSymbol("A", SymbolKind.Class, newRange(0, 0, 10, 0), newRange(0, 0, 0, 1),
				null);
		final var symbolB = new DocumentSymbol("B", SymbolKind.Method, newRange(2, 0, 2, 5), newRange(2, 0, 2, 1),
				null);
		final var symbolBClone = new DocumentSymbol("B", SymbolKind.Method, newRange(2, 0, 2, 5), newRange(2, 0, 2, 1),
				null);

		assertTrue(symbolsModel_update(symbolA));
		assertEquals(1, symbolsModel.getElements().length);

		// non-empty to empty -> change
		assertTrue(symbolsModel.update(Collections.emptyList()));
		assertEquals(0, symbolsModel.getElements().length);

		// empty to empty again -> no change
		assertFalse(symbolsModel.update(Collections.emptyList()));
		assertEquals(0, symbolsModel.getElements().length);

		// populate with two root symbols A, B -> change
		assertTrue(symbolsModel_update(symbolA, symbolB));
		assertEquals(2, symbolsModel.getElements().length);

		// same two symbols in same order -> no change
		assertFalse(symbolsModel_update(symbolAClone, symbolBClone));
		assertEquals(2, symbolsModel.getElements().length);

		// same two symbols but different order -> no change (normalized)
		assertFalse(symbolsModel_update(symbolB, symbolA));
		assertEquals(2, symbolsModel.getElements().length);

		// position-only update for both roots but with same effective order -> change
		final var symbolAShift = new DocumentSymbol("A", SymbolKind.Class, newRange(0, 1, 10, 1), newRange(0, 1, 0, 2),
				null);
		final var symbolBShift = new DocumentSymbol("B", SymbolKind.Method, newRange(2, 1, 2, 6), newRange(2, 1, 2, 2),
				null);
		assertTrue(symbolsModel_update(symbolAShift, symbolBShift));

		// position-only update for both roots but with different order -> change
		final var symbolAShift2 = new DocumentSymbol("A", SymbolKind.Class, symbolBShift.getRange(),
				symbolBShift.getSelectionRange(), null);
		final var symbolBShift2 = new DocumentSymbol("B", SymbolKind.Method, symbolAShift.getRange(),
				symbolAShift.getSelectionRange(), null);
		assertTrue(symbolsModel_update(symbolAShift2, symbolBShift2));

		// Ensure DocumentSymbol elements are wrapped with URI so
		// hasChildren/getChildren work
		symbolsModel.setUri(URI.create("file://test"));

		// Now make B a child of A -> change
		final var symbolBAsChild = new DocumentSymbol("B", SymbolKind.Method, newRange(0, 2, 0, 4),
				newRange(0, 2, 0, 3), null);
		final var symbolAWithChildB = new DocumentSymbol("A", SymbolKind.Class, newRange(0, 0, 10, 0),
				newRange(0, 0, 0, 1));
		symbolAWithChildB.setChildren(List.of(symbolBAsChild));

		assertTrue(symbolsModel_update(symbolAWithChildB));
		assertEquals(1, symbolsModel.getElements().length);
		assertTrue(symbolsModel.hasChildren(symbolsModel.getElements()[0]));

		// same nested tree again -> no change
		final var symbolBAsChildClone = new DocumentSymbol("B", SymbolKind.Method, newRange(0, 2, 0, 4),
				newRange(0, 2, 0, 3), null);
		final var symbolAWithChildBClone = new DocumentSymbol("A", SymbolKind.Class, newRange(0, 0, 10, 0),
				newRange(0, 0, 0, 1));
		symbolAWithChildBClone.setChildren(List.of(symbolBAsChildClone));
		assertFalse(symbolsModel_update(symbolAWithChildBClone));

		// replace B with C as child of A -> change
		final var symbolC = new DocumentSymbol("C", SymbolKind.Method, newRange(0, 2, 0, 4), newRange(0, 2, 0, 3),
				null);
		final var symbolAWithChildC = new DocumentSymbol("A", SymbolKind.Class, newRange(0, 0, 10, 0),
				newRange(0, 0, 0, 1));
		symbolAWithChildC.setChildren(List.of(symbolC));
		assertTrue(symbolsModel_update(symbolAWithChildC));
		assertEquals(1, symbolsModel.getElements().length);
		assertTrue(symbolsModel.hasChildren(symbolsModel.getElements()[0]));
	}

	@Test
	public void testUpdateChangeDetection_SymbolInformationResponse() {
		final var symbolA = newSymbolInformation("A", SymbolKind.Class, newRange(0, 0, 0, 10));
		final var symbolAClone = newSymbolInformation("A", SymbolKind.Class, newRange(0, 0, 0, 10));
		final var symbolB = newSymbolInformation("B", SymbolKind.Method, newRange(2, 0, 2, 5));
		final var symbolBClone = newSymbolInformation("B", SymbolKind.Method, newRange(2, 0, 2, 5));

		// populate with one symbol -> change
		assertTrue(symbolsModel_update(symbolA));
		assertEquals(1, symbolsModel.getElements().length);

		// non-empty to empty -> change
		assertTrue(symbolsModel.update(Collections.emptyList()));
		assertEquals(0, symbolsModel.getElements().length);

		// empty to empty again -> no change
		assertFalse(symbolsModel.update(Collections.emptyList()));
		assertEquals(0, symbolsModel.getElements().length);

		// populate with two symbols -> change
		assertTrue(symbolsModel_update(symbolA, symbolB));
		assertEquals(2, symbolsModel.getElements().length);

		// populate with same two symbols in same order -> no change
		assertFalse(symbolsModel_update(symbolA, symbolB));
		assertEquals(2, symbolsModel.getElements().length);

		// populate with same two symbols cloned in same order -> no change
		assertFalse(symbolsModel_update(symbolAClone, symbolBClone));
		assertEquals(2, symbolsModel.getElements().length);

		// populate with same two symbols but in different order -> no change
		assertFalse(symbolsModel_update(symbolB, symbolA));
		assertEquals(2, symbolsModel.getElements().length);

		// make B included in A by changing its range to be inside A's range -> change
		final var symbolBAsChild = newSymbolInformation("B", SymbolKind.Method, newRange(0, 2, 0, 4));
		assertTrue(symbolsModel_update(symbolA, symbolBAsChild));
		// The model now exposes A as root and B as child
		assertEquals(1, symbolsModel.getElements().length);
		assertTrue(symbolsModel.hasChildren(symbolsModel.getElements()[0]));

		// populate with same nested tree -> no change
		assertFalse(symbolsModel_update(symbolAClone, symbolBAsChild));

		// replace B with C as child of A -> change
		final var symbolCAsChild = newSymbolInformation("C", SymbolKind.Method, newRange(0, 2, 0, 4));
		assertTrue(symbolsModel_update(symbolA, symbolCAsChild));
		// The model now exposes A as root and C as child
		assertEquals(1, symbolsModel.getElements().length);
		assertTrue(symbolsModel.hasChildren(symbolsModel.getElements()[0]));
	}

	private boolean symbolsModel_update(DocumentSymbol... symbols) {
		return symbolsModel.update(
				Arrays.stream(symbols).map(sym -> Either.<SymbolInformation, DocumentSymbol>forRight(sym)).toList());
	}

	private boolean symbolsModel_update(SymbolInformation... symbols) {
		return symbolsModel.update(
				Arrays.stream(symbols).map(sym -> Either.<SymbolInformation, DocumentSymbol>forLeft(sym)).toList());
	}

	private Range newRange(int startLine, int startChar, int endLine, int endChar) {
		return new Range(new Position(startLine, startChar), new Position(endLine, endChar));
	}

	@SuppressWarnings("deprecation")
	private SymbolInformation newSymbolInformation(String name, SymbolKind kind, Range range) {
		final var symbolInformation = new SymbolInformation();
		symbolInformation.setName(name);
		symbolInformation.setKind(kind);
		symbolInformation.setLocation(new Location("file://test", range));
		return symbolInformation;
	}
}
