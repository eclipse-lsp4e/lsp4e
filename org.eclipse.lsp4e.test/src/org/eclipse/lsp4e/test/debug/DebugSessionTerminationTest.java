/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation.
 *******************************************************************************/
package org.eclipse.lsp4e.test.debug;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.Launch;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4e.debug.debugmodel.TransportStreams;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.ExitedEventArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.ProcessEventArguments;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.junit.jupiter.api.Test;

/**
 * Regression test for https://github.com/eclipse-lsp4e/lsp4e/issues/266: ensure
 * LSP4E.debug does not terminate the debug adapter too early.
 *
 * Scenario:
 * <ol>
 * <li>create a mock DAP server and wire it into a DSPDebugTarget via a Launcher
 * stub (no real IO)
 * <li>server sends a ProcessEvent so LSP4E creates a DSPProcess
 * <li>server sends a terminated event followed by an exited event
 * <li>assert that the exited event can still be delivered without the
 * connection being torn down
 * </ol>
 */
class DebugSessionTerminationTest extends AbstractTestWithProject {

	/**
	 * Minimal in-memory mock of a DAP server sufficient for this test.
	 */
	private static final class MockDebugServer implements IDebugProtocolServer {
		IDebugProtocolClient client;
		final AtomicBoolean exitedDelivered = new AtomicBoolean(false);
		final AtomicInteger terminateRequestCount = new AtomicInteger(0);
		final AtomicInteger disconnectRequestCount = new AtomicInteger(0);

		@Override
		public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
			var caps = new Capabilities();
			// Advertise support for terminate so that DSPDebugTarget will send a
			// terminate request when it (incorrectly) decides to terminate the adapter.
			caps.setSupportsTerminateRequest(true);
			if (client != null) {
				client.initialized();
			}
			return CompletableFuture.completedFuture(caps);
		}

