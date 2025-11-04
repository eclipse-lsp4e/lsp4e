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
package org.eclipse.lsp4e.test.format;

import static org.junit.Assert.assertTrue;

import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4e.format.DefaultFormatRegionsProvider;
import org.eclipse.lsp4e.format.IFormatRegionsProvider;
import org.eclipse.lsp4e.internal.FormatRegionsProviderUtil;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;

public class FormatRegionsProviderUtilTest {

	@Test
	public void lookupOnlyDefault() throws Exception {
		var provider = FormatRegionsProviderUtil.lookup("noFormatProviderForThisId"); //$NON-NLS-1$
		assertTrue(provider instanceof DefaultFormatRegionsProvider);
	}

	@Test
	public void lookup() throws Exception {
		// Setup
		IFormatRegionsProvider instance = new IFormatRegionsProvider() {

			@Override
			public IRegion @Nullable [] getFormattingRegions(IDocument document) {
				return null;
			}

		};

		BundleContext bundleContext = FrameworkUtil.getBundle(FormatRegionsProviderUtil.class).getBundleContext();
		final Dictionary<String, Object> props = new Hashtable<>();
		props.put("serverDefinitionId", "foo");
		ServiceRegistration<IFormatRegionsProvider> serviceRegistration = bundleContext
				.registerService(IFormatRegionsProvider.class, instance, props);

		IFormatRegionsProvider provider;
		// There is no matching provider, so we expect the default.
		provider = FormatRegionsProviderUtil.lookup("notMatching");
		assertTrue(provider instanceof DefaultFormatRegionsProvider);

		// There is a matching provider
		provider = FormatRegionsProviderUtil.lookup("foo"); //$NON-NLS-1$
		assertTrue(provider == instance);

		// Cleanup
		serviceRegistration.unregister();
	}

}
