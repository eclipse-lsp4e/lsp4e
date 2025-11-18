/*******************************************************************************
 * Copyright (c) 2024 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import java.lang.reflect.Method;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Test base class that provides a new unique temporary test project for each @org.junit.Test run
 */
public abstract class AbstractTestWithProject extends AbstractTest {
	protected IProject project;

	@BeforeEach
	public void setUpProject(TestInfo testInfo) throws Exception {
		String testClass = testInfo.getTestClass().map(Class::getSimpleName).orElse("UnknownTestClass");
		String testMethod = testInfo.getTestMethod().map(Method::getName).orElse("UnknownTestMethod");
		String projectName = testClass + "_" + testMethod + "_" + System.currentTimeMillis();
		project = TestUtils.createProject(projectName);
	}

	@AfterEach
	public void tearDownProject() throws Exception {
		if (project != null && project.exists()) {
			deleteProjectWithRetries(project, 10, 500);
		}
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
