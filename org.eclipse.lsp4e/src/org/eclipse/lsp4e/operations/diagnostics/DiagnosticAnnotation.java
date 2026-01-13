/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.diagnostics;

import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.lsp4j.Diagnostic;

class DiagnosticAnnotation extends Annotation {

	private static final String TYPE_INFO = "org.eclipse.ui.workbench.texteditor.info"; //$NON-NLS-1$
	private static final String TYPE_WARNING = "org.eclipse.ui.workbench.texteditor.warning"; //$NON-NLS-1$
	private static final String TYPE_ERROR = "org.eclipse.ui.workbench.texteditor.error"; //$NON-NLS-1$
	private final Diagnostic diagnostic;
	private final Function<Diagnostic, String> textComputer;

	public DiagnosticAnnotation(Diagnostic diagnostic, Function<Diagnostic, String> textComputer) {
		this.diagnostic = diagnostic;
		this.textComputer = textComputer;
	}

	@Override
	public String getType() {
		return switch (diagnostic.getSeverity()) {
		case Error -> TYPE_ERROR;
		case Warning -> TYPE_WARNING;
		case Information -> TYPE_INFO;
		case Hint -> TYPE_INFO;
		case null -> TYPE_ERROR;
		};
	}

	@Override
	public void setType(@Nullable String type) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getText() {
		return textComputer.apply(diagnostic);
	}

	@Override
	public void setText(@Nullable String text) {
		throw new UnsupportedOperationException();
	}

}
