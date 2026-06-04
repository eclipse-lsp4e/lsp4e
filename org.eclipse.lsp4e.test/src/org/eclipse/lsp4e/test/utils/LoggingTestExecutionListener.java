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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;

/**
 * Log test execution events.
 * This somewhat duplicates the default reporting from surefire but has the advantage
 * that you also get this output when running the tests through Eclipse.
 */
public class LoggingTestExecutionListener implements TestExecutionListener {

	private Logger LOGGER = System.getLogger(LoggingTestExecutionListener.class.getSimpleName());

	@Override
	public void executionStarted(TestIdentifier testIdentifier) {
		LOGGER.log(Level.INFO, "Starting {0}", testIdentifier.getDisplayName());
	}

	@Override
	public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
		LOGGER.log(Level.INFO, "Finished {0}: {1}", testIdentifier.getDisplayName(), testExecutionResult.getStatus());
	}

	@Override
	public void executionSkipped(TestIdentifier testIdentifier, String reason) {
		LOGGER.log(Level.INFO, "Skipped {0}: {1}", testIdentifier.getDisplayName(), reason);
	}

}
