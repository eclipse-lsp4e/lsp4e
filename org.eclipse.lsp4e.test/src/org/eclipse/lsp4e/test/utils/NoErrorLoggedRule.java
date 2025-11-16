/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class NoErrorLoggedRule implements BeforeEachCallback, AfterEachCallback {

	private ILog log;
	private ILogListener listener;
	private final List<IStatus> loggedErrors = new ArrayList<>();

	public NoErrorLoggedRule() {
		this(LanguageServerPlugin.getDefault().getLog());
	}

	public NoErrorLoggedRule(ILog log) {
		this.log = log;
		final var logger = System.getLogger(log.getBundle().getSymbolicName());
		listener = (status, unused) -> {
			switch (status.getSeverity()) {
			case IStatus.ERROR:
				loggedErrors.add(status);
				logger.log(Level.ERROR, status.toString(), status.getException());
				break;
			case IStatus.WARNING:
				logger.log(Level.WARNING, status.toString(), status.getException());
				break;
			case IStatus.INFO:
				logger.log(Level.INFO, status.toString(), status.getException());
				break;
			}
		};
	}

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		loggedErrors.clear();
		log.addLogListener(listener);
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		log.removeLogListener(listener);
		assertEquals(Collections.emptyList(), loggedErrors, "Some errors were logged");
	}

}
