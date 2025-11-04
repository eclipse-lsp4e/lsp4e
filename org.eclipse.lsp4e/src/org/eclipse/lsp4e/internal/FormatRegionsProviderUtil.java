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

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.format.IFormatRegionsProvider;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

public class FormatRegionsProviderUtil {

	private FormatRegionsProviderUtil() {
		// this class shouldn't be instantiated
	}

	/**
	 * Lookup the {@link IFormatRegionsProvider} for the given server definition id.
	 *
	 * @param serverDefinitionId
	 *            The id the of the language server
	 * @return The found {@link IFormatRegionsProvider} or {@code null}.
	 * @see IFormatRegionsProvider
	 */
	public static @Nullable IFormatRegionsProvider lookup(String serverDefinitionId) {
		final var bundle = FrameworkUtil.getBundle(FormatRegionsProviderUtil.class);
		if (bundle == null) {
			return null;
		}

		var bundleContext = bundle.getBundleContext();
		if (bundleContext == null) {
			return null;
		}

		final ServiceReference<?>[] serviceReferences;
		try {
			String filter = "(serverDefinitionId=" + serverDefinitionId + ")"; //$NON-NLS-1$ //$NON-NLS-2$
			serviceReferences = bundleContext.getAllServiceReferences(IFormatRegionsProvider.class.getName(), filter);
		} catch (InvalidSyntaxException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}

		final ServiceReference<?> reference;
		if (serviceReferences != null) {
			reference = serviceReferences[0];
		} else {
			// Fallback to default
			reference = bundleContext.getServiceReference(IFormatRegionsProvider.class.getName());
		}

		if (reference == null) {
			return null;
		}

		return (IFormatRegionsProvider) bundleContext.getService(reference);
	}

}
