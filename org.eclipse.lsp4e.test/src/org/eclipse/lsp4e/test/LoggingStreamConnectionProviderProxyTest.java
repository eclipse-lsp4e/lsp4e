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
package org.eclipse.lsp4e.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.logging.LoggingStreamConnectionProviderProxy;
import org.eclipse.lsp4e.logging.LoggingUtils;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LoggingStreamConnectionProviderProxyTest {

	private static final String SERVER_ID = "org.eclipse.lsp4e.test.logging";

	private final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();

	@BeforeEach
	public void enableLogging() {
		store.setValue(LoggingUtils.lsToConsoleLoggingId(SERVER_ID), true);
	}

	@AfterEach
	public void resetLogging() {
		store.setToDefault(LoggingUtils.lsToConsoleLoggingId(SERVER_ID));
	}

	@Test
	public void testReadAtEndOfStreamReturnsMinusOne() throws IOException {
		final var proxy = new LoggingStreamConnectionProviderProxy(new FixedStreamsProvider(new byte[0]), SERVER_ID);
		final var buffer = new byte[16];

		assertEquals(-1, proxy.getInputStream().read(buffer, 0, buffer.length));
		assertEquals(-1, proxy.getErrorStream().read(buffer, 0, buffer.length));
	}

	@Test
	public void testReadPassesDataThrough() throws IOException {
		final byte[] content = "Content".getBytes(StandardCharsets.UTF_8);
		final var proxy = new LoggingStreamConnectionProviderProxy(new FixedStreamsProvider(content), SERVER_ID);
		final var buffer = new byte[16];

		for (InputStream stream : new InputStream[] { proxy.getInputStream(), proxy.getErrorStream() }) {
			int read = stream.read(buffer, 2, buffer.length - 2);
			assertEquals(content.length, read);
			assertArrayEquals(content, Arrays.copyOfRange(buffer, 2, 2 + read));
			assertEquals(-1, stream.read(buffer, 0, buffer.length));
		}
	}

	private static final class FixedStreamsProvider implements StreamConnectionProvider {

		private final InputStream inputStream;
		private final InputStream errorStream;
		private final OutputStream outputStream = new ByteArrayOutputStream();

		FixedStreamsProvider(byte[] content) {
			this.inputStream = new ByteArrayInputStream(content);
			this.errorStream = new ByteArrayInputStream(content);
		}

		@Override
		public void start() {
		}

		@Override
		public InputStream getInputStream() {
			return inputStream;
		}

		@Override
		public OutputStream getOutputStream() {
			return outputStream;
		}

		@Override
		public InputStream getErrorStream() {
			return errorStream;
		}

		@Override
		public void stop() {
		}
	}
}
