/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Victor Rubezhny (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.debug;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.Map;

import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.junit.Test;

public class DebugTest extends AbstractTestWithProject {

	/**
	 * Test for the `IllegalStateException: Duplicate RPC method runInTerminal` issue.
	 *
	 * The issue has started to appear after the move of `runInTerminal` method from the `IDebugProtocolServer`
	 * interface to `IDebugProtocolClient` interface in LSP5J while the DSPDebugTarget class that implements `runInTerminal`
	 * method of `IDebugProtocolClient` interface leaved unchanged thus creating an RPC method duplication.
	 *
	 * @throws Exception
	 */
	@Test
	public void testSupportedJSONRPCMethods() throws Exception {
		try {
			Map<String, JsonRpcMethod> rpcMethods = ServiceEndpoints.getSupportedMethods(DSPDebugTarget.class);
			assertNotNull("RPC Methods not found on DSPDebugTarget", rpcMethods);
			assertFalse("Zero RPC Methods found on DSPDebugTarget", rpcMethods.isEmpty());
		} catch (Throwable ex) {
			fail("An error occurred while getting the RPC Methods of DSPDebugTarget: " + ex.getMessage());
		}
	}
}
