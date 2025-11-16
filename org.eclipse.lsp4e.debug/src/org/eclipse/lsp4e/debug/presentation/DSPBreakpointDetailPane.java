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

import static org.eclipse.lsp4e.internal.NullSafetyHelper.lateNonNull;
import static org.eclipse.swt.events.SelectionListener.widgetSelectedAdapter;

import java.util.Objects;

import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDetailPane;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4e.debug.breakpoints.DSPLineBreakpoint;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPartSite;
//

/**
 * Detail pane for LSP4E line breakpoints.
 */
public class DSPBreakpointDetailPane implements IDetailPane {

	public static final String ID = DSPBreakpointDetailPaneFactory.PANE_ID;
	public static final String NAME = DSPBreakpointDetailPaneFactory.PANE_NAME;
	public static final String DESCRIPTION = DSPBreakpointDetailPaneFactory.PANE_DESCRIPTION;

	private Composite control = lateNonNull();
	private Button enableConditionButton = lateNonNull();
	private SourceCodeEditor conditionEditor = lateNonNull();
	private Button enableHitConditionButton = lateNonNull();
	private Text hitConditionText = lateNonNull();
	private Spinner columnSpinner = lateNonNull();
	private @Nullable DSPLineBreakpoint selectedBP = null;
	private volatile boolean updating;

	/**
	 * @return the capabilities of the active debug adapter or null if none is
	 *         active
	 */
	private @Nullable Capabilities getDebugAdapterCapabilities() {
		final var ctx = DebugUITools.getDebugContext();
		if (ctx == null)
			return null;

		final Object targetObj = ctx instanceof final IDebugElement de //
				? de.getDebugTarget()
				: Adapters.adapt(ctx, IDebugTarget.class);
		return targetObj instanceof final DSPDebugTarget dt //
				? dt.getCapabilities()
				: null;
	}

	private @Nullable DSPLineBreakpoint getSelectedBreakPoint(final IStructuredSelection selection) {
		if (selection.size() != 1)
			return null;

		Object first = selection.getFirstElement();
		if (first == null)
			return null;

		if (first instanceof DSPLineBreakpoint d)
			return d;

		if (!(first instanceof IBreakpoint)) {
			Object adapted = org.eclipse.core.runtime.Platform.getAdapterManager().getAdapter(first, IBreakpoint.class);
			if (adapted instanceof IBreakpoint b) {
				first = b;
			}
		}
		return first instanceof DSPLineBreakpoint d ? d : null;
	}

