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

package org.eclipse.lsp4e.internal;

import java.net.URI;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LSPEclipseUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * <p>NOTE: In case a resource has been moved or deleted the entry will not be removed automatically.
 * It's up to the caller to check if the resource is accessible.
 *
 * <p>The cache is limited to 100 resource elements. It uses least-recently-used eviction if limit exceeds.
 * The cache will try to evict entries that haven't been used recently.
 * Therefore entries can be removed before the limit exceeds.
 */
public final class ResourceForUriCache {
	private static final String FILE_SCHEME = "file"; //$NON-NLS-1$
	private static final Cache<URI, IResource> cache = CacheBuilder.newBuilder().maximumSize(100).build();

	private ResourceForUriCache() {
		// this class shouldn't be instantiated
	}

	/**
	 * <p>Returns the cached IResource for the given URI. Tries to determine the IResource
	 * if it's not already in the cache. Returns NULL if the IResource could not be determined,
	 * e.g. the URI points to a file outside the workspace.
	 *
	 * <p>In case a resource has been moved or deleted, the cache entry will be invalidated,
	 * and this method will re-attempt to find the IResource.
	 * @param uri
	 * @return IResource or NULL
	 */
	@Nullable
	public static IResource get(@Nullable URI uri) {
		// Note: The load method in CacheLoader/LoadingCache cannot be applied here because
		// the load method has to return a non-null value.
		// But it cannot be guaranteed that there can be a IResource fetched for the given URI.
		URI localURI = uri;
		IResource resource = null;
		if (localURI != null) {
			resource = cache.getIfPresent(localURI);
			if (resource != null) {
				if (resource.isAccessible()) {
					return resource;
				}
				cache.invalidate(localURI);
			}
			resource = findResourceFor(localURI);
			if (resource != null) {
				cache.put(localURI, resource);
			}
		}
		return resource;
	}

	@Nullable
	private static IResource findResourceFor(URI uri) {
		if (FILE_SCHEME.equals(uri.getScheme())) {
			IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();

			IFile[] files = wsRoot.findFilesForLocationURI(uri);
			if (files.length > 0) {
				IFile file = LSPEclipseUtils.findMostNested(files);
				if(file!=null) {
					return file;
				}
			}

			return ArrayUtil.findFirst(wsRoot.findContainersForLocationURI(uri));
		} else {
			return Adapters.adapt(uri, IResource.class, true);
		}
	}

}


