/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.viewers.DecoratingStyledCellLabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.search.internal.ui.text.DecoratingFileSearchLabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

public class FileAndURIMatchLabelProvider extends DecoratingStyledCellLabelProvider {

	public static class FileAndURIMatchBaseLabelProvider implements IStyledLabelProvider {
		private IStyledLabelProvider resourceMatchDelegate;

		public FileAndURIMatchBaseLabelProvider(IStyledLabelProvider styledStringProvider) {
			this.resourceMatchDelegate = styledStringProvider;
		}

		@Override
		public void addListener(ILabelProviderListener listener) {
			this.resourceMatchDelegate.addListener(listener);
		}

		@Override
		public void dispose() {
			this.resourceMatchDelegate.dispose();
		}

		@Override
		public boolean isLabelProperty(Object element, String property) {
			if (canDelegate(element)) {
				return resourceMatchDelegate.isLabelProperty(element, property);
			}
			return false;
		}

		@Override
		public void removeListener(ILabelProviderListener listener) {
			resourceMatchDelegate.removeListener(listener);
		}

		@Override
		public @Nullable StyledString getStyledText(@Nullable Object element) {
			if (canDelegate(element)) {
				return resourceMatchDelegate.getStyledText(element);
			}
			if (element instanceof URI uri) {
				if ("file".equals(uri.getScheme())) { //$NON-NLS-1$
					return new StyledString(uri.getPath());
				} else {
					try {
						final var trimmedURI = new URI(uri.getScheme(),
							uri.getAuthority(),
							uri.getPath(),
							null, // Ignore the query part of the input url
							uri.getFragment());
						final var res = new StyledString(trimmedURI.toString());
						if (uri.getQuery() != null) {
							res.append("?" + uri.getRawQuery(), StyledString.QUALIFIER_STYLER); //$NON-NLS-1$
						}
						return res;
					} catch (URISyntaxException ex) {
						return new StyledString(uri.toString());
					}
				}
			}
			if (element instanceof URIMatch match) {
				return new StyledString(match.getOffset() + ".." + (match.getOffset() + match.getLength())); //$NON-NLS-1$
			}
			return null;
		}

		@Override
		public @Nullable Image getImage(@Nullable Object element) {
			if (canDelegate(element)) {
				return resourceMatchDelegate.getImage(element);
			}
			if (element instanceof URI uri && "file".equals(uri.getScheme())) { //$NON-NLS-1$
				return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_FILE);
			}
			return null;
		}

		private boolean canDelegate(@Nullable Object element) {
			return !(element instanceof URI || element instanceof URIMatch);
		}
	}

	public FileAndURIMatchLabelProvider(FileAndURIMatchBaseLabelProvider baseLabelProvider, DecoratingFileSearchLabelProvider fileMatchLabelProvider) {
		super(baseLabelProvider, fileMatchLabelProvider.getLabelDecorator(), fileMatchLabelProvider.getDecorationContext());
	}

}