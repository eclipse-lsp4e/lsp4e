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

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.lsp4e.ui.AbstractLsp4eLabelProvider;
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

	private static class TestLabelProvider extends AbstractLsp4eLabelProvider {
		// increase method visibility for the following test
		@Override
		public @Nullable Image getImageFor(@Nullable SymbolKind symbolKind, @Nullable List<SymbolTag> symbolTags) {
			return super.getImageFor(symbolKind, symbolTags);
		}
	}
	
	// Deprecated is used to test the case where no visibility tag is available, that should default to the standard symbol icon
	@ParameterizedTest
	@EnumSource(value=SymbolTag.class, mode=Mode.INCLUDE, names= { "Private", "Package", "Protected", "Public",
			"Internal", "File", "Deprecated"})
	public void testVisibilityOverlayImagesForFieldsAndMethodsAvailable(SymbolTag tag) {
		var symbolTags = List.of(tag);
		TestLabelProvider labelProvider = new TestLabelProvider();
		
		Image fieldImage = labelProvider.getImageFor(SymbolKind.Field, symbolTags);
		Image methodImage = labelProvider.getImageFor(SymbolKind.Method, symbolTags);
		
		assertNotNull(fieldImage);
		assertNotNull(methodImage);
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
