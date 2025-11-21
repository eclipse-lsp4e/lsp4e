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
package org.eclipse.lsp4e.internal;

import java.util.Map;
import java.util.Objects;

import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;

import com.google.gson.Gson;

/**
 * Provides a {@link Gson} instance which can properly serialize and deserialize LSP4J JSON-RPC objects
 */
public class JsonUtil {

	public static final Gson LSP4J_GSON = Objects.requireNonNull(new MessageJsonHandler(Map.of()).getGson());

}
