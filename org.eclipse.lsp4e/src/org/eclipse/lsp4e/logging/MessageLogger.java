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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

/**
 * Logs messages to a log file and/or an Eclipse Console.
 */
public class MessageLogger {

	private final @Nullable Path logFile;
	private @Nullable MessageConsoleStream consoleStream;
	private boolean logToFile;
	private boolean logToConsole;
	private final String serverId;
	private final String logToFilePreferenceKey;
	private final String logToConsolePreferenceKey;
	private IPropertyChangeListener prefChangeListener;
	private IPreferenceStore store;
	private String source;

	public MessageLogger(String serverId, String source) {
		this.serverId = serverId;
		this.source = source;
		store = LanguageServerPlugin.getDefault().getPreferenceStore();
		logToFilePreferenceKey = LoggingUtils.lsToFileLoggingId(serverId);
		logToConsolePreferenceKey = LoggingUtils.lsToConsoleLoggingId(serverId);
		logToFile = store.getBoolean(logToFilePreferenceKey);
		logToConsole = store.getBoolean(logToConsolePreferenceKey);

		// Dynamically enable/disable logging if the preference changes.
		// This only works if logging was enabled initially.
		prefChangeListener = event -> {
			if (event.getProperty().equals(logToFilePreferenceKey) && event.getNewValue() instanceof Boolean newValue) {
				logToFile = newValue;
			} else if (event.getProperty().equals(logToConsolePreferenceKey)
					&& event.getNewValue() instanceof Boolean newValue) {
				logToConsole = newValue;
			}
		};
		store.addPropertyChangeListener(prefChangeListener);
		this.logFile = getLogFile();
	}

	public boolean shouldLog() {
		return logToFile || logToConsole;
	}

	public void log(String message) {
		if (logToConsole) {
			logToConsole(message);
		}
		if (logToFile) {
			logToFile(message);
		}
	}

	private void logToFile(String string) {
		final var logFile = this.logFile;
		if (logFile == null) {
			return;
		}
		try {
			Files.write(logFile, string.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private @Nullable Path getLogFile() {
		if (logFile != null) {
			return logFile;
		}
		Path logFolder = LoggingUtils.getLogDirectory();
		if (logFolder == null) {
			return null;
		}
		final Path file = logFolder.resolve(serverId + "-" + source + ".log"); //$NON-NLS-1$ //$NON-NLS-2$
		if (Files.exists(file) && !(Files.isRegularFile(file) && Files.isWritable(file))) {
			LanguageServerPlugin.logError("Log file '%s' exists but is no writable file".formatted(file.toString())); //$NON-NLS-1$
			return null;
		}
		return file;
	}

	private void logToConsole(String string) {
		var consoleStream = this.consoleStream;
		if (consoleStream == null || consoleStream.isClosed()) {
			consoleStream = this.consoleStream = findConsole().newMessageStream();
		}
		consoleStream.print(string);
	}

	private MessageConsole findConsole() {
		String consoleName = NLS.bind(Messages.LSConsoleName, serverId, source);
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();
		for (IConsole existing : conMan.getConsoles()) {
			if (consoleName.equals(existing.getName()))
				return (MessageConsole) existing;
		}
		// no console found, so create a new one
		final var myConsole = new MessageConsole(consoleName, null);
		// limit text buffer size to prevent OOM
		myConsole.setWaterMarks(80_000, 800_000);
		conMan.addConsoles(new IConsole[] { myConsole });
		return myConsole;
	}

	public void dispose() throws IOException {
		store.removePropertyChangeListener(prefChangeListener);
		if (consoleStream != null) {
			consoleStream.close();
		}
	}

}
