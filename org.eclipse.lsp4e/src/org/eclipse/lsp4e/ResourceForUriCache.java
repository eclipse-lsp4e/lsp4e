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

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.net.URI;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.Nullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Caches the {@link LSPEclipseUtils#findResourceFor(URI)} call. <code>ResourceForUriCache.getInstance().get(URI)</code>
 * can an be used instead of a direct call to LSPEclipseUtils.findResourceFor(URI),
 * because the call is very time consuming.
 *
 * The cache is limited to 100 resources elements.
 * Resources are removed in case the URI has changed due to a resource move or delete operation.
 */
public final class ResourceForUriCache implements IResourceChangeListener {
	private static final int URI_CHANGED = IResourceDelta.REPLACED |
			IResourceDelta.MOVED_FROM |
			IResourceDelta.MOVED_TO |
			IResourceDelta.REMOVED |
			IResourceDelta.REMOVED_PHANTOM;

	private static final Cache<URI, IResource> cache =  CacheBuilder.newBuilder().maximumSize(100).build();
	private static @Nullable ResourceForUriCache instance;

	private ResourceForUriCache() {
		//  use getInstance()
	}

	public static synchronized ResourceForUriCache getInstance() {
		if (instance == null) {
			instance = new ResourceForUriCache();
		}
		return castNonNull(instance);
	}

	@Nullable
	public synchronized IResource get(@Nullable URI uri) {
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

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		if (event.getDelta() != null) {
			try {
				event.getDelta().accept(delta -> {
					if ((delta.getKind() | URI_CHANGED) == URI_CHANGED) {
						var uri = delta.getResource().getLocationURI();
						if (uri != null) {
							cache.invalidate(uri);
						}
					}
					return true;
				});
			} catch (CoreException e) {
				Platform.getLog(getClass()).log(e.getStatus());
			}
		}
	}

}


