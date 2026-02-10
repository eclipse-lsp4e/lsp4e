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
package org.eclipse.lsp4e.test.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.swt.graphics.Image;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

public class LSPImagesTest {
	
	@ParameterizedTest
	@EnumSource(SymbolKind.class)
	public void testAllImagesForSymbolKindAvailable(SymbolKind kind) {
		Image img = LSPImages.imageFromSymbolKind(kind);
		
		assertNotNull(img);
	}
	
	@ParameterizedTest
	@EnumSource(SymbolTag.class)
	public void testAllOverlayImagesForSymbolTagAvailable(SymbolTag tag) {
		ImageDescriptor descriptor = LSPImages.imageDescriptorOverlayFromSymbolTag(tag);
		Image img = LSPImages.imageOverlayFromSymbolTag(tag);
		
		assertNotNull(descriptor);
		assertNotNull(img);
	}
	
	@ParameterizedTest
	@EnumSource(value=CompletionItemKind.class, mode=Mode.EXCLUDE, names= { "Color", "Event", "Operator" })
	public void testAllImagesForCompletionItemKindAvailable(CompletionItemKind kind) {
		CompletionItem item = new CompletionItem();
		item.setKind(kind);
		
		Image img = LSPImages.imageFromCompletionItem(item);
		
		assertNotNull(img);
	}

}
