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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.outline.CNFOutlinePage;
import org.eclipse.lsp4e.outline.OutlineSorter;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class OutlineSorterTest {

	private final AtomicInteger compareCalls = new AtomicInteger();

	private final OutlineSorter sorter = new OutlineSorter() {
		@Override
		public int compare(final Viewer viewer, final Object o1, final Object o2) {
			compareCalls.incrementAndGet();
			return super.compare(viewer, o1, o2);
		}
	};

	@AfterEach
	public void resetPreference() {
		InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID).remove(CNFOutlinePage.SORT_OUTLINE_PREFERENCE);
	}

	private static DocumentSymbol symbol(final String name) {
		final var range = new Range(new Position(0, 0), new Position(0, 1));
		return new DocumentSymbol(name, SymbolKind.Method, range, range);
	}

	private static void setSorting(final boolean enabled) {
		InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID).putBoolean(CNFOutlinePage.SORT_OUTLINE_PREFERENCE,
				enabled);
	}

	@Test
	public void testSortSkippedWhenDisabled() {
		setSorting(false);
		final Object[] elements = { symbol("c"), symbol("a"), symbol("b") };
		final Object[] expected = elements.clone();

		sorter.sort(null, elements);

		assertArrayEquals(expected, elements);
		assertEquals(0, compareCalls.get());
	}

	@Test
	public void testSortByNameWhenEnabled() {
		setSorting(true);
		final DocumentSymbol a = symbol("a");
		final DocumentSymbol b = symbol("b");
		final DocumentSymbol c = symbol("c");
		final Object[] elements = { c, a, b };

		sorter.sort(null, elements);

		assertArrayEquals(new Object[] { a, b, c }, elements);
	}
}
