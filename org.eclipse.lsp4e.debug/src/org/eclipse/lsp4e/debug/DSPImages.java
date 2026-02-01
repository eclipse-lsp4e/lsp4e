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

import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.osgi.framework.Bundle;

public final class DSPImages {
	private DSPImages() {
		// private constructor to avoid instances, requested by sonar
	}

	private static @Nullable ImageRegistry imageRegistry;
	private static final String ICONS_PATH = "$nl$/icons/"; //$NON-NLS-1$
	private static final String VIEWS = ICONS_PATH + "view16/";

	public static final String IMG_VIEW_DEBUGGER_TAB = "IMG_DEBUGGER_TAB"; //$NON-NLS-1$

	public static void initialize(ImageRegistry registry) {
		imageRegistry = registry;
		declareRegistryImage(IMG_VIEW_DEBUGGER_TAB, VIEWS + "debugger_tab.svg");
	}

	private static void declareRegistryImage(String key, String path) {
		ImageDescriptor desc = ImageDescriptor.getMissingImageDescriptor();
		Bundle bundle = Platform.getBundle(DSPPlugin.PLUGIN_ID);
		URL url = null;
		if (bundle != null) {
			url = FileLocator.find(bundle, new Path(path), null);
			if (url != null) {
				desc = ImageDescriptor.createFromURL(url);
			}
		}
		getImageRegistry().put(key, desc);
	}

	public static @Nullable Image get(String key) {
		return getImageRegistry().get(key);
	}

	/**
	 * Helper method to access the image registry from the JavaPlugin class.
	 */
	static ImageRegistry getImageRegistry() {
		ImageRegistry imageRegistry = DSPImages.imageRegistry;
		if (imageRegistry == null) {
			imageRegistry = DSPImages.imageRegistry = DSPPlugin.getDefault().getImageRegistry();
		}
		return imageRegistry;
	}
}
