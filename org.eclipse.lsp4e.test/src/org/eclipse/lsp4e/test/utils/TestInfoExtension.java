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

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TestInfoExtension implements BeforeEachCallback {

	@Override
	public void beforeEach(ExtensionContext context) {
		String testClass = context.getTestClass().map(Class::getName).orElse("UnknownTestClass");
		System.getLogger(testClass).log(Level.INFO, "Testing [" + context.getDisplayName() + "]...");
	}
	
}
