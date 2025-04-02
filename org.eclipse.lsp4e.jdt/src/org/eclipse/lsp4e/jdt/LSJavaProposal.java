/*******************************************************************************
 * Copyright (c) 2022, 2024 VMware Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Alex Boyko (VMware Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.internal.codeassist.RelevanceConstants;
import org.eclipse.jdt.internal.ui.text.java.AbstractJavaCompletionProposal;
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocBrowserInformationControlInput;
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.internal.text.html.HTMLPrinter;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPart;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

@SuppressWarnings("restriction")
class LSJavaProposal extends LSCompletionProposal implements IJavaCompletionProposal {
	
	private static final int LS_DEFAULT_RELEVANCE = 18;
	
	private static final int MAX_BASE_RELEVANCE = 51 * 16; // Looks like JDT's max for exact match is 52
	
	 // Based on org.eclipse.jdt.internal.ui.text.java.RelevanceComputer
	private static final int DEFAULT_RELEVANCE = (RelevanceConstants.R_DEFAULT + LS_DEFAULT_RELEVANCE) * 16;

	private static final int RANGE_WITHIN_CATEGORY = Math.round((MAX_BASE_RELEVANCE - DEFAULT_RELEVANCE) / 4f);

	private static @Nullable String fgCSSStyles = null;
	
	private boolean relevanceComputed = false;
	private int relevance = -1;

	private @Nullable IInformationControlCreator infoControlCreator = null;

	public LSJavaProposal(LSCompletionProposal lsProposal) {
		super(lsProposal);
	}

	@Override
	public int getRelevance() {
		if (!relevanceComputed) {
				// Based on org.eclipse.jdt.internal.ui.text.java.RelevanceComputer
				relevance = computeBaseRelevance();
				switch (getItem().getKind()) {
				case Class:
					relevance += 3;
					break;
				case Field:
				case Property:	
					relevance += 5;
					break;
				case Method:
					relevance += 4;
					break;
				case Variable:
				case Value:
					relevance += 6;
					break;
				default:
				}
			relevanceComputed = true;
		}
		return relevance;
	}
	
	private int computeBaseRelevance() {
		// Incorporate LSP4E category and rank into base relevance.
		int base = MAX_BASE_RELEVANCE - (getRankCategory() - 1) * RANGE_WITHIN_CATEGORY;
		int rank = getRankScore();
		base -= (rank >= 0 && rank < RANGE_WITHIN_CATEGORY ? rank : RANGE_WITHIN_CATEGORY);
		return base;
	}
	
	/*
	 * Copied from org.eclipse.jdt.internal.ui.text.java.AbstractJavaCompletionProposal.getInformationControlCreator()
	 */
	@Override
	public @Nullable IInformationControlCreator getInformationControlCreator() {
		Shell shell= UI.getActiveShell();
		if (shell == null || !BrowserInformationControl.isAvailable(shell))
			return null;

		if (infoControlCreator == null) {
			/*
			 * see https://bugs.eclipse.org/bugs/show_bug.cgi?id=232024
			 */
			IWorkbenchPart part = UI.getActivePart();
			JavadocHover.PresenterControlCreator presenterControlCreator= new JavadocHover.PresenterControlCreator(part == null ? null : part.getSite());
			infoControlCreator= new JavadocHover.HoverControlCreator(presenterControlCreator, true);
		}
		return infoControlCreator;
	}

	/*
	 * Copied from org.eclipse.jdt.internal.ui.text.java.AbstractJavaCompletionProposal.getAdditionalProposalInfo(IProgressMonitor monitor)
	 */
	@Override
	public Object getAdditionalProposalInfo(IProgressMonitor monitor) {
		StringBuilder buffer = new StringBuilder((String) super.getAdditionalProposalInfo(monitor));
		ColorRegistry registry= JFaceResources.getColorRegistry();
		RGB fgRGB= registry.getRGB("org.eclipse.jdt.ui.Javadoc.foregroundColor"); //$NON-NLS-1$
		RGB bgRGB= registry.getRGB("org.eclipse.jdt.ui.Javadoc.backgroundColor"); //$NON-NLS-1$
		HTMLPrinter.insertPageProlog(buffer, 0, fgRGB, bgRGB, getCSSStyles());
		HTMLPrinter.addPageEpilog(buffer);
		return new JavadocBrowserInformationControlInput(null, null, buffer.toString(), 0);

	}
	
	/**
	 * Returns the style information for displaying HTML (Javadoc) content.
	 * Copied from org.eclipse.jdt.internal.ui.text.java.AbstractJavaCompletionProposal.getCSSStyles()
	 *
	 * @return the CSS styles
	 */
	private @Nullable String getCSSStyles() {
		if (fgCSSStyles == null) {
			Bundle bundle= FrameworkUtil.getBundle(AbstractJavaCompletionProposal.class);
			URL url= bundle == null ? null : bundle.getEntry("/JavadocHoverStyleSheet.css"); //$NON-NLS-1$
			if (url != null) {
				BufferedReader reader= null;
				try {
					url= FileLocator.toFileURL(url);
					reader= new BufferedReader(new InputStreamReader(url.openStream()));
					StringBuilder buffer= new StringBuilder(200);
					String line= reader.readLine();
					while (line != null) {
						buffer.append(line);
						buffer.append('\n');
						line= reader.readLine();
					}
					fgCSSStyles= buffer.toString();
				} catch (IOException ex) {
					LanguageServerPlugin.logError(ex);
				} finally {
					try {
						if (reader != null)
							reader.close();
					} catch (IOException e) {
					}
				}

			}
		}
		String css= fgCSSStyles;
		if (css != null) {
			FontData fontData= JFaceResources.getFontRegistry().getFontData(PreferenceConstants.APPEARANCE_JAVADOC_FONT)[0];
			css= HTMLPrinter.convertTopLevelFont(css, fontData);
		}
		return css;
	}


}