		@Override
		public CompletableFuture<Void> launch(Map<String, Object> args) {
			if (client != null) {
				var process = new ProcessEventArguments();
				process.setName("mock-debuggee");
				client.process(process);

				var terminated = new TerminatedEventArguments();
				client.terminated(terminated);

				var exited = new ExitedEventArguments();
				exited.setExitCode(0);
				try {
					client.exited(exited);
					exitedDelivered.set(true);
				} catch (Throwable t) {
					exitedDelivered.set(false);
				}
			}
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> terminate(TerminateArguments args) {
			terminateRequestCount.incrementAndGet();
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> disconnect(DisconnectArguments args) {
			disconnectRequestCount.incrementAndGet();
			return CompletableFuture.completedFuture(null);
		}
	}

	/**
	 * Mock server that only sends a 'terminated' event (no 'exited') to exercise
	 * the fallback termination path in DSPDebugTarget.
	 */
	private static final class MockTerminatedOnlyServer implements IDebugProtocolServer {
		IDebugProtocolClient client;
		final AtomicBoolean terminatedDelivered = new AtomicBoolean(false);
		final AtomicInteger terminateRequestCount = new AtomicInteger(0);
		final AtomicInteger disconnectRequestCount = new AtomicInteger(0);

		@Override
		public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
			var caps = new Capabilities();
			caps.setSupportsTerminateRequest(true);
			if (client != null) {
				client.initialized();
			}
			return CompletableFuture.completedFuture(caps);
		}

		@Override
		public CompletableFuture<Void> launch(Map<String, Object> args) {
			if (client != null) {
				var process = new ProcessEventArguments();
				process.setName("mock-debuggee");
				client.process(process);

				var terminated = new TerminatedEventArguments();
				client.terminated(terminated);
				terminatedDelivered.set(true);
			}
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> terminate(TerminateArguments args) {
			terminateRequestCount.incrementAndGet();
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<Void> disconnect(DisconnectArguments args) {
			disconnectRequestCount.incrementAndGet();
			return CompletableFuture.completedFuture(null);
		}
	}

	/**
	 * DSPDebugTarget variant that injects a mock server without real JSON-RPC IO.
	 */
	private static final class TestDebugTarget extends DSPDebugTarget {
		private final IDebugProtocolServer server;

		TestDebugTarget(ILaunch launch, Map<String, Object> dspParameters, IDebugProtocolServer server) {
			super(launch, () -> new TransportStreams.DefaultTransportStreams(InputStream.nullInputStream(),
					OutputStream.nullOutputStream()), dspParameters);
			this.server = server;
		}

		@Override
		protected Launcher<? extends IDebugProtocolServer> createLauncher(UnaryOperator<MessageConsumer> wrapper,
				InputStream in, OutputStream out, ExecutorService threadPool) {
			if (server instanceof MockDebugServer m) {
				m.client = this;
			} else if (server instanceof MockTerminatedOnlyServer m) {
				m.client = this;
			}
			return new Launcher<>() {
				@Override
				public RemoteEndpoint getRemoteEndpoint() {
					return null;
				}

				@Override
				public IDebugProtocolServer getRemoteProxy() {
					return server;
				}

				@Override
				public CompletableFuture<Void> startListening() {
					return CompletableFuture.completedFuture(null);
				}
			};
		}
	}

	private static final String LAUNCH_TYPE_ID = "org.eclipse.lsp4e.debug.launchType";

	private static ILaunch newLaunch(String mode) throws Exception {
		ILaunchConfigurationType type = DebugPlugin.getDefault().getLaunchManager()
				.getLaunchConfigurationType(LAUNCH_TYPE_ID);
		ILaunchConfigurationWorkingCopy wc = type.newInstance(null,
				"DebugSessionTerminationTest-" + System.currentTimeMillis());
		return new Launch(wc, mode, null);
	}

	@Test
	void testTerminatedDoesNotPreventExited() throws Exception {
		ILaunch launch = newLaunch(ILaunchManager.RUN_MODE);

		var params = new HashMap<String, Object>();
		params.put("type", "mock");
		params.put("request", "launch");
		params.put("program", "dummy");

		var server = new MockDebugServer();
		var target = new TestDebugTarget(launch, params, server);

		target.initialize(new NullProgressMonitor());

		// Wait until the terminated event from the adapter has been processed.
		TestUtils.waitForAndAssertCondition(5_000, target::isTerminated);

		// Sanity check: the target should be marked as terminated.
		assertTrue(target.isTerminated(), "Debug target should be terminated");

		// The exited event should still be deliverable after terminated.
		assertTrue(server.exitedDelivered.get(), "Debug adapter exited event should be deliverable after terminated");

		// Bug 266 history: LSP4E used to react to a 'terminated' event from the
		// adapter by calling DSPProcess.terminate(), which in turn called
		// DSPDebugTarget.terminate() and sent a terminate or disconnect request back
		// to the adapter. This was contrary to the DAP guidelines where a 'terminated'
		// event indicates that the debuggee has ended and the adapter should be
		// allowed to shut down cleanly (emitting 'exited'). This test ensures no such
		// terminate or disconnect request is sent in response to the adapter's event.
		assertEquals(0, server.terminateRequestCount.get(),
				"LSP4E must not send terminate request back to adapter after receiving terminated event");
		assertEquals(0, server.disconnectRequestCount.get(),
				"LSP4E must not send disconnect request back to adapter after receiving terminated event");
	}

	@Test
	void testTerminatedWithoutExitedCleansUpWithoutAdapterTerminate() throws Exception {
		ILaunch launch = newLaunch(ILaunchManager.RUN_MODE);

		var params = new HashMap<String, Object>();
		params.put("type", "mock");
		params.put("request", "launch");
		params.put("program", "dummy");

		var server = new MockTerminatedOnlyServer();
		var target = new TestDebugTarget(launch, params, server);

		target.initialize(new NullProgressMonitor());

		// Ensure the adapter's 'terminated' event has been sent
		TestUtils.waitForAndAssertCondition(5_000, server.terminatedDelivered::get);

		// The fallback in DSPDebugTarget.terminated(TerminatedEventArguments) should
		// eventually clean up the session even if no 'exited' event is delivered.
		TestUtils.waitForAndAssertCondition(5_000, target::isTerminated);

		assertTrue(target.isTerminated(), "Debug target should be terminated after adapter-only terminated event");

		// As with the exited case, LSP4E must not send terminate or disconnect
		// requests back to the adapter as a reaction to its 'terminated' event.
		assertEquals(0, server.terminateRequestCount.get(),
				"LSP4E must not send terminate request back to adapter after receiving terminated event");
		assertEquals(0, server.disconnectRequestCount.get(),
				"LSP4E must not send disconnect request back to adapter after receiving terminated event");
	}
}
