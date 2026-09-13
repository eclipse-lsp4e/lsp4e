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
import java.io.Writer;

import org.eclipse.lsp4e.ui.Messages;

/**
 * A {@link Writer} which can used to log messages sent between LSP4E and a Language Server.
 */
public class LoggingWriter extends Writer {

	private MessageLogger messageLogger;

	public LoggingWriter(String serverId) {
		messageLogger = new MessageLogger(serverId, Messages.LSLogSourceMessages);
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		if (messageLogger.shouldLog()) {
			String message = new String(cbuf, off, len);
			messageLogger.log(message);
		}
	}

	@Override
	public void flush() throws IOException {
		// Nop
	}

	@Override
	public void close() throws IOException {
		messageLogger.dispose();
	}

}
