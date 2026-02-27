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
package org.eclipse.lsp4e.test.symbols;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4e.operations.symbols.SymbolsUtil;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.junit.jupiter.api.Test;

public class SymbolsUtilTest {
	
	private static final List<SymbolTag> symbolTagsWithDeprecated = Arrays.asList(
			SymbolTag.Package, SymbolTag.Deprecated, SymbolTag.ReadOnly);
	private static final List<SymbolTag> symbolTagsWithoutDeprecated = Arrays.asList(
			SymbolTag.Public, SymbolTag.Declaration, SymbolTag.Static);
	
	@Test
	public void testDeprecatedCheckForSymbolInformation() {
		var symbolInformation = new SymbolInformation();
		
		assertFalse(SymbolsUtil.isDeprecated(symbolInformation));
		
		symbolInformation.setDeprecated(true);
		
		assertTrue(SymbolsUtil.isDeprecated(symbolInformation));
		
		symbolInformation = new SymbolInformation();
		symbolInformation.setTags(symbolTagsWithDeprecated);
		
		assertTrue(SymbolsUtil.isDeprecated(symbolInformation));
		
		symbolInformation.setTags(symbolTagsWithoutDeprecated);
		
		assertFalse(SymbolsUtil.isDeprecated(symbolInformation));
	}
	
	@Test
	public void testDeprecatedCheckForWorkspaceSymbol() {
		var workspaceSymbol = new WorkspaceSymbol();
		
		assertFalse(SymbolsUtil.isDeprecated(workspaceSymbol));
		
		workspaceSymbol.setTags(symbolTagsWithDeprecated);
		
		assertTrue(SymbolsUtil.isDeprecated(workspaceSymbol));
		
		workspaceSymbol.setTags(symbolTagsWithoutDeprecated);
		
		assertFalse(SymbolsUtil.isDeprecated(workspaceSymbol));
	}
	
	@Test
	public void testDeprecatedCheckForDocumentSymbol() {
		var documentSymbol = new DocumentSymbol();
		
		assertFalse(SymbolsUtil.isDeprecated(documentSymbol));
		
		documentSymbol.setDeprecated(true);
		
		assertTrue(SymbolsUtil.isDeprecated(documentSymbol));
		
		documentSymbol = new DocumentSymbol();
		documentSymbol.setTags(symbolTagsWithDeprecated);
		
		assertTrue(SymbolsUtil.isDeprecated(documentSymbol));
		
		documentSymbol.setTags(symbolTagsWithoutDeprecated);
		
		assertFalse(SymbolsUtil.isDeprecated(documentSymbol));
	}

}
