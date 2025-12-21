/*******************************************************************************
 * Copyright (c) 2005, 2019 QNX Software Systems and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * QNX Software Systems - Initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.debug;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;

public final class DSPImages {
	private DSPImages() {
		// private constructor to avoid instances, requested by sonar
	}

	private static final String NAME_PREFIX = DSPPlugin.PLUGIN_ID + '.';
	private static final int NAME_PREFIX_LENGTH = NAME_PREFIX.length();

	// The plugin registry
	private static ImageRegistry imageRegistry = new ImageRegistry();

	// Subdirectory (under the package containing this class) where 16 color images
	// are
	private static URL fgIconBaseURL;
	static {
		fgIconBaseURL = Platform.getBundle(DSPPlugin.PLUGIN_ID).getEntry("/icons/"); //$NON-NLS-1$
	}

	private static final String T_TABS = "view16/"; //$NON-NLS-1$
	@SuppressWarnings("unused") // none yet, leave for the future
	private static final String T_OBJS = "obj16/"; //$NON-NLS-1$

	public static final String IMG_VIEW_DEBUGGER_TAB = NAME_PREFIX + "debugger_tab.svg"; //$NON-NLS-1$

	public static final ImageDescriptor DESC_TAB_DEBUGGER = createManaged(T_TABS, IMG_VIEW_DEBUGGER_TAB);

	public static void initialize() {
	}

	private static ImageDescriptor createManaged(String prefix, String name) {
		return createManaged(imageRegistry, prefix, name);
	}

	private static ImageDescriptor createManaged(ImageRegistry registry, String prefix, String name) {
		ImageDescriptor result = ImageDescriptor
				.createFromURL(makeIconFileURL(prefix, name.substring(NAME_PREFIX_LENGTH)));
		registry.put(name, result);
		return result;
	}

	public static @Nullable Image get(String key) {
		return imageRegistry.get(key);
	}

	private static @Nullable URL makeIconFileURL(String prefix, String name) {
		final var buffer = new StringBuilder(prefix);
		buffer.append(name);
		try {
			return new URL(fgIconBaseURL, buffer.toString());
		} catch (MalformedURLException e) {
			DSPPlugin.logError(e);
			return null;
		}
	}

	/**
	 * Helper method to access the image registry from the JavaPlugin class.
	 */
	static ImageRegistry getImageRegistry() {
		return imageRegistry;
	}
}
