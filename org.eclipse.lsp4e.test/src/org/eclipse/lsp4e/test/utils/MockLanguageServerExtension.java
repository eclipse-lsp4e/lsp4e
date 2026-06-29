/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.ConnectDocumentToLanguageServerSetupParticipant;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.tests.mock.MockConnectionProvider;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.ui.UI;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

/**
 * Allows test cases to define a parameter of type
 * {@link MockLanguageServerFactory}, which they can use to tailor the behavior
 * of all {@link MockLanguageServer} launched during the execution of the test
 * case.
 * 
 * This extension also cleans up thoroughly after each test execution, e.g.,
 * deleting all projects, clearing all Language Servers, etc.
 */
public class MockLanguageServerExtension implements ParameterResolver, AfterEachCallback {

	private static final String KEY = "factory";

	private static final Namespace NAMESPACE = Namespace.create(MockLanguageServerExtension.class);

	@Override
	public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return parameterContext.getParameter().getType().equals(MockLanguageServerFactory.class);
	}

	@Override
	public @Nullable Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
			throws ParameterResolutionException {
		// Store it, just in case.
		MockLanguageServerFactory factory = extensionContext.getStore(NAMESPACE).computeIfAbsent(KEY, __ -> {
			return new MockLanguageServerFactory();
		}, MockLanguageServerFactory.class);
		MockConnectionProvider.factory = factory;

		return factory;
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		MockLanguageServerFactory factory = MockConnectionProvider.factory;
		for (MockLanguageServer server : factory.getServers()) {
			// Reset delay to zero, otherwise servers will be slow to respond to shutdown
			// request.
			server.setTimeToProceedQueries(0);
			// Wait for all in-flight requests to finish.
			server.waitBeforeTearDown();
		}

		// Make sure there are no pending document setups
		ConnectDocumentToLanguageServerSetupParticipant.waitForAll();

		// Give the platform three attempts to close all editors
		for (int i = 3; i > 0 && !UI.getActivePage().closeAllEditors(false); i--) {
		}

		// Now delete all projects
		for (IProject project : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
			deleteProjectWithRetries(project, 10, 500);
		}

		// Cleanup any started servers
		LanguageServiceAccessor.clearStartedServers();

		// Restore factory
		MockConnectionProvider.factory = new MockLanguageServerFactory();
	}

	/**
	 * Mitigation for potential
	 * <code>java.nio.file.FileSystemException: The process cannot access the file because it is being used by another process</code>
	 * when deleting a project.
	 */
	private static void deleteProjectWithRetries(IProject project, int maxAttempts, long delayMillis)
			throws CoreException {
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				if (!project.exists()) {
					break;
				}
				project.close(null);
				project.delete(IResource.FORCE | IResource.ALWAYS_DELETE_PROJECT_CONTENT, null);
				break;
			} catch (CoreException ex) {
				if (attempt == maxAttempts) {
					throw ex;
				}
				try {
					Thread.sleep(delayMillis);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					ex.printStackTrace();
					break;
				}
			}
		}
	}

}
