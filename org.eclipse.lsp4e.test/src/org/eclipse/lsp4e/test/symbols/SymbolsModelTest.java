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
 *******************************************************************************/
package org.eclipse.lsp4e.test.symbols;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

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
