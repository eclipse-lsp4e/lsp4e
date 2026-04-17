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
package org.eclipse.lsp4e.operations.symbols.internal;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4e.ui.SymbolIconProvider;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;

public class SymbolIconProviderRegistry {

	private static final String EXTENSION_POINT_ID = LanguageServerPlugin.PLUGIN_ID + ".symbolIconsProvider"; //$NON-NLS-1$

	// default icon provider from LSP4E
	private final SymbolIconProvider defaultIconProvider = new SymbolIconProvider();

	// symbol icon providers from extensions, cached per content type ID
	private final Map<String, SymbolIconProvider> cachedIconProviders = new HashMap<>();

	private SymbolIconProviderRegistry() {
		loadExtensions();
	}

	/**
	 * Initialization-on-demand holder: the JVM guarantees that this nested class
	 * is loaded and initialized exactly once, in a thread-safe manner, the first
	 * time {@link #get()} is called.
	 */
	private static final class Holder {
		static final SymbolIconProviderRegistry INSTANCE = new SymbolIconProviderRegistry();
	}

	private static SymbolIconProviderRegistry get() {
		return Holder.INSTANCE;
	}

	private void loadExtensions() {
		IExtensionPoint extensionPoint = Platform.getExtensionRegistry().getExtensionPoint(EXTENSION_POINT_ID);

		if (extensionPoint == null) {
			Platform.getLog(getClass()).error("No extension point found for ID " + EXTENSION_POINT_ID); //$NON-NLS-1$
			return;
		}

		for (IConfigurationElement configurationElement : extensionPoint.getConfigurationElements()) {
			if ("iconProvider".equals(configurationElement.getName())) { //$NON-NLS-1$
				String className = configurationElement.getAttribute("class"); //$NON-NLS-1$

				if (className == null || className.isBlank()) {
					continue;
				}

				SymbolIconProvider iconProvider;
				try {
					iconProvider = (SymbolIconProvider) configurationElement.createExecutableExtension("class"); //$NON-NLS-1$
				} catch (CoreException | ClassCastException e) {
					Platform.getLog(getClass()).error("Failed instantiating class " + className, e); //$NON-NLS-1$
					continue;
				}

				for ( IConfigurationElement contentTypeConfigElement : configurationElement.getChildren("contentType")) { //$NON-NLS-1$
					String contentTypeId = contentTypeConfigElement.getAttribute("contentTypeId"); //$NON-NLS-1$

					if (contentTypeId == null || contentTypeId.isBlank()) {
						continue;
					}

					cachedIconProviders.put(contentTypeId, iconProvider);
				}
			}
		}
	}

	public static SymbolIconProvider getSymbolIconProviderFor(Object symbol) {
		return get().getIconProvider(symbol);
	}

	private SymbolIconProvider getIconProvider(Object symbol) {
		URI uri = getUri(symbol);
		if (uri == null) {
			return defaultIconProvider;
		}

		String fileName = null;
		try {
			fileName = Path.of(uri.getPath()).getFileName().toString();
		} catch (Exception e) {
			Platform.getLog(getClass()).warn("Failed to parse file name from URI " + uri, e); //$NON-NLS-1$
			return defaultIconProvider;
		}

		IContentType[] contentTypes = Platform.getContentTypeManager().findContentTypesFor(fileName);
		for (IContentType contentType : contentTypes) {
			IContentType candidate = contentType;
			while (candidate != null) {
				SymbolIconProvider iconProvider = cachedIconProviders.get(candidate.getId());
				if (iconProvider != null) {
					return iconProvider;
				}
				candidate = candidate.getBaseType();
			}
		}

		return defaultIconProvider;
	}

	private @Nullable URI getUri(Object symbol) {
		if (symbol instanceof SymbolInformation info)
			return toUri(info.getLocation().getUri());
		if (symbol instanceof WorkspaceSymbol ws)
			return toUri(ws.getLocation().map(Location::getUri, WorkspaceSymbolLocation::getUri));
		if (symbol instanceof DocumentSymbolWithURI s)
			return s.uri;
		if (symbol instanceof TypeHierarchyItem item) // for subclasses handling TH
			return toUri(item.getUri());
		if (symbol instanceof CallHierarchyItem item) // for subclasses handling CH
			return toUri(item.getUri());
		return null; // plain DocumentSymbol — no URI
	}

	private @Nullable URI toUri(String uri) {
		if (uri == null) {
			return null;
		}

		try {
			return URI.create(uri);
		} catch (IllegalArgumentException e) {
			Platform.getLog(getClass()).warn("Failed to parse URI " + uri, e); //$NON-NLS-1$
			return null;
		}
	}
}
