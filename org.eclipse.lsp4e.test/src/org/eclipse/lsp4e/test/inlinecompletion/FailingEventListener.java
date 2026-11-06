/*******************************************************************************
 * Copyright (c) 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.inlinecompletion;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEvent;
import org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEventListener;

public class FailingEventListener implements InlineCompletionEventListener {

	/** The UUIDs of the events this listener failed on. */
	static final List<UUID> FAILED = new CopyOnWriteArrayList<>();

	@Override
	public void handle(InlineCompletionEvent event) {
		if (InlineCompletionExtensionsTest.EVENT_URI.equals(event.uri())) {
			FAILED.add(event.uuid());
			throw new IllegalStateException("expected by InlineCompletionExtensionsTest");
		}
	}
}
