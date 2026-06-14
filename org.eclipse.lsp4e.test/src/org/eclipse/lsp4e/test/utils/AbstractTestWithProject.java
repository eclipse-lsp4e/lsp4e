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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

/**
 * Test base class that provides a new unique temporary test project for each @org.junit.Test run
 */
public abstract class AbstractTestWithProject {
	protected IProject project;

	@BeforeEach
	public void setUpProject(TestInfo testInfo) throws Exception {
		String testClass = testInfo.getTestClass().map(Class::getSimpleName).orElse("UnknownTestClass");
		String testMethod = testInfo.getTestMethod().map(Method::getName).orElse("UnknownTestMethod");
		String projectName = testClass + "_" + testMethod + "_" + System.currentTimeMillis();
		project = TestUtils.createProject(projectName);
	}

}
