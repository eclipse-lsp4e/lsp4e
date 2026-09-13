/*******************************************************************************
 * Copyright (c) 2018, 2026 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Lucas Bullen (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.logging;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * A {@link StreamConnectionProvider} proxy which can be used to log the raw bytes sent
 * between LSP4E and a Language Server.
 */
public class LoggingStreamConnectionProviderProxy implements StreamConnectionProvider, IAdaptable {

	private final StreamConnectionProvider provider;
	private @Nullable InputStream inputStream;
	private @Nullable OutputStream outputStream;
	private @Nullable InputStream errorStream;
	private final String id;
	private final MessageLogger messageLogger;

	public LoggingStreamConnectionProviderProxy(StreamConnectionProvider provider, String serverId) {
		this.id = serverId;
		this.provider = provider;
		this.messageLogger = new MessageLogger(serverId, Messages.LSLogSourceRaw);
	}

	private enum Direction { LANGUAGE_SERVER_TO_LSP4E, LSP4E_TO_LANGUAGE_SERVER, ERROR_FROM_LANGUAGE_SERVER }

	private String message(Direction direction, byte[] payload) {
		String now = OffsetDateTime.now().toString();
		final var builder = new StringBuilder(payload.length + id.length() + direction.toString().length() + now.length() + 10);
		builder.append("\n["); //$NON-NLS-1$
		builder.append(now);
		builder.append("] "); //$NON-NLS-1$
		builder.append(direction);
		builder.append(' ');
		builder.append(id);
		builder.append(":\n"); //$NON-NLS-1$
		builder.append(new String(payload, StandardCharsets.UTF_8));
		return builder.toString();
	}

	private String errorMessage(byte[] payload) {
		return message(Direction.ERROR_FROM_LANGUAGE_SERVER, payload);
	}

	@Override
	public @Nullable InputStream getInputStream() {
		if (inputStream != null) {
			return inputStream;
		}
		if (provider.getInputStream() != null) {
			inputStream = new FilterInputStream(provider.getInputStream()) {
				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int bytes = super.read(b, off, len);
					final var payload = new byte[bytes];
					System.arraycopy(b, off, payload, 0, bytes);
					if (messageLogger.shouldLog()) {
						String s = message(Direction.LANGUAGE_SERVER_TO_LSP4E, payload);
						messageLogger.log(s);
					}
					return bytes;
				}
			};
		}
		return inputStream;
	}

	@Override
	public <T> @Nullable T getAdapter(@Nullable Class<T> adapter) {
		if(adapter == ProcessHandle.class) {
			return Adapters.adapt(provider, adapter);
		}
		return null;
	}

	@Override
	public @Nullable InputStream getErrorStream() {
		if (errorStream != null) {
			return errorStream;
		}
		if (provider.getErrorStream() != null) {
			errorStream = new FilterInputStream(provider.getErrorStream()) {
				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int bytes = super.read(b, off, len);
					final var payload = new byte[bytes];
					System.arraycopy(b, off, payload, 0, bytes);
					if (messageLogger.shouldLog()) {
						String s = errorMessage(payload);
						messageLogger.log(s);
					}
					return bytes;
				}
			};
		}
		return errorStream;
	}

	@Override
	public @Nullable OutputStream getOutputStream() {
		if (outputStream != null) {
			return outputStream;
		}
		if (provider.getOutputStream() != null) {
			outputStream = new FilterOutputStream(provider.getOutputStream()) {
				@Override
				public void write(byte[] b) throws IOException {
					if (messageLogger.shouldLog()) {
						String s = message(Direction.LSP4E_TO_LANGUAGE_SERVER, b);
						messageLogger.log(s);
					}
					super.write(b);
				}
			};
		}
		return outputStream;
	}

	@Override
	public void start() throws IOException {
		provider.start();
	}

	@Override
	public @Nullable InputStream forwardCopyTo(@Nullable InputStream input, @Nullable OutputStream output) {
		return provider.forwardCopyTo(input, output);
	}

	@Override
	public @Nullable Object getInitializationOptions(@Nullable URI rootUri) {
		return provider.getInitializationOptions(rootUri);
	}

	@Override
	public String getTrace(@Nullable URI rootUri) {
		return provider.getTrace(rootUri);
	}

	@Override
	public void handleMessage(Message message, LanguageServer languageServer, @Nullable URI rootURI) {
		provider.handleMessage(message, languageServer, rootURI);
	}

	@Override
	public void stop() {
		provider.stop();
		try {
			if (outputStream != null) {
				outputStream.close();
				outputStream = null;
			}
			if (inputStream != null) {
				inputStream.close();
				inputStream = null;
			}
			if (errorStream != null) {
				errorStream.close();
				errorStream = null;
			}
		} catch (IOException e) {
			LanguageServerPlugin.logError(e);
		}

		try {
			messageLogger.dispose();
		} catch (IOException e) {
			LanguageServerPlugin.logError("Failed to dipose message logger", e); //$NON-NLS-1$
		}
	}

}
