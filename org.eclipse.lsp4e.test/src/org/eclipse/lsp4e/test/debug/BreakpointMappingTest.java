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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.lsp4e.debug.breakpoints.DSPLineBreakpoint;
import org.eclipse.lsp4e.debug.debugmodel.DSPBreakpointManager;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that DSPBreakpointManager maps marker attributes to DAP
 * SourceBreakpoint (condition, column, hitCondition).
 */
class BreakpointMappingTest extends AbstractTestWithProject {

	private static class CapturingServer implements IDebugProtocolServer {
		List<SetBreakpointsArguments> calls = new ArrayList<>();

		@Override
		public CompletableFuture<SetBreakpointsResponse> setBreakpoints(SetBreakpointsArguments arguments) {
			synchronized (calls) {
				calls.add(arguments);
			}
			var resp = new SetBreakpointsResponse();
			resp.setBreakpoints(new Breakpoint[0]);
			return CompletableFuture.completedFuture(resp);
		}
	}

	private List<IBreakpoint> created = new ArrayList<>();

	@BeforeEach
	void clearBreakpoints() throws CoreException {
		// Ensure a clean slate for the test case
		for (IBreakpoint bp : DebugPlugin.getDefault().getBreakpointManager().getBreakpoints()) {
			bp.delete();
		}
	}

	@AfterEach
	void cleanupCreated() throws CoreException {
		for (IBreakpoint bp : created) {
			bp.delete();
		}
	}

	@Test
	void breakpoint_conditions_are_sent_to_server() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "txt", "first line\nsecond line\n");

		var bp = new DSPLineBreakpoint(file, 2);
		bp.setCondition("x > 0");
		bp.setColumn(7);
		bp.setHitCondition(">= 3");
		created.add(bp);
		DebugPlugin.getDefault().getBreakpointManager().addBreakpoint(bp);

		var server = new CapturingServer();
		var manager = new DSPBreakpointManager(DebugPlugin.getDefault().getBreakpointManager(), server, null);

		try {
			manager.initialize().join();

			SetBreakpointsArguments matching = null;
			synchronized (server.calls) {
				assertTrue(!server.calls.isEmpty(), "No setBreakpoints() calls captured");
				String path = file.getLocation().toOSString();
				for (SetBreakpointsArguments a : server.calls) {
					if (a.getSource() != null && path.equals(a.getSource().getPath())) {
						matching = a;
						break;
					}
				}
			}

			assertNotNull(matching, "No setBreakpoints() call for our file was captured");
			SourceBreakpoint[] sent = matching.getBreakpoints();
			assertNotNull(sent);
			assertEquals(1, sent.length, "Expected exactly one SourceBreakpoint");

			SourceBreakpoint sb = sent[0];
			assertEquals("x > 0", sb.getCondition());
			assertEquals(7, sb.getColumn());
			assertEquals(">= 3", sb.getHitCondition());
		} finally {
			manager.shutdown();
		}
	}

	@Test
	void changing_breakpoint_condition_replaces_existing_entry() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "txt", "first line\nsecond line\n");

		var bp = new DSPLineBreakpoint(file, 2);
		bp.setCondition("x > 0");
		created.add(bp);

		var server = new CapturingServer();
		var manager = new DSPBreakpointManager(DebugPlugin.getDefault().getBreakpointManager(), server, null);

		try {
			// Simulate initial registration of the breakpoint with the manager
			manager.initialize().join();
			manager.breakpointAdded(bp);

			// Change the condition and simulate the platform reporting the change
			server.calls.clear();
			bp.setCondition("x > 1");
			manager.breakpointChanged(bp, null);

			SetBreakpointsArguments matching = null;
			synchronized (server.calls) {
				assertTrue(!server.calls.isEmpty(),
						"No setBreakpoints() calls captured after breakpoint condition change");
				String path = file.getLocation().toOSString();
				for (SetBreakpointsArguments a : server.calls) {
					if (a.getSource() != null && path.equals(a.getSource().getPath())) {
						matching = a;
						break;
					}
				}
			}

			assertNotNull(matching,
					"No setBreakpoints() call for our file was captured after breakpoint condition change");
			SourceBreakpoint[] sent = matching.getBreakpoints();
			assertNotNull(sent);
			assertEquals(1, sent.length, "Expected exactly one SourceBreakpoint after condition change");

			SourceBreakpoint sb = sent[0];
			assertEquals("x > 1", sb.getCondition());
		} finally {
			manager.shutdown();
		}
	}
}
