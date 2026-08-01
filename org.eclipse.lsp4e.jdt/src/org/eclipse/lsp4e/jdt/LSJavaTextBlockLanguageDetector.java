/*******************************************************************************
 * Copyright (c) 2026 Daniel Schmid and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Daniel Schmid - Initial API and implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt;

import java.net.URI;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;

/**
 * Allows plugins providing custom logic for which language servers should be
 * used within Java text blocks.
 */
public interface LSJavaTextBlockLanguageDetector {
	/**
	 * Creates a {@link URI} indicating the language server that should be used for
	 * a Java text block.
	 *
	 * @param compilationUnit
	 *                            The compilation unit corresponding to the Java
	 *                            file.
	 * @param textViewer
	 *                            The text viewer the Java file is opened in.
	 * @param textBlockRegion
	 *                            The source code region of the text block within
	 *                            the Java file.
	 * @return A {@link URI} where documents corresponding to that {@link URI} are
	 *         recognized by that language server. This {@link URI} does not need to
	 *         be resolvable.
	 */
	@Nullable
	URI createURIForTextBlock(ICompilationUnit compilationUnit, ITextViewer textViewer, IRegion textBlockRegion);
}
