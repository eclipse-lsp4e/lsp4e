/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/

package org.eclipse.lsp4e;

import java.net.URI;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * <p>Caches the {@link LSPEclipseUtils#findResourceFor(URI)} call. <code>ResourceForUriCache.getInstance().get(URI)</code>
 * can an be used instead of a direct call to LSPEclipseUtils.findResourceFor(URI),
 * because the call is very time consuming.
 *
 * <p>NOTE: In case a resource has been moved or deleted the entry will not be removed automatically.
 * It's up to the caller to check if the resource is accessible.
 *
 * <p>The cache is limited to 100 resource elements. It uses least-recently-used eviction if limit exceeds.
 * The cache will try to evict entries that haven't been used recently.
 * Therefore entries can be removed before the limit exceeds.
 */
public final class ResourceForUriCache {
	private static final Cache<URI, IResource> cache =  CacheBuilder.newBuilder().maximumSize(100).build();
	private static final ResourceForUriCache INSTANCE = new ResourceForUriCache();

	private ResourceForUriCache() {
		//  use getInstance()
	}

	public static ResourceForUriCache getInstance() {
		return INSTANCE;
	}

	/**
	 * <p>Returns the cached IResource for the given URI. Tries to determine the IResource
	 * if it's not already in the cache. Returns NULL if the IResource could not be determined,
	 * e.g. the URI points to a file outside the workspace.
	 *
	 * <p>NOTE: In case a resource has been moved or deleted the entry will not be removed automatically.
	 * It's up to the caller to check if the resource is accessible.
	 * @param uri
	 * @return IResource or NULL
	 */
	@Nullable
	public IResource get(@Nullable URI uri) {
		IResource resource = null;
		if (uri != null) {
			resource = cache.getIfPresent(uri);
			if (resource != null) {
				return resource;
			}
			resource = LSPEclipseUtils.findResourceFor(uri);
			if (resource != null) {
				cache.put(uri, resource);
			}
		}
		return resource;
	}
}