	@Override
	public Control createControl(final Composite parent) {
		control = new Composite(parent, SWT.NONE);
		control.setBackground(UI.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
		GridLayoutFactory.swtDefaults().numColumns(1).equalWidth(false).applyTo(control);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(control);

		/*
		 * column row
		 */
		final var colRow = new Composite(control, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(2).margins(0, 0).equalWidth(false).applyTo(colRow);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(colRow);

		final var colHint = "Inline breakpoint column (1-based).\nSet > 0 to break at a specific character position on this line; set 0 to use the adapter's default stoppable location.";
		final var colLabel = new Label(colRow, SWT.NONE);
		colLabel.setText("Column");
		colLabel.setToolTipText(colHint);
		GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.CENTER).applyTo(colLabel);

		columnSpinner = new Spinner(colRow, SWT.BORDER);
		columnSpinner.setMinimum(0);
		columnSpinner.setMaximum(Integer.MAX_VALUE);
		columnSpinner.setToolTipText(colHint);
		GridDataFactory.swtDefaults().hint(100, SWT.DEFAULT).applyTo(columnSpinner);

		/*
		 * hit condition
		 */
		final var hitHint = "Trigger this breakpoint only on specific hit counts.\nExamples: 5 (5th hit), >= 10 (10th and later), % 3 == 0 (every 3rd hit).";
		final var hitRow = new Composite(control, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(2).margins(0, 0).equalWidth(false).applyTo(hitRow);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(hitRow);

		enableHitConditionButton = new Button(hitRow, SWT.CHECK);
		enableHitConditionButton.setText("Hit condition");
		enableHitConditionButton.setToolTipText(hitHint);
		GridDataFactory.swtDefaults().indent(0, 0).align(SWT.BEGINNING, SWT.CENTER).applyTo(enableHitConditionButton);

		hitConditionText = new Text(hitRow, SWT.BORDER);
		hitConditionText.setToolTipText(hitHint);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(hitConditionText);

		/*
		 * condition
		 */
		final var condHint = "The breakpoint stops only if the expression evaluates to true in the program context.";
		enableConditionButton = new Button(control, SWT.CHECK);
		enableConditionButton.setText("Condition");
		enableConditionButton.setToolTipText(condHint);

		conditionEditor = SourceCodeEditor.create(control, SWT.NONE);
		conditionEditor.setToolTipText(condHint);
		conditionEditor.setEditorLayoutData(GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, 120));

		hookListeners();
		return control;
	}

	@Override
	public void display(final IStructuredSelection selection) {
		final var selectedBP_ = selectedBP = getSelectedBreakPoint(selection);
		updating = true;
		try {
			if (selectedBP_ != null) {
				final var m = selectedBP_.getMarker();
				conditionEditor.configureForResource(m != null ? m.getResource() : null);
				final String hit = selectedBP_.getHitCondition();
				final boolean hitEnabled = hit != null && !hit.isBlank();
				enableHitConditionButton.setSelection(hitEnabled);
				if (hitEnabled) {
					final String hitNew = hit != null ? hit : "";
					final String hitOld = hitConditionText.getText();
					if (!Objects.equals(hitOld, hitNew)) {
						final Point sel2 = hitConditionText.getSelection();
						final int caret2 = sel2.y;
						hitConditionText.setText(hitNew);
						hitConditionText.setSelection(Math.min(caret2, hitConditionText.getCharCount()));
					}
				}

				final String condition = selectedBP_.getCondition();
				final boolean condEnabled = condition != null && !condition.isBlank();
				enableConditionButton.setSelection(condEnabled);
				if (condEnabled) {
					final String newText = condition != null ? condition : "";
					final String oldText = conditionEditor.getText();
					if (!Objects.equals(oldText, newText)) {
						final Point sel = conditionEditor.getSelection();
						final int caret = sel.y;
						conditionEditor.setText(newText);
						conditionEditor.setSelection(Math.min(caret, conditionEditor.getTextWidget().getCharCount()));
					}
				}

				columnSpinner.setSelection(Math.max(0, selectedBP_.getColumn()));
				setEnabled(true);

				// Apply capability gating
				final var caps = getDebugAdapterCapabilities(); // is null if no debug session is active

				final boolean condSupported = caps == null || caps.getSupportsConditionalBreakpoints();
				enableConditionButton.setEnabled(condSupported);
				conditionEditor.setEnabled(condSupported);

				final boolean hitSupported = caps == null || caps.getSupportsHitConditionalBreakpoints();
				enableHitConditionButton.setEnabled(hitSupported);
				hitConditionText.setEnabled(hitSupported);
			} else {
				enableConditionButton.setSelection(false);
				conditionEditor.setText("");
				enableHitConditionButton.setSelection(false);
				hitConditionText.setText("");
				columnSpinner.setSelection(0);
				setEnabled(false);
			}
		} finally {
			updating = false;
		}
	}

	@Override
	public void dispose() {
		if (control != null && !control.isDisposed()) {
			control.dispose();
		}
		selectedBP = null;
	}

	@Override
	public @Nullable String getDescription() {
		return DESCRIPTION;
	}

	@Override
	public String getID() {
		return ID;
	}

	@Override
	public String getName() {
		return NAME;
	}

	// removed legacy reflection-based TM4E viewer creation

	private void hookListeners() {
		enableConditionButton.addSelectionListener(widgetSelectedAdapter(e -> {
			boolean enabled = enableConditionButton.getSelection();
			if (!enabled && selectedBP != null) {
				try {
					selectedBP.setCondition(null);
				} catch (CoreException ex) {
					DSPPlugin.logError(ex);
				}
			}
		}));

		conditionEditor.addModifyListener(e -> {
			final var selectedBP = this.selectedBP;
			if (updating || selectedBP == null)
				return;

			if (!enableConditionButton.getSelection()) {
				enableConditionButton.setSelection(true);
			}
			try {
				selectedBP.setCondition(conditionEditor.getText());
			} catch (CoreException ex) {
				DSPPlugin.logError(ex);
			}
		});

		enableHitConditionButton.addSelectionListener(widgetSelectedAdapter(e -> {
			boolean enabled = enableHitConditionButton.getSelection();
			if (!enabled && selectedBP != null) {
				try {
					selectedBP.setHitCondition(null);
				} catch (CoreException ex) {
					DSPPlugin.logError(ex);
				}
			}
		}));

		hitConditionText.addModifyListener(e -> {
			final var selectedBP = this.selectedBP;
			if (updating || selectedBP == null)
				return;

			if (!enableHitConditionButton.getSelection()) {
				enableHitConditionButton.setSelection(true);
			}
			try {
				selectedBP.setHitCondition(hitConditionText.getText());
			} catch (CoreException ex) {
				DSPPlugin.logError(ex);
			}
		});

		columnSpinner.addSelectionListener(widgetSelectedAdapter(e -> {
			final var selectedBP = this.selectedBP;
			if (updating || selectedBP == null)
				return;

			try {
				selectedBP.setColumn(columnSpinner.getSelection());
			} catch (CoreException ex) {
				DSPPlugin.logError(ex);
			}
		}));
	}

	@Override
	public void init(final @Nullable IWorkbenchPartSite partSite) {
		// no-op
	}

	private void setEnabled(boolean enabled) {
		columnSpinner.setEnabled(enabled);
	}

	@Override
	public boolean setFocus() {
		if (conditionEditor != null && !conditionEditor.isDisposed() && conditionEditor.isEnabled()) {
			conditionEditor.getTextWidget().setFocus();
			return true;
		}
		return false;
	}
}
