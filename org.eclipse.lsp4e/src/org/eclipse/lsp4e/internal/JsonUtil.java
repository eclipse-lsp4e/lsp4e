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

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * Provides a {@link Gson} instance which can properly serialize and deserialize LSP4J JSON-RPC objects
 */
public class JsonUtil {

	public static final Gson LSP4J_GSON = Objects.requireNonNull(new MessageJsonHandler(Map.of()).getGson());

	/**
	 * Decodes the {@code registerOptions} payload of a dynamic capability {@link Registration}.
	 * <p>
	 * The payload is typed as {@code LSPAny} in the protocol, so LSP4J leaves it as a raw
	 * {@link JsonElement}; the concrete type depends on the registration's {@code method}. An
	 * in-process server may also hand over the Java object directly, which is returned as-is.
	 *
	 * @param registration the registration whose options to decode
	 * @param type         the LSP4J {@code *RegistrationOptions} type expected for
	 *                     {@code registration.getMethod()}
	 * @return the decoded options, or {@code null} if the registration carries no options
	 * @throws com.google.gson.JsonParseException if the payload does not match {@code type}
	 */
	public static <T> @Nullable T registrationOptions(final Registration registration, final Class<T> type) {
		final Object options = registration.getRegisterOptions();
		if (options == null) {
			return null;
		}
		if (type.isInstance(options)) {
			return type.cast(options);
		}
		if (options instanceof JsonElement json) {
			return LSP4J_GSON.fromJson(json, type);
		}
		return null;
	}

	/**
	 * Creates a deep copy of an LSP4J object by round-tripping it through JSON.
	 */
	public static <T> T deepCopy(final T value, final Class<T> type) {
		return Objects.requireNonNull(LSP4J_GSON.fromJson(LSP4J_GSON.toJsonTree(value, type), type));
	}

}
