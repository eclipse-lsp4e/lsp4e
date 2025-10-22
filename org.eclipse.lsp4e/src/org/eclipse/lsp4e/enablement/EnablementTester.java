/*******************************************************************************
 * Copyright (c) 2018, 2020 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rastislav Wagner (Red Hat Inc.) - initial implementation
 *  Alexander Fedorov (ArSysOp) - added parent context to evaluation
 *******************************************************************************/
package org.eclipse.lsp4e.enablement;

import java.net.URI;
import java.util.function.Supplier;

import org.eclipse.core.expressions.EvaluationContext;
import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;

/**
 * Represents enabledWhen element from plugin.xml
 *
 * @author rawagner
 *
 */
public final class EnablementTester {

	private final Expression expression;
	private final String description;
	private final Supplier<@Nullable IEvaluationContext> parent;

	public EnablementTester(Expression expression, String description) {
		this(() -> null, expression, description);
	}

	public EnablementTester(Supplier<@Nullable IEvaluationContext> parent, Expression expression, String description) {
		this.description = description;
		this.expression = expression;
		this.parent = parent;
	}

	/**
	 *
	 * @return enablement test description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Evaluates enablement expression
	 *
	 * @return true if expression evaluates to true, false otherwise
	 */
	public boolean evaluate(@Nullable URI uri) {
		boolean temporaryLoadDocument = false;
		IResource resource = null;
		try {
			IDocument document = null;
			resource = LSPEclipseUtils.findResourceFor(uri);
			if (resource != null) {
				document = LSPEclipseUtils.getExistingDocument(resource);
				if (document == null) {
					document = LSPEclipseUtils.getDocument(resource);
					temporaryLoadDocument = true;
				}
			}
			if (document == null) {
				temporaryLoadDocument = false;
			}
			final var context = new EvaluationContext(parent.get(), new Object());
			context.addVariable("document", //$NON-NLS-1$
					document != null ? document : IEvaluationContext.UNDEFINED_VARIABLE);
			context.addVariable("resource", //$NON-NLS-1$
					resource != null ? resource : IEvaluationContext.UNDEFINED_VARIABLE);
			context.addVariable("uri", //$NON-NLS-1$
					uri != null ? uri : IEvaluationContext.UNDEFINED_VARIABLE);
			context.setAllowPluginActivation(true);
			return expression.evaluate(context).equals(EvaluationResult.TRUE);
		} catch (CoreException e) {
			LanguageServerPlugin.logError("Error occurred during evaluation of enablement expression", e); //$NON-NLS-1$
		} finally {
			if (temporaryLoadDocument && resource != null) {
				try {
					FileBuffers.getTextFileBufferManager().disconnect(resource.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
				} catch (CoreException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}

		return false;
	}

}
