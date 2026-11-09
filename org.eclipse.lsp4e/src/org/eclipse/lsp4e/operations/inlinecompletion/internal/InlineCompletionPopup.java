/*******************************************************************************
 * Copyright (c) 2025, 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.AbstractInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * Popup above the cursor showing information about the inline completion, such
 * as the index of the shown suggestion among the alternatives.
 */
public class InlineCompletionPopup extends AbstractInformationControl {

	private static final int MARGIN_WIDTH = 5;
	private static final int MARGIN_HEIGHT = 5;
	private static final int MAX_POPUP_WIDTH = 400;
	private static final int POPUP_OFFSET_FROM_CURSOR = 5;
	private static final int AUTO_HIDE_DELAY_MS = 3000;

	/** The popup shown, of which there is one at most; used on the UI thread only. */
	private static @Nullable InlineCompletionPopup shown;

	private @Nullable Label infoLabel;
	private @Nullable String currentInfo;

	public InlineCompletionPopup(Shell parentShell) {
		super(parentShell, false);
		create();
	}

	@Override
	public boolean hasContents() {
		final String info = currentInfo;
		return info != null && !info.isEmpty();
	}

	@Override
	protected void createContent(Composite parent) {
		final var layout = new GridLayout(1, false);
		layout.marginWidth = MARGIN_WIDTH;
		layout.marginHeight = MARGIN_HEIGHT;
		parent.setLayout(layout);

		final var label = new Label(parent, SWT.NONE);
		label.setLayoutData(new GridData(GridData.FILL_BOTH));
		label.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND));
		label.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND));
		infoLabel = label;
	}

	@Override
	public void setInformation(@Nullable String information) {
		currentInfo = information;
		final Label label = infoLabel;
		if (label != null && !label.isDisposed()) {
			label.setText(information != null ? information : ""); //$NON-NLS-1$
			getShell().pack(true);
		}
	}

	@Override
	public Point computeSizeHint() {
		final Point size = getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT);
		size.x = Math.min(size.x, MAX_POPUP_WIDTH);
		return size;
	}

	@Override
	public @Nullable IInformationControlCreator getInformationPresenterControlCreator() {
		return InlineCompletionPopup::new;
	}

	/**
	 * Shows a popup with a message above the given offset for a few seconds, instead
	 * of the one shown before, if any. Must be called on the UI thread.
	 *
	 * @param textViewer
	 *            the viewer
	 * @param message
	 *            the message; nothing is shown if it is empty
	 * @param offset
	 *            the document offset above which the popup is shown
	 */
	public static void showPopup(ITextViewer textViewer, String message, int offset) {
		hidePopup();
		final StyledText textWidget = textViewer.getTextWidget();
		if (message.isEmpty() || textWidget == null || textWidget.isDisposed()) {
			return;
		}
		// a folded offset has no place in the widget
		final int widgetOffset = textViewer instanceof ITextViewerExtension5 extension
				? extension.modelOffset2WidgetOffset(offset)
				: offset;
		if (widgetOffset < 0 || widgetOffset > textWidget.getCharCount()) {
			return;
		}
		final var popup = new InlineCompletionPopup(textWidget.getShell());
		popup.setInformation(message);

		final Point location = textWidget.toDisplay(textWidget.getLocationAtOffset(widgetOffset));
		final Point size = popup.computeSizeHint();
		location.y -= size.y + POPUP_OFFSET_FROM_CURSOR;

		popup.setLocation(location);
		popup.setSize(size.x, size.y);
		popup.setVisible(true);
		shown = popup;

		textWidget.getDisplay().timerExec(AUTO_HIDE_DELAY_MS, () -> {
			if (shown == popup) {
				hidePopup();
			}
		});
	}

	/**
	 * Hides the popup shown, if any. Must be called on the UI thread.
	 */
	public static void hidePopup() {
		final InlineCompletionPopup popup = shown;
		shown = null;
		if (popup != null && !popup.getShell().isDisposed()) {
			popup.dispose();
		}
	}

	/**
	 * Returns the message of the popup shown, or {@code null} if there is none; for
	 * tests.
	 */
	public static @Nullable String getShownMessage() {
		final InlineCompletionPopup popup = shown;
		return popup == null || popup.getShell().isDisposed() ? null : popup.currentInfo;
	}
}
