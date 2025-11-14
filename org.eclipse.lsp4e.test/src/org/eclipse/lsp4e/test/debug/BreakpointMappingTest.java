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

import static org.junit.Assert.*;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies that DSPBreakpointManager maps marker attributes to DAP
 * SourceBreakpoint (condition, column, hitCondition).
 */
public class BreakpointMappingTest extends AbstractTestWithProject {

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

	@Before
	public void clearBreakpoints() throws CoreException {
		// Ensure a clean slate for the test case
		for (IBreakpoint bp : DebugPlugin.getDefault().getBreakpointManager().getBreakpoints()) {
			bp.delete();
		}
	}

	@After
	public void cleanupCreated() throws CoreException {
		for (IBreakpoint bp : created) {
			bp.delete();
		}
	}

	@Test
	public void breakpoint_conditions_are_sent_to_server() throws Exception {
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
				assertTrue("No setBreakpoints() calls captured", !server.calls.isEmpty());
				String path = file.getLocation().toOSString();
				for (SetBreakpointsArguments a : server.calls) {
					if (a.getSource() != null && path.equals(a.getSource().getPath())) {
						matching = a;
						break;
					}
				}
			}

			assertNotNull("No setBreakpoints() call for our file was captured", matching);
			SourceBreakpoint[] sent = matching.getBreakpoints();
			assertNotNull(sent);
			assertTrue("Expected exactly one SourceBreakpoint", sent.length == 1);

			SourceBreakpoint sb = sent[0];
			assertEquals("x > 0", sb.getCondition());
			assertEquals(Integer.valueOf(7), sb.getColumn());
			assertEquals(">= 3", sb.getHitCondition());
		} finally {
			manager.shutdown();
		}
	}
}
