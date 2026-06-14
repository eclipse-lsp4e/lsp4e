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
package org.eclipse.lsp4e.tests.mock;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Message;

/**
 * A factory which is used to create language server instances for test cases.
 * The behavior of the LS can be tailored towards a specific test case.
 */
public class MockLanguageServerFactory {

	private BiConsumer<Integer, MockLanguageServer> configuration = (idx, server) -> {
	};

	private Supplier<ServerCapabilities> capabilities = MockLanguageServer::defaultServerCapabilities;

	private final List<MockLanguageServer> launchedServers = new CopyOnWriteArrayList<>();

	/**
	 * The amount of launched servers
	 */
	private final AtomicInteger launchCounter = new AtomicInteger();

	/**
	 * How often was start called on the MockConnectionProvider
	 */
	public final AtomicInteger connectionProviderStartCounter = new AtomicInteger();

	/**
	 * How often was stop called on the MockConnectionProvider
	 */
	public final AtomicInteger connectionProviderStopCounter = new AtomicInteger();

	/**
	 * The observed cancellation messages
	 */
	public final Collection<Message> cancellations = new ArrayList<>();

	/**
	 * Pass a consumer which is called for each newly created LanguageServer. The
	 * first argument is the index of the currently configured server. This can be
	 * used to create languages servers which behave differently, based on this
	 * index.
	 * 
	 * Note: If you need to change the behavior of a server after it was launched,
	 * you can retrieve it {@link #getServer()} or {@link #getServers()} and
	 * configure it appropriately.
	 */
	public void withConfiguration(BiConsumer<Integer, MockLanguageServer> configuration) {
		this.configuration = configuration;
	}

	/**
	 * Supply the capabilities that will be offered by each configured
	 * LanguageServer.
	 */
	public void withCapabilities(Supplier<ServerCapabilities> capabilities) {
		this.capabilities = capabilities;
	}

	/**
	 * Get the list of launched servers.
	 */
	public List<MockLanguageServer> getServers() {
		return List.copyOf(launchedServers);
	}

	/**
	 * Get the most recently launched server.
	 */
	public MockLanguageServer getServer() {
		if (launchedServers.isEmpty()) {
			throw new IllegalStateException("""
					No server was launched so far.
					Retrieving the actual server only works after it was connected, e.g., a document was opened.
					If you want to configure it beforehand, use #withConfiguration.
					""");
		}
		return launchedServers.getLast();
	}

	/**
	 * Internal hook used by the MockConnectionProvider to create a Language Server.
	 */
	public MockLanguageServer create(OutputStream stdout) {
		MockLanguageServer server = new MockLanguageServer(capabilities, stdout);

		int launchNum = launchCounter.getAndIncrement();
		configuration.accept(launchNum, server);
		launchedServers.add(server);

		return server;
	}

	/**
	 * The amount of launched servers.
	 */
	public int getServerCount() {
		return launchCounter.get();
	}

}
