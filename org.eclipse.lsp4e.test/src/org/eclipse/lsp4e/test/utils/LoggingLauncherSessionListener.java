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

import java.io.IOException;
import java.io.PrintStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.logging.LogManager;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Set up logging. Redirect stderr to stdout if the tests are executed from
 * surefire-plugin.
 */
public class LoggingLauncherSessionListener implements LauncherSessionListener {

	private PrintStream originalSystemErr;

	private Logger LOGGER = System.getLogger(LoggingLauncherSessionListener.class.getName());

	private final boolean isExecutedBySurefirePlugin = System.getProperty("surefire.real.class.path") != null;

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		if (isExecutedBySurefirePlugin) {
			originalSystemErr = System.err;
			System.setErr(System.out);
		}

		// Explicitly load the JUL config
		System.setProperty("java.util.logging.config.file", "src/jul.properties");
		try {
			LogManager.getLogManager().readConfiguration();
		} catch (SecurityException | IOException e) {
			LOGGER.log(Level.ERROR, "Failed to apply jul.properties", e);
		}
	}

	@Override
	public void launcherSessionClosed(LauncherSession session) {
		if (isExecutedBySurefirePlugin) {
			System.setErr(originalSystemErr);
		}
	}

}
