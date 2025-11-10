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
package org.eclipse.lsp4e.operations.hover;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Lightweight carrier for asynchronous hover HTML content.
 *
 * Holds a placeholder HTML to show immediately and a future that will
 * eventually provide the final HTML. The {@link #token} acts as an identity to
 * guard UI updates against races when the control input changes quickly.
 */
@SuppressWarnings("javadoc")
final class AsyncHtmlHoverInput {

	final UUID token = UUID.randomUUID();
	final CompletableFuture<@Nullable String> future;
	final String placeholderHtml;

	AsyncHtmlHoverInput(CompletableFuture<@Nullable String> future, String placeholderHtml) {
		this.future = future;
		this.placeholderHtml = placeholderHtml;
	}
}
