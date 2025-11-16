/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.server;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.Test;

public class StreamConnectionProviderTest {

	private static StreamConnectionProvider newProvider() {
		return new StreamConnectionProvider() {
			@Override
			public void start() throws IOException {
			}

			@Override
			public InputStream getInputStream() {
				return null;
			}

			@Override
			public OutputStream getOutputStream() {
				return null;
			}

			@Override
			public InputStream getErrorStream() {
				return null;
			}

			@Override
			public void stop() {
			}

			@Override
			public void handleMessage(Message message, LanguageServer languageServer, URI rootURI) {
			}
		};
	}

	@Test
	public void test_forwardCopy_singleByteRead_writesToProvidedOutput() throws Exception {
		final var input = new ByteArrayInputStream("ABC".getBytes(UTF_8));
		final var sink = new ByteArrayOutputStream();

		try (InputStream forwarding = newProvider().forwardCopyTo(input, sink)) {
			while ((forwarding.read()) != -1) {
				// read one byte at a time to exercise single-byte read path
			}
		}
		assertEquals("ABC", sink.toString(UTF_8), "expected input to be forwarded to provided OutputStream");
	}

	@Test
	public void test_forwardCopy_readArray_onEOF_returnsMinusOne_noException() throws Exception {
		final var emptyInput = new ByteArrayInputStream(new byte[0]);
		final var sink = new ByteArrayOutputStream();

		try (final var forwarding = newProvider().forwardCopyTo(emptyInput, sink)) {
			final var buf = new byte[8];
			int n = forwarding.read(buf); // should be -1 and not throw
			assertEquals(-1, n, "expected EOF (-1) on empty stream");
		}
		assertArrayEquals(new byte[0], sink.toByteArray());
	}
}
