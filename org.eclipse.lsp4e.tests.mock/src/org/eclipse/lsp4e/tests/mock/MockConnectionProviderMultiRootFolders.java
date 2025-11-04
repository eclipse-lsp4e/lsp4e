/*******************************************************************************
 * Copyright (c) 2016, 2018 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Martin Lippert (Pivotal Inc.) - Bug 531030 - fixed crash when initial project gets deleted in multi-root workspaces
 *******************************************************************************/
package org.eclipse.lsp4e.tests.mock;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.Pipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class MockConnectionProviderMultiRootFolders implements StreamConnectionProvider {
	private static final Logger LOG = Logger.getLogger(MockConnectionProviderMultiRootFolders.class.getName());
	
	static ExecutorService sharedExecutor = new ThreadPoolExecutor(0, Runtime.getRuntime().availableProcessors(), 0,
			TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
			new ThreadFactoryBuilder().setNameFormat("mock-connection-provider-%d").build());

	static private AtomicInteger startCount = new AtomicInteger(0);
	static private AtomicInteger stopCount = new AtomicInteger(0);

	static public void resetCounts() {
		startCount.set(0);
		stopCount.set(0);
	}

	static public int getStartCount() {
		return startCount.get();
	}

	static public int getStopCount() {
		return stopCount.get();
	}

	private InputStream clientInputStream;
	private OutputStream clientOutputStream;
	private InputStream errorStream;
	private Collection<Closeable> streams = new ArrayList<>(4);
	private Future<Void> launcherFuture;

	@Override
	public void start() throws IOException {
		try {
			Pipe serverOutputToClientInput = Pipe.open();
			Pipe clientOutputToServerInput = Pipe.open();
			errorStream = InputStream.nullInputStream();
	
			InputStream serverInputStream = Channels.newInputStream(clientOutputToServerInput.source());
			OutputStream serverOutputStream = Channels.newOutputStream(serverOutputToClientInput.sink());
	
			Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(
					MockLanguageServerMultiRootFolders.INSTANCE, serverInputStream, serverOutputStream, sharedExecutor,
					(c) -> c);
	
			clientInputStream = Channels.newInputStream(serverOutputToClientInput.source());
			clientOutputStream = Channels.newOutputStream(clientOutputToServerInput.sink());
			launcherFuture = launcher.startListening();
			MockLanguageServer.INSTANCE.addRemoteProxy(launcher.getRemoteProxy());
			streams.add(clientInputStream);
			streams.add(clientOutputStream);
			streams.add(serverInputStream);
			streams.add(serverOutputStream);
			streams.add(errorStream);
	
			startCount.incrementAndGet();
		} catch (Exception x) {
			LOG.log(Level.SEVERE, "MockConnectionProvider#start", x);
		}
	}

	@Override
	public InputStream getInputStream() {
		return clientInputStream;
	}

	@Override
	public OutputStream getOutputStream() {
		return clientOutputStream;
	}

	@Override
	public InputStream getErrorStream() {
		return errorStream;
	}

	@Override
	public void stop() {
		stopCount.incrementAndGet();
		if (launcherFuture != null) {
			launcherFuture.cancel(true);
		}
	}
}
