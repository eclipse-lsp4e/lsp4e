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
package org.eclipse.lsp4e.logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.LanguageServerPlugin;

/**
 * Utility methods for logging the communication with a language server.
 */
public class LoggingUtils {

	private static final String LOG_FOLDER = "languageServers-log"; //$NON-NLS-1$

	private static final String FILE_KEY = "file.logging.enabled"; //$NON-NLS-1$
	private static final String STDERR_KEY = "stderr.logging.enabled"; //$NON-NLS-1$

	public static @Nullable Path getLogDirectory() {
		IPath root = ResourcesPlugin.getWorkspace().getRoot().getLocation();
		if (root == null) {
			return null;
		}
		Path logFolder = root.toPath().resolve(LOG_FOLDER);
		try {
			Files.createDirectories(logFolder);
			return logFolder;
		} catch (IOException e) {
			LanguageServerPlugin.logError("Failed to create log directory: '%s'".formatted(logFolder.toString()), e); //$NON-NLS-1$
			return null;
		}
	}

	/**
	 * Converts a language server ID to the preference ID for logging communications
	 * to file from the language server
	 *
	 * @return language server's preference ID for file logging
	 */
	public static String lsToFileLoggingId(String serverId) {
		return serverId + "." + FILE_KEY;//$NON-NLS-1$
	}

	/**
	 * Converts a language server ID to the preference ID for logging communications
	 * to console from the language server
	 *
	 * @return language server's preference ID for console logging
	 */
	public static String lsToConsoleLoggingId(String serverId) {
		return serverId + "." + STDERR_KEY;//$NON-NLS-1$
	}

	/**
	 * Returns whether currently created connections should be logged to file or a
	 * console.
	 *
	 * @return If connections should be logged
	 */
	public static boolean shouldLog(String serverId) {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		return store.getBoolean(lsToFileLoggingId(serverId)) || store.getBoolean(lsToConsoleLoggingId(serverId));
	}

}
