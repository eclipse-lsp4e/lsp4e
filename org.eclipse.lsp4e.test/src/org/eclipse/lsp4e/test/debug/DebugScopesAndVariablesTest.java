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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.UnaryOperator;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4e.debug.debugmodel.DSPStackFrame;
import org.eclipse.lsp4e.debug.debugmodel.TransportStreams;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.junit.jupiter.api.Test;

/**
 * End-to-end style test around DSPStackFrame.getVariables() to verify that
 * scopes and variables are retrieved when the adapter reports a stop.
 *
 * Scenario:
 * <ol>
 * <li>create a mock DAP server that supports
 * initialize/launch/threads/stackTrace/scopes/variables
 * <li>wire it into a DSPDebugTarget via a Launcher stub (no real IO)
 * <li>server sends initialized + stopped; client refreshes threads and frames
 * <li>assert frame.getVariables() returns scopes; assert expanding returns
 * variables
 * </ol>
 */
public class DebugScopesAndVariablesTest extends AbstractTestWithProject {

	/**
	 * Minimal in-memory mock of a DAP server sufficient for this test
	 */
	private static final class MockDebugServer implements IDebugProtocolServer {
		// Fixed ids for this test
		private static final int THREAD_ID = 1;

		private static final int FRAME_ID = 101;
		private static final int LOCALS_REF = 201;
		// Wired by TestDebugTarget#createLauncher
		IDebugProtocolClient client;

		// Unused in this test but required by interface since LSP4E may call evaluate
		@Override
		public CompletableFuture<EvaluateResponse> evaluate(org.eclipse.lsp4j.debug.EvaluateArguments args) {
			var r = new EvaluateResponse();
			r.setResult("n/a");
			r.setVariablesReference(0);
			return CompletableFuture.completedFuture(r);
		}

		@Override
		public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
			var caps = new Capabilities();
			// Keep configurationDone optional for simplicity
			caps.setSupportsConfigurationDoneRequest(false);
			// Notify client that we are initialized as LSP4E waits for this signal.
			if (client != null) {
				client.initialized();
			}
			return CompletableFuture.completedFuture(caps);
		}

		@Override
		public CompletableFuture<Void> launch(Map<String, Object> args) {
			// Immediately report a stopped event so client populates threads/frames.
			if (client != null) {
				var stopped = new StoppedEventArguments();
				stopped.setReason("breakpoint");
				stopped.setThreadId(THREAD_ID);
				client.stopped(stopped);
			}
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
			var scope = new Scope();
			scope.setName("locals");
			scope.setVariablesReference(LOCALS_REF);
			var resp = new ScopesResponse();
			resp.setScopes(new Scope[] { scope });
			return CompletableFuture.completedFuture(resp);
		}

		@Override
		public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
			var sf = new StackFrame();
			sf.setId(FRAME_ID);
			sf.setName("func");
			sf.setLine(1);
			var resp = new StackTraceResponse();
			resp.setTotalFrames(1);
			resp.setStackFrames(new StackFrame[] { sf });
			return CompletableFuture.completedFuture(resp);
		}

		@Override
		public CompletableFuture<ThreadsResponse> threads() {
			var r = new ThreadsResponse();
			var t = new Thread();
			t.setId(THREAD_ID);
			t.setName("Main");
			r.setThreads(new Thread[] { t });
			return CompletableFuture.completedFuture(r);
		}

		@Override
		public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
			var v = new Variable();
			v.setName("x");
			v.setValue("42");
			v.setVariablesReference(0);
			var resp = new VariablesResponse();
			resp.setVariables(new Variable[] { v });
			return CompletableFuture.completedFuture(resp);
		}
	}

	/**
	 * DSPDebugTarget variant that injects a mock server without real JSON-RPC IO
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
			// Give the server a handle to the client so it can send notifications.
			if (server instanceof MockDebugServer m) {
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
				"ScopesAndVariablesTest-" + System.currentTimeMillis());
		return new Launch(wc, mode, null);
	}

	@Test
	public void testScopesAndVariablesAreReturned() throws Exception {
		ILaunch launch = newLaunch(ILaunchManager.RUN_MODE);

		var params = new HashMap<String, Object>();
		params.put("type", "mock");
		params.put("request", "launch");
		params.put("program", "dummy");

		var server = new MockDebugServer();
		var target = new TestDebugTarget(launch, params, server);

		target.initialize(new NullProgressMonitor());

		// Wait until server has sent 'stopped' and client marked itself suspended
		TestUtils.waitForAndAssertCondition(5000, target::isSuspended);

		var threads = target.getThreads();
		assertTrue(threads.length > 0, "No threads reported by debug target");
		assertEquals(1, threads.length, "Expected exactly one thread");
		assertEquals("Main", threads[0].getName(), "Thread name mismatch");
		assertEquals(Integer.valueOf(1), threads[0].getId(), "Thread id mismatch");

		var frames = threads[0].getStackFrames();
		assertTrue(frames.length > 0, "No stack frames available on stopped thread");
		assertEquals(1, frames.length, "Expected exactly one frame");

		IStackFrame frame = frames[0];
		assertEquals("func", frame.getName(), "Frame name mismatch");
		assertEquals(1, frame.getLineNumber(), "Frame line mismatch");
		assertEquals(101, ((DSPStackFrame) frame).getFrameId().intValue(), "Frame id mismatch");
		IVariable[] scopes = frame.getVariables();
		assertTrue(scopes.length > 0, "Expected at least one scope");
		assertEquals(1, scopes.length, "Expected exactly one scope");
		// Expect exactly one scope named "locals"
		assertArrayEquals(new String[] { "locals" }, new String[] { scopes[0].getName() });
		assertTrue(scopes[0].getValue().hasVariables(), "Scope should advertise child variables");

		// Expand the scope to fetch actual variables via 'variables' request
		var value = scopes[0].getValue();
		var vars = value.getVariables();
		assertNotNull(value, "Scope value should not be null");
		assertTrue(vars != null && vars.length > 0, "Expected at least one variable under 'locals'");
		assertEquals(1, vars.length, "Expected exactly one local variable");
		assertArrayEquals(new String[] { "x" }, new String[] { vars[0].getName() });
		assertEquals("42", vars[0].getValue().getValueString(), "Variable value mismatch");
		assertFalse(vars[0].getValue().hasVariables(), "Leaf variable should not have children");

		// Capabilities returned by mock initialize
		assertNotNull(target.getCapabilities(), "Capabilities should be available after initialize");
		assertFalse(target.getCapabilities().getSupportsConfigurationDoneRequest(), 
				"supportsConfigurationDoneRequest should be false");
	}
}
