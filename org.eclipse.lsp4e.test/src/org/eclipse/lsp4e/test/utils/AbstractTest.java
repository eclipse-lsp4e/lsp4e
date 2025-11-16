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

import java.io.PrintStream;

import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.ServerCapabilities;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test base class that configures a {@link AllCleanExtension} and a
 * {@link TestInfoExtension} and works around a surefire-plugin issue which
 * suppresses output to stderr
 */
public abstract class AbstractTest {

	private static PrintStream originalSystemErr;

	private static final boolean isExecutedBySurefirePlugin = System.getProperty("surefire.real.class.path") != null;

	@BeforeAll
	public static void setUpSystemErrRedirection() throws Exception {
		if (isExecutedBySurefirePlugin) {
			// redirect stderr to stdout during test execution as it is otherwise suppressed
			// by the surefire-plugin
			originalSystemErr = System.err;
			System.setErr(System.out);
		}
	}

	@AfterAll
	public static void tearDownSystemErrRedirection() throws Exception {
		if (isExecutedBySurefirePlugin) {
			System.setErr(originalSystemErr);
		}
	}

	@RegisterExtension
	@Order(1)
	public final AllCleanExtension allCleanRule = new AllCleanExtension(this::getServerCapabilities);
	
	@RegisterExtension
	@Order(0)
	public final TestInfoExtension testInfo = new TestInfoExtension();

	/**
	 * Override if required, used by {@link #allCleanRule}
	 */
	protected ServerCapabilities getServerCapabilities() {
		return MockLanguageServer.defaultServerCapabilities();
	}
}
