/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.operations.hover;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.text.Region;
import org.eclipse.lsp4j.Hover;

/**
 * A {@link Region} which contains the hover request which was sent when
 * creating this region.
 */
public class LSPHoverRegion extends Region {

	private final CompletableFuture<List<Hover>> request;

	public LSPHoverRegion(int offset, int length, CompletableFuture<List<Hover>> request) {
		super(offset, length);
		this.request = request;
	}

	public CompletableFuture<List<Hover>> getRequest() {
		return request;
	}

}
