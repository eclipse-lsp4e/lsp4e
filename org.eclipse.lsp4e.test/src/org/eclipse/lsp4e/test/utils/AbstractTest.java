/*******************************************************************************
 * Copyright (c) 2024 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.ServerCapabilities;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test base class that configures a {@link AllCleanExtension}.
 */
public abstract class AbstractTest {

	@RegisterExtension
	public final AllCleanExtension allCleanRule = new AllCleanExtension(this::getServerCapabilities);

	/**
	 * Override if required, used by {@link #allCleanRule}
	 */
	protected ServerCapabilities getServerCapabilities() {
		return MockLanguageServer.defaultServerCapabilities();
	}
}
