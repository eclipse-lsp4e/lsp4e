/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.inlinecompletion;

import static org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionStatus.State.*;
import static org.eclipse.lsp4e.test.inlinecompletion.InlineCompletionTest.*;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionStatus;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionStatusWidget;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.ui.InlineCompletionPreferencePage;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.keys.IBindingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests of the status bar widget of inline completion, and of switching between the trigger modes.
 */
public class InlineCompletionStatusWidgetTest extends AbstractTestWithProject {

	private static final int TIMEOUT = 5_000;
	private static final String TOGGLE = "org.eclipse.lsp4e.inlineCompletion.toggleTriggerMode";

	private final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
	private final InlineCompletionStatus status = InlineCompletionStatus.getInstance();
	private Shell shell;
	private CLabel label;

	@BeforeEach
	public void createWidget() {
		// closing the editor of an earlier test publishes the status asynchronously
		while (UI.getDisplay().readAndDispatch()) {
			// flush it, so that it does not overwrite the states set here
		}
		store.setValue(InlineCompletionPreferencePage.PREF_AUTOMATIC, true);
		status.setState(INITIALIZING);
		shell = new Shell(UI.getDisplay());
		shell.setLayout(new FillLayout());
		new InlineCompletionStatusWidget().fill(shell);
		label = (CLabel) shell.getChildren()[0];
	}

	@AfterEach
	public void disposeWidget() {
		shell.dispose();
		store.setToDefault(InlineCompletionPreferencePage.PREF_AUTOMATIC);
		status.setState(INITIALIZING);
	}

	@Test
	public void showsTheStateWhileThereIsSomethingToShow() {
		assertFalse(label.getVisible(), "no server is known to have the capability");

		status.setState(READY);
		assertTrue(label.getVisible());
		assertTrue(label.getToolTipText().contains("Ready (automatic)"), label.getToolTipText());
		final Image ready = label.getImage();
		assertNotNull(ready);

		status.setState(ERROR);
		assertTrue(label.getToolTipText().contains("Error"), label.getToolTipText());
		assertNotSame(ready, label.getImage());

		status.setState(INITIALIZING);
		assertFalse(label.getVisible());
	}

	@Test
	public void isHiddenWhileDisabled() {
		SwitchableEnablement.enabled = false;
		try {
			status.setState(READY);
			assertFalse(label.getVisible());
		} finally {
			SwitchableEnablement.enabled = true;
		}
	}

	@Test
	public void animatesWhileFetching() {
		status.setState(READY);
		final Image ready = label.getImage();
		status.setState(FETCHING);
		waitForAndAssertCondition(TIMEOUT, () -> label.getImage() != ready);
		final Image frame = label.getImage();
		waitForAndAssertCondition(TIMEOUT, () -> label.getImage() != frame);

		status.setState(READY);
		assertSame(ready, label.getImage());
		processEvents(200);
		assertSame(ready, label.getImage(), "the animation has stopped");
	}

	@Test
	public void clickingSwitchesTheTriggerMode() {
		status.setState(READY);
		click(1);
		assertFalse(store.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC));
		assertTrue(label.getToolTipText().contains("Ready (manual)"), label.getToolTipText());

		click(3);
		assertFalse(store.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC), "not the context menu's button");

		click(1);
		assertTrue(store.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC));
		assertTrue(label.getToolTipText().contains("Ready (automatic)"), label.getToolTipText());
	}

	private void click(int button) {
		final var event = new Event();
		event.button = button;
		label.notifyListeners(SWT.MouseUp, event);
	}

	@Test
	public void theContextMenuOffersTheStatusAndTheActions() {
		status.setState(READY);
		final Menu menu = label.getMenu();
		menu.notifyListeners(SWT.Show, new Event());
		final List<MenuItem> items = Arrays.stream(menu.getItems()).filter(item -> (item.getStyle() & SWT.SEPARATOR) == 0)
				.toList();
		assertEquals(4, items.size(), Arrays.toString(menu.getItems()));
		final MenuItem state = items.get(0);
		assertEquals("Inline Completion Status: Ready (automatic)", state.getText());
		assertNotNull(state.getImage());
		assertFalse(state.getEnabled());
		assertTrue(items.get(1).getSelection());
		menu.notifyListeners(SWT.Hide, new Event());
	}

	@Test
	public void theCommandSwitchesTheTriggerMode() throws Exception {
		final IHandlerService handlers = PlatformUI.getWorkbench().getService(IHandlerService.class);
		handlers.executeCommand(TOGGLE, null);
		assertFalse(store.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC));
		handlers.executeCommand(TOGGLE, null);
		assertTrue(store.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC));
	}

	@Test
	public void theCommandIsBoundInTextEditors(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		openEditor(TestUtils.createUniqueTestFile(project, "hello\n"));
		final IBindingService bindings = PlatformUI.getWorkbench().getService(IBindingService.class);
		final boolean mac = Platform.OS_MACOSX.equals(Platform.getOS());
		final KeySequence expected = KeySequence.getInstance(mac ? "M2+M4+\\" : "M2+M3+\\");
		final List<TriggerSequence> active = List.of(bindings.getActiveBindingsFor(TOGGLE));
		assertTrue(active.contains(expected), "active bindings: " + active);
	}

	@Test
	public void theStatusBarOfTheWorkbenchWindowHasTheWidget(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		openEditor(TestUtils.createUniqueTestFile(project, "hello\n"));
		waitForAndAssertCondition(TIMEOUT, () -> assertEquals(READY, status.getState()));
		final var labels = new ArrayList<CLabel>();
		collectStatusLabels(UI.getActiveShell(), labels);
		assertEquals(1, labels.size(), labels.toString());
		assertTrue(labels.get(0).getVisible());
	}

	private static void collectStatusLabels(Composite parent, List<CLabel> labels) {
		for (final Control child : parent.getChildren()) {
			if (child instanceof CLabel statusLabel && statusLabel.getToolTipText() != null
					&& statusLabel.getToolTipText().contains("Inline Completion")) {
				labels.add(statusLabel);
			} else if (child instanceof Composite composite) {
				collectStatusLabels(composite, labels);
			}
		}
	}
}
