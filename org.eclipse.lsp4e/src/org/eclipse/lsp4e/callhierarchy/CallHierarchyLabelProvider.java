/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG (http://www.avaloq.com).
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Andrew Lamb (Avaloq Group AG) - Initial implementation
 *******************************************************************************/

package org.eclipse.lsp4e.callhierarchy;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.swt.graphics.Image;

/**
 * Label provider for the call hierarchy tree view.
 */
public class CallHierarchyLabelProvider extends LabelProvider implements IStyledLabelProvider {

	@Override
	public @Nullable Image getImage(final @Nullable Object element) {
		if (element instanceof CallHierarchyViewTreeNode treeNode) {
			CallHierarchyItem callContainer = treeNode.getCallContainer();
			Image res = LSPImages.imageFromSymbolKind(callContainer.getKind());
			if (res != null) {
				return res;
			}
		}
		return element == null ? null : super.getImage(element);
	}

	@Override
	public @Nullable StyledString getStyledText(final @Nullable Object element) {
		if (element instanceof CallHierarchyViewTreeNode treeNode) {
			CallHierarchyItem callContainer = treeNode.getCallContainer();
			final var styledString = new StyledString();
			appendName(styledString, callContainer.getName());
			if (callContainer.getDetail() != null) {
				appendDetail(styledString, callContainer.getDetail());
			}
			return styledString;
		} else if (element instanceof String s) {
			return new StyledString(s);
		}
		return null;
	}

	/**
	 * Append the given call container name to the given styled string.
	 *
	 * @param styledString
	 *            the styled string to append to.
	 * @param name
	 *            the call container name to append.
	 */
	protected void appendName(final StyledString styledString, final String name) {
		// This implementation is specific to the way the symbol detail is encoded in
		// the call container name (following VSCode as defacto standard).
		int colon = name.lastIndexOf(':');
		if (colon >= 0) {
			styledString.append(name.substring(0, colon));
			styledString.append(name.substring(colon), StyledString.DECORATIONS_STYLER);
		} else {
			styledString.append(name);
		}
	}

	/**
	 * Append the given call container detail to the given styled string.
	 *
	 * @param styledString
	 *            the styled string to append to.
	 * @param detail
	 *            the call container detail to append.
	 */
	protected void appendDetail(final StyledString styledString, final String detail) {
		styledString.append(" - ", StyledString.QUALIFIER_STYLER); //$NON-NLS-1$
		styledString.append(detail, StyledString.QUALIFIER_STYLER);
	}
}
