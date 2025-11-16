/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import java.util.function.Supplier;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.ConnectDocumentToLanguageServerSetupParticipant;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.tests.mock.MockConnectionProvider;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.intro.IIntroPart;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class AllCleanExtension implements BeforeEachCallback, AfterEachCallback {

	private final Supplier<ServerCapabilities> serverConfigurer;

	public AllCleanExtension() {
		this.serverConfigurer = MockLanguageServer::defaultServerCapabilities;
	}

	public AllCleanExtension(final Supplier<ServerCapabilities> serverConfigurer) {
		this.serverConfigurer = serverConfigurer;
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		IIntroPart intro = PlatformUI.getWorkbench().getIntroManager().getIntro();
		if (intro != null) {
			PlatformUI.getWorkbench().getIntroManager().closeIntro(intro);
		}
		clear();
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		clear();
	}

	private void clear() {
		MockLanguageServer.INSTANCE.setTimeToProceedQueries(0);
		// Give the platform three attempts to shut down windows
		for (int i = 3; i > 0 && !UI.getActivePage().closeAllEditors(false); i--) {}
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			try {
				project.delete(IResource.FORCE, null);
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		MockLanguageServer.INSTANCE.waitBeforeTearDown();
		LanguageServiceAccessor.clearStartedServers();
		MockLanguageServer.reset(this.serverConfigurer);
		MockConnectionProvider.cancellations.clear();
		TestUtils.tearDown();
	}

}
