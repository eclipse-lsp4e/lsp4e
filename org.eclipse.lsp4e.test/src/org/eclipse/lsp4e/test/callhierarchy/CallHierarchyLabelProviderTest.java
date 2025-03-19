/*******************************************************************************
 * Copyright (c) 2025 Advantest Europe GmbH (https://www.advantest.com/).
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Dietrich Travkin (Solunar GmbH) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.callhierarchy;

import static org.junit.Assert.*;

import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.callhierarchy.CallHierarchyLabelProvider;
import org.eclipse.lsp4e.callhierarchy.CallHierarchyViewTreeNode;
import org.eclipse.lsp4e.test.utils.AbstractTest;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.swt.custom.StyleRange;
import org.junit.BeforeClass;
import org.junit.Test;

public class CallHierarchyLabelProviderTest extends AbstractTest {
	
	private static CallHierarchyLabelProvider labelProvider = null;
	
	@BeforeClass
	public static void setUp() {
		labelProvider = new CallHierarchyLabelProvider();
	}
	
	@Test
	public void testSimpleText() {
		String name = "Some arbitrary name";
		
		StyledString label =  labelProvider.getStyledText(name);
		
		assertEquals(name, label.getString());
		
		StyledString expectedStyledText = new StyledString();
		expectedStyledText.append(name);
		
		assertEqualStyles(expectedStyledText, label);
	}
	
	@Test
	public void testCallHierarchyItemLabelWithSimpleName() {
		String name = "calledMethod()";
		
		CallHierarchyItem callHierarchyItem = new CallHierarchyItem();
		callHierarchyItem.setName(name);
		CallHierarchyViewTreeNode node = new CallHierarchyViewTreeNode(callHierarchyItem);
		
		StyledString label =  labelProvider.getStyledText(node);
		
		assertEquals(name, label.getString());
		
		StyledString expectedStyledText = new StyledString();
		expectedStyledText.append(name);
		
		assertEqualStyles(expectedStyledText, label);
	}
	
	@Test
	public void testCallHierarchyItemLabelWithoutDetail() {
		String name = "doSomething(ClassName<T> param1, double param2) : String";
		
		CallHierarchyItem callHierarchyItem = new CallHierarchyItem();
		callHierarchyItem.setName(name);
		CallHierarchyViewTreeNode node = new CallHierarchyViewTreeNode(callHierarchyItem);
		
		StyledString label =  labelProvider.getStyledText(node);
		
		assertEquals(name, label.getString());
		
		StyledString expectedStyledText = new StyledString();
		expectedStyledText.append("doSomething(ClassName<T> param1, double param2) ");
		expectedStyledText.append(": String", StyledString.DECORATIONS_STYLER);
		
		assertEqualStyles(expectedStyledText, label);
	}
	
	@Test
	public void testCompleteCallHierarchyItemLabel() {
		String name = "doSomething(int a, String b) : void";
		String detail = "org.eclipse.lsp4e.SomeClass";
		
		CallHierarchyItem callHierarchyItem = new CallHierarchyItem();
		callHierarchyItem.setName(name);
		callHierarchyItem.setDetail(detail);
		CallHierarchyViewTreeNode node = new CallHierarchyViewTreeNode(callHierarchyItem);
		
		StyledString label =  labelProvider.getStyledText(node);
		
		assertEquals("doSomething(int a, String b) : void - org.eclipse.lsp4e.SomeClass", label.getString());
		
		StyledString expectedStyledText = new StyledString();
		expectedStyledText.append("doSomething(int a, String b) ");
		expectedStyledText.append(": void", StyledString.DECORATIONS_STYLER);
		expectedStyledText.append(" - org.eclipse.lsp4e.SomeClass", StyledString.QUALIFIER_STYLER);
		
		assertEqualStyles(expectedStyledText, label);
	}
	
	private void assertEqualStyles(StyledString expectedStyledText, StyledString actualStyledText) {
		assertEquals(expectedStyledText.getStyleRanges().length, actualStyledText.getStyleRanges().length);
		
		for (int i = 0; i < expectedStyledText.getStyleRanges().length; i++) {
			StyleRange expected = expectedStyledText.getStyleRanges()[i];
			StyleRange actual = actualStyledText.getStyleRanges()[i];
			
			assertEquals(expected, actual);
		}
	}
}
