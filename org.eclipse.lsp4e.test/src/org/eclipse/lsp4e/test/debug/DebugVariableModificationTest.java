/*******************************************************************************
 * Copyright (c) 2026 Avaloq Group AG and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.Launch;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4e.debug.debugmodel.DSPStackFrame;
import org.eclipse.lsp4e.debug.debugmodel.TransportStreams;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetExpressionArguments;
import org.eclipse.lsp4j.debug.SetExpressionResponse;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
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
 * Verifies that modifying the value of a variable sends the DAP request matching
 * the origin of the variable: {@code setVariable} for variables contained in a
 * variables container, {@code setExpression} for evaluate results, and nothing
 * for scopes.
 */
public class DebugVariableModificationTest extends AbstractTestWithProject {

	private static final int THREAD_ID = 1;
	private static final int FRAME_ID = 101;
	private static final int LOCALS_REF = 201;
	private static final int EVALUATE_RESULT_REF = 301;

	private static final class MockDebugServer implements IDebugProtocolServer {
		final Capabilities capabilities = new Capabilities();
		final CompletableFuture<SetVariableArguments> setVariableArgs = new CompletableFuture<>();
		final CompletableFuture<SetExpressionArguments> setExpressionArgs = new CompletableFuture<>();
		IDebugProtocolClient client;

		@Override
		public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
			capabilities.setSupportsConfigurationDoneRequest(false);
			client.initialized();
			return CompletableFuture.completedFuture(capabilities);
		}

		@Override
		public CompletableFuture<Void> launch(Map<String, Object> args) {
			var stopped = new StoppedEventArguments();
			stopped.setReason("breakpoint");
			stopped.setThreadId(THREAD_ID);
			client.stopped(stopped);
			return CompletableFuture.completedFuture(null);
		}

		@Override
		public CompletableFuture<ThreadsResponse> threads() {
			var t = new Thread();
			t.setId(THREAD_ID);
			t.setName("Main");
			var r = new ThreadsResponse();
			r.setThreads(new Thread[] { t });
			return CompletableFuture.completedFuture(r);
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
		public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
			var scope = new Scope();
			scope.setName("locals");
			scope.setVariablesReference(LOCALS_REF);
			var resp = new ScopesResponse();
			resp.setScopes(new Scope[] { scope });
			return CompletableFuture.completedFuture(resp);
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

		@Override
		public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
			var r = new EvaluateResponse();
			r.setResult("Node@1");
			r.setVariablesReference(EVALUATE_RESULT_REF);
			return CompletableFuture.completedFuture(r);
		}

		@Override
		public CompletableFuture<SetVariableResponse> setVariable(SetVariableArguments args) {
			setVariableArgs.complete(args);
			var r = new SetVariableResponse();
			r.setValue(args.getValue());
			return CompletableFuture.completedFuture(r);
		}

		@Override
		public CompletableFuture<SetExpressionResponse> setExpression(SetExpressionArguments args) {
			setExpressionArgs.complete(args);
			var r = new SetExpressionResponse();
			r.setValue(args.getValue());
			return CompletableFuture.completedFuture(r);
		}
	}

	private static final class TestDebugTarget extends DSPDebugTarget {
		private final MockDebugServer server;

		TestDebugTarget(ILaunch launch, Map<String, Object> dspParameters, MockDebugServer server) {
			super(launch, () -> new TransportStreams.DefaultTransportStreams(InputStream.nullInputStream(),
					OutputStream.nullOutputStream()), dspParameters);
			this.server = server;
		}

		@Override
		protected Launcher<? extends IDebugProtocolServer> createLauncher(UnaryOperator<MessageConsumer> wrapper,
				InputStream in, OutputStream out, ExecutorService threadPool) {
			server.client = this;
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

	private static DSPStackFrame startAndGetTopFrame(MockDebugServer server) throws Exception {
		ILaunchConfigurationType type = DebugPlugin.getDefault().getLaunchManager()
				.getLaunchConfigurationType("org.eclipse.lsp4e.debug.launchType");
		ILaunchConfigurationWorkingCopy wc = type.newInstance(null,
				"VariableModificationTest-" + System.currentTimeMillis());
		var params = new HashMap<String, Object>();
		params.put("type", "mock");
		params.put("request", "launch");
		params.put("program", "dummy");
		var target = new TestDebugTarget(new Launch(wc, ILaunchManager.RUN_MODE, null), params, server);
		target.initialize(new NullProgressMonitor());
		TestUtils.waitForAndAssertCondition(5000, target::isSuspended);
		return (DSPStackFrame) target.getThreads()[0].getStackFrames()[0];
	}

	@Test
	public void testSetValueOfContainedVariableUsesSetVariableWithContainerReference() throws Exception {
		var server = new MockDebugServer();
		server.capabilities.setSupportsSetVariable(true);
		DSPStackFrame frame = startAndGetTopFrame(server);

		IVariable x = frame.getVariables()[0].getValue().getVariables()[0];
		assertTrue(x.supportsValueModification());
		x.setValue("43");

		SetVariableArguments args = server.setVariableArgs.get(5, TimeUnit.SECONDS);
		assertEquals(LOCALS_REF, args.getVariablesReference());
		assertEquals("x", args.getName());
		assertEquals("43", args.getValue());
		TestUtils.waitForAndAssertCondition(5000, () -> "43".equals(x.getValue().getValueString()));
	}

	@Test
	public void testScopeDoesNotSupportValueModification() throws Exception {
		var server = new MockDebugServer();
		server.capabilities.setSupportsSetVariable(true);
		server.capabilities.setSupportsSetExpression(true);
		DSPStackFrame frame = startAndGetTopFrame(server);

		IVariable scope = frame.getVariables()[0];
		assertFalse(scope.supportsValueModification());
		assertThrows(DebugException.class, () -> scope.setValue("1"));
		assertFalse(server.setVariableArgs.isDone());
		assertFalse(server.setExpressionArgs.isDone());
	}

	@Test
	public void testSetValueOfEvaluateResultUsesSetExpression() throws Exception {
		var server = new MockDebugServer();
		server.capabilities.setSupportsSetVariable(true);
		server.capabilities.setSupportsSetExpression(true);
		DSPStackFrame frame = startAndGetTopFrame(server);

		IVariable result = frame.evaluate("node").get(5, TimeUnit.SECONDS);
		assertTrue(result.supportsValueModification());
		result.setValue("null");

		SetExpressionArguments args = server.setExpressionArgs.get(5, TimeUnit.SECONDS);
		assertEquals(FRAME_ID, args.getFrameId());
		assertEquals("node", args.getExpression());
		assertEquals("null", args.getValue());
		assertFalse(server.setVariableArgs.isDone());
		TestUtils.waitForAndAssertCondition(5000, () -> "null".equals(result.getValue().getValueString()));
	}

	@Test
	public void testEvaluateResultIsNotModifiableWithoutSetExpressionSupport() throws Exception {
		var server = new MockDebugServer();
		server.capabilities.setSupportsSetVariable(true);
		DSPStackFrame frame = startAndGetTopFrame(server);

		IVariable result = frame.evaluate("node").get(5, TimeUnit.SECONDS);
		assertFalse(result.supportsValueModification());
	}
}
