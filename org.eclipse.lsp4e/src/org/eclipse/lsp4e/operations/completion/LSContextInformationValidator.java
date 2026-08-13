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
package org.eclipse.lsp4e.operations.completion;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationPresenter;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;

public final class LSContextInformationValidator implements IContextInformationValidator, IContextInformationPresenter {

	/** The content assist processor. */
	private final IContentAssistProcessor fProcessor;
	/** The context information to be validated. */
	private @Nullable IContextInformation fContextInformation;
	/** The associated text viewer. */
	private @Nullable ITextViewer fViewer;

	/**
	 * Creates a new context information validator which is ready to be installed on
	 * a particular context information.
	 *
	 * @param processor
	 *            the processor to be used for validation
	 */
	public LSContextInformationValidator(IContentAssistProcessor processor) {
		fProcessor = processor;
	}

	@Override
	public void install(@Nullable IContextInformation contextInformation, @Nullable ITextViewer viewer, int offset) {
		fContextInformation = contextInformation;
		fViewer = viewer;
	}

	@Override
	public boolean isContextInformationValid(int offset) {
		if (!(fViewer instanceof ITextViewer viewer)) {
			return false;
		}
		if (!(fContextInformation instanceof LSContextInformation contextInfo)) {
			return false;
		}

		// Check if the current context information is still valid for the given offset.
		IContextInformation[] infos = fProcessor.computeContextInformation(viewer, offset);
		if (infos != null) {
			for (IContextInformation info : infos) {
				if (contextInfo.equals(info)) {
					// Grab active parameter from new context.
					contextInfo.setActiveParameter(((LSContextInformation) info).getActiveParameter());
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean updatePresentation(int offset, @Nullable TextPresentation presentation) {
		if (presentation == null) {
			return false;
		}
		if (!(fContextInformation instanceof LSContextInformation contextInfo)) {
			return false;
		}

		/*
		 * Assumption: The context information has been validated before this call
		 * and the active parameter has been updated accordingly.
		 */
		presentation.clear();
		if (contextInfo.getActiveParameter() instanceof LocationInString location) {
			presentation.addStyleRange(new StyleRange(location.start(), location.length(), null, null, SWT.BOLD));
		}
		return true;
	}
}
