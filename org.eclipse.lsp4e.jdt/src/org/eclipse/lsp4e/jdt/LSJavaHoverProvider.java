/*******************************************************************************
 * Copyright (c) 2017, 2020 Pivotal Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Martin Lippert (Pivotal Inc.) - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocBrowserInformationControlInput;
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.internal.text.html.HTMLPrinter;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.hover.LSPTextHover;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;

@SuppressWarnings("restriction")
public class LSJavaHoverProvider extends JavadocHover {

	private static @Nullable String fgStyleSheet;
	private static final String BODY_OPEN = "<body";
	private static final String BODY_CLOSE = "</body>";
	private static final String SEPARATOR = "<hr/>";

	private final LSPTextHover lsBasedHover = new LSPTextHover();

	@Override
	public @Nullable Object getHoverInfo2(@Nullable ITextViewer textViewer, @Nullable IRegion hoverRegion) {
		if (textViewer == null || hoverRegion == null) {
			return null;
		}
		CompletableFuture<@Nullable String> lsHoverFuture = this.lsBasedHover.getHoverInfoFuture(textViewer, hoverRegion);
		final var lsHtmlHoverContent = new AtomicReference<@Nullable String>();
		final var jdtHoverControlInput = new AtomicReference<@Nullable JavadocBrowserInformationControlInput>();

		JavadocBrowserInformationControlInput input;
		IJavaElement javaElement = null;
		JavadocBrowserInformationControlInput previous = null;
		int leadingImageWidth = 0;
		String jdtHtmlHoverContent = "";

		try {
			CompletableFuture.allOf(
					CompletableFuture
					.runAsync(
							() -> jdtHoverControlInput.set((JavadocBrowserInformationControlInput) super.getHoverInfo2(textViewer, hoverRegion))),
					lsHoverFuture.thenAccept(lsHtmlHoverContent::set)
			).get(1000, TimeUnit.MILLISECONDS);

			if (lsHtmlHoverContent.get() == null) {
				// No hover content from Language Servers. Return null to let JDT compute the hover using its own Hover Providers
				return null;
			}
			input = jdtHoverControlInput.get();
			if (input != null) {
				previous = (JavadocBrowserInformationControlInput) input.getPrevious();
				javaElement = input.getElement();
				leadingImageWidth = input.getLeadingImageWidth();
				jdtHtmlHoverContent = input.getHtml();
			}
		} catch (ExecutionException e) {
			LanguageServerPlugin.logWarning("Javadoc unavailable. Failed to obtain it.", e);
			// Return null to let JDT compute the hover using its own Hover Providers
			return null;
		} catch (InterruptedException e) {
			LanguageServerPlugin.logWarning("Javadoc unavailable. Failed to obtain it.", e);
			Thread.currentThread().interrupt();
			// Return null to let JDT compute the hover using its own Hover Providers
			return null;
		} catch (TimeoutException e) {
			LanguageServerPlugin.logWarning("Timeout waiting for data to generate LS hover", e);
			// Return null to let JDT compute the hover using its own Hover Providers
			return null;
		}

		/*
		 *  LS HTML and JDT HTML are two HTML docs that need to be combined. JDT HTML comes with embedded CSS.
		 *  Therefore it is best to insert LS HTML body inside the body of JDT HTML to take advantage of the JDT CSS.
		 */
		String content = formatContent(lsHtmlHoverContent.get(), jdtHtmlHoverContent);
		return new JavadocBrowserInformationControlInput(previous, javaElement, content, leadingImageWidth);
	}

	private String formatContent(@Nullable String lsContent, @Nullable String jdtContent) {
		if (lsContent != null && lsContent.trim().length() > 0 && jdtContent != null
				&& jdtContent.trim().length() > 0) {
			return concatenateHtml(lsContent, jdtContent);
		} else if (lsContent != null && (jdtContent == null || jdtContent.trim().isEmpty())) {
			return wrapHtml(lsContent).toString();
		} else {
			return (lsContent == null ? "" : lsContent) + (jdtContent == null ? "" : jdtContent);
		}
	}

	private static StringBuilder wrapHtml(String html) {
		/*
		 * No JDT content. Means no JDT CSS part either. Therefore add JDT CSS chunk to it.
		 */
		ColorRegistry registry = JFaceResources.getColorRegistry();
		RGB fgRGB = registry.getRGB("org.eclipse.jdt.ui.Javadoc.foregroundColor"); //$NON-NLS-1$
		RGB bgRGB= registry.getRGB("org.eclipse.jdt.ui.Javadoc.backgroundColor"); //$NON-NLS-1$

		final var buffer = new StringBuilder(html);
		HTMLPrinter.insertPageProlog(buffer, 0, fgRGB, bgRGB, getStyleSheet());
		HTMLPrinter.addPageEpilog(buffer);
		return buffer;
	}

	private static String concatenateHtml(String lsHtml, String jdtHtml) {
		int insertPosition = jdtHtml.indexOf(BODY_OPEN);
		if (insertPosition >= 0) {
			insertPosition = jdtHtml.indexOf('>', insertPosition);
			if (insertPosition >= 0 && insertPosition < jdtHtml.length()) {
				// skip <body> tag close
				insertPosition++;
				int bodyStartIdx = lsHtml.indexOf(BODY_OPEN);
				int bodyEndIdx = -1;
				if (bodyStartIdx >= 0) {
					bodyStartIdx = lsHtml.indexOf('>', bodyStartIdx);
					if (bodyStartIdx >= 0 && bodyStartIdx < lsHtml.length()) {
						// skip <body> tag close
						bodyStartIdx++;
						bodyEndIdx = lsHtml.indexOf(BODY_CLOSE, bodyStartIdx);
						if (bodyEndIdx >= bodyStartIdx && bodyEndIdx <= lsHtml.length()) {
							return jdtHtml.substring(0, insertPosition) + lsHtml.substring(bodyStartIdx, bodyEndIdx) + SEPARATOR + jdtHtml.substring(insertPosition);
						}
						LanguageServerPlugin.logWarning("LS Hover Html and JDT hover html were naively concatenated as LS hover HTML BODY tag closing bracket is at invalid position", null);
					} else {
						LanguageServerPlugin.logWarning("LS Hover Html and JDT hover html were naively concatenated as LS hover HTML BODY tag closing bracket wasn't found", null);
					}
				} else {
					LanguageServerPlugin.logWarning("LS Hover Html and JDT hover html were naively concatenated as LS hover HTML BODY tag wasn't found", null);
				}
			} else {
				LanguageServerPlugin.logWarning("LS Hover Html and JDT hover html were naively concatenated as JDT hover HTML BODY tag closing bracket wasn't found", null);
			}
		} else {
			LanguageServerPlugin.logWarning("LS Hover Html and JDT hover html were naively concatenated as JDT hover HTML BODY tag wasn't found", null);
		}
		return lsHtml + SEPARATOR + jdtHtml;
	}

	@Override
	public @Nullable IRegion getHoverRegion(ITextViewer textViewer, int offset) {
		return this.lsBasedHover.getHoverRegion(textViewer, offset);
	}

	/**
	 * Taken from {@link JavadocHover}. It's <code>private</code>. See {@link JavadocHover#getStyleSheet()}.
	 * @return CSS as string
	 */
	private static @Nullable String getStyleSheet() {
		if (fgStyleSheet == null) {
			fgStyleSheet= JavadocHover.loadStyleSheet("/JavadocHoverStyleSheet.css"); //$NON-NLS-1$
		}
		String css = fgStyleSheet;
		if (css != null) {
			FontData fontData= JFaceResources.getFontRegistry().getFontData(PreferenceConstants.APPEARANCE_JAVADOC_FONT)[0];
			css = HTMLPrinter.convertTopLevelFont(css, fontData);
		}
		return css;
	}

}
