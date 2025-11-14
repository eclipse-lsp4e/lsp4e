/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation.
 *******************************************************************************/
package org.eclipse.lsp4e.debug.presentation;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.tm4e.core.grammar.IGrammar;
import org.eclipse.tm4e.registry.TMEclipseRegistryPlugin;
import org.eclipse.tm4e.ui.TMUIPlugin;
import org.eclipse.tm4e.ui.text.TMPresentationReconciler;

/**
 * Editor wrapper that normalizes access to the underlying text widget and
 * allows optional syntax highlighting if TM4E plugin is present.
 */
abstract class SourceCodeEditor extends Composite {

	private static final class PlainEditor extends SourceCodeEditor {
		private final StyledText text;

		PlainEditor(Composite parent, int style) {
			super(parent, style);
			text = new StyledText(this, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
		}

		@Override
		StyledText getTextWidget() {
			return text;
		}
	}

	/**
	 * Source editor based on TM4E TextMate syntax highlighting
	 */
	private static final class TMEditor extends SourceCodeEditor {

		private final TMPresentationReconciler reconciler = new TMPresentationReconciler();
		private final SourceViewer viewer;
		private final Document doc;

		TMEditor(final Composite parent, final int style) {
			super(parent, style);
			viewer = new SourceViewer(this, null, null, false, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
			viewer.configure(new SourceViewerConfiguration() {
				@Override
				public IPresentationReconciler getPresentationReconciler(final @Nullable ISourceViewer sourceViewer) {
					return reconciler;
				}
			});
			doc = new Document();
			viewer.setDocument(doc);
		}

		@Override
		StyledText getTextWidget() {
			return viewer.getTextWidget();
		}

		@Override
		void setText(String text) {
			doc.set(text);
		}

		@Override
		String getText() {
			return viewer.getTextWidget().getText();
		}

		@Override
		void configureForResource(@Nullable IResource resource) {
			if (resource == null) {
				return;
			}
			final var contentTypes = Platform.getContentTypeManager().findContentTypesFor(resource.getName());
			final IGrammar grammar = TMEclipseRegistryPlugin.getGrammarRegistryManager().getGrammarFor(contentTypes);
			reconciler.setGrammar(grammar);
			if (grammar != null) {
				final var theme = TMUIPlugin.getThemeManager().getThemeForScope(grammar.getScopeName());
				final StyledText styledText = viewer.getTextWidget();
				styledText.setFont(JFaceResources.getTextFont());
				styledText.setForeground(null);
				styledText.setBackground(null);
				theme.initializeViewerColors(styledText);
				reconciler.setTheme(theme);
			}
		}
	}

	static SourceCodeEditor create(Composite parent, int style) {
		if (Platform.getBundle("org.eclipse.tm4e.ui") != null) {
			return new TMEditor(parent, style);
		}
		return new PlainEditor(parent, style);
	}

	private SourceCodeEditor(Composite parent, int style) {
		super(parent, style);
		GridLayoutFactory.swtDefaults().margins(0, 0).applyTo(this);
	}

	/**
	 * Underlying text widget to use for caret/selection operations.
	 */
	abstract StyledText getTextWidget();

	/**
	 * Optional hook for syntax highlighting based on the resource's content type.
	 */
	void configureForResource(@Nullable IResource resource) {
		// default no-op
	}

	void setEditorLayoutData(GridDataFactory data) {
		data.applyTo(this);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(getTextWidget());
	}

	String getText() {
		return getTextWidget().getText();
	}

	void setText(String text) {
		getTextWidget().setText(text);
	}

	void addModifyListener(ModifyListener l) {
		getTextWidget().addModifyListener(l);
	}

	Point getSelection() {
		return getTextWidget().getSelection();
	}

	void setSelection(int caret) {
		getTextWidget().setSelection(caret);
	}

	@Override
	public void setToolTipText(@Nullable String string) {
		getTextWidget().setToolTipText(string);
	}

	@Override
	public void setEnabled(boolean enabled) {
		getTextWidget().setEnabled(enabled);
	}
}
