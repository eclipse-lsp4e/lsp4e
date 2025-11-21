/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.test.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.lsp4e.internal.JsonUtil;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.WatchKind;
import org.junit.jupiter.api.Test;

public class JsonUtilTest {

	@Test
	void testRoundtrip() throws Exception {
		// Setup an object which uses Either internally.
		// This can only be properly de/serialized with Gson instance which knows about LSP4J types.
		var original = new FileSystemWatcher();
		original.setGlobPattern("**");
		original.setKind(WatchKind.Create | WatchKind.Change | WatchKind.Delete);
		
		String json = JsonUtil.LSP4J_GSON.toJson(original);
		assertEquals("""
				{"globPattern":"**","kind":7}""", json);
		
		FileSystemWatcher copy = JsonUtil.LSP4J_GSON.fromJson(json, FileSystemWatcher.class);
		assertEquals(original, copy);
	}

}
