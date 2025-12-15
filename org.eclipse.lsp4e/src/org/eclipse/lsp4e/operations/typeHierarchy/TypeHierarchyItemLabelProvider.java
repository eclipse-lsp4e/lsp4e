/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.swt.graphics.Image;

public class TypeHierarchyItemLabelProvider extends LabelProvider implements IStyledLabelProvider {

	@Override
	public String getText(Object element) {
		if (element instanceof TypeHierarchyItem item) {
			return item.getName();
		}
		return super.getText(element);
	}

	@Override
	public @Nullable Image getImage(@Nullable Object element) {
		if (element instanceof TypeHierarchyItem item) {
			return LSPImages.getImageFor(item.getKind(), item.getTags());
		}
		return element == null ? null : super.getImage(element);
	}

	@Override
	public StyledString getStyledText(@Nullable Object element) {
		if (element instanceof TypeHierarchyItem item) {
			return new StyledString(item.getName());
		} else if (element instanceof String s) {
			return new StyledString(s);
		}
		return new StyledString();
	}

}
