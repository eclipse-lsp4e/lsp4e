/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import static org.eclipse.lsp4e.internal.ArrayUtil.NO_OBJECTS;
import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.content.IContentTypeManager;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchConfigurationTreeContentProvider;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.ArrayUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;

public class NewContentTypeLSPLaunchDialog extends Dialog {

	////
	// copied from ContentTypesPreferencePage

	private static final class ContentTypesLabelProvider extends LabelProvider {
		@Override
		public String getText(Object element) {
			final var contentType = (IContentType) element;
			return contentType.getName();
		}
	}

	private static final class ContentTypesContentProvider implements ITreeContentProvider {

		private @Nullable IContentTypeManager manager;

		@Override
		public Object[] getChildren(@Nullable Object parentElement) {
			final var manager = this.manager;
			if (manager == null)
				return NO_OBJECTS;
			final var baseType = (IContentType) parentElement;
			return ArrayUtil.filter(manager.getAllContentTypes(), type -> Objects.equals(type.getBaseType(), baseType));
		}

		@Override
		public @Nullable Object getParent(Object element) {
			final var contentType = (IContentType) element;
			return contentType.getBaseType();
		}

		@Override
		public boolean hasChildren(Object element) {
			return getChildren(element).length > 0;
		}

		@Override
		public Object[] getElements(@Nullable Object inputElement) {
			return getChildren(null);
		}

		@Override
		public void inputChanged(Viewer viewer, @Nullable Object oldInput, @Nullable Object newInput) {
			manager = (IContentTypeManager) newInput;
		}
	}

	protected @Nullable IContentType contentType;
	protected @Nullable ILaunchConfiguration launchConfig;
	protected Set<String> launchMode = Collections.emptySet();

	protected NewContentTypeLSPLaunchDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		final var res = (Composite)super.createDialogArea(parent);
		res.setLayout(new GridLayout(2, false));
		new Label(res, SWT.NONE).setText(Messages.NewContentTypeLSPLaunchDialog_associateContentType);
		new Label(res, SWT.NONE).setText(Messages.NewContentTypeLSPLaunchDialog_withLSPLaunch);
		// copied from ContentTypesPreferencePage
		final var contentTypesFilteredTree = new FilteredTree(res, SWT.BORDER, new PatternFilter(), true, false);
		TreeViewer contentTypesViewer = contentTypesFilteredTree.getViewer();
		contentTypesFilteredTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		contentTypesViewer.setContentProvider(new ContentTypesContentProvider());
		contentTypesViewer.setLabelProvider(new ContentTypesLabelProvider());
		contentTypesViewer.setComparator(new ViewerComparator());
		contentTypesViewer.setInput(Platform.getContentTypeManager());
		contentTypesViewer.addSelectionChangedListener(event -> {
			if (event.getSelection() instanceof IStructuredSelection sel && sel.getFirstElement() instanceof IContentType newContentType) {
				contentType = newContentType;
			}
			updateButtons();
		});
		// copied from LaunchConfigurationDialog : todo use LaunchConfigurationFilteredTree
		final var launchersFilteredTree = new FilteredTree(res, SWT.BORDER, new PatternFilter(), true, false);
		TreeViewer launchConfigViewer = launchersFilteredTree.getViewer();
		launchersFilteredTree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		launchConfigViewer.setLabelProvider(new DecoratingLabelProvider(DebugUITools.newDebugModelPresentation(), PlatformUI.getWorkbench().getDecoratorManager().getLabelDecorator()));
		launchConfigViewer.setContentProvider(new LaunchConfigurationTreeContentProvider(null, getShell()));
		launchConfigViewer.setInput(DebugPlugin.getDefault().getLaunchManager());
		final var launchModeViewer = new ComboViewer(res);
		final var comboGridData = new GridData(SWT.RIGHT, SWT.DEFAULT, true, false, 2, 1);
		comboGridData.widthHint = 100;
		launchModeViewer.getControl().setLayoutData(comboGridData);
		launchModeViewer.setContentProvider(new ArrayContentProvider());
		launchModeViewer.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object o) {
				final var res = new StringBuilder();
				for (String s : (Set<String>)o) {
					res.append(s);
					res.append(',');
				}
				if (res.length() > 0) {
					res.deleteCharAt(res.length() - 1);
				}
				return res.toString();
			}
		});
		launchConfigViewer.addSelectionChangedListener(event -> {
			ILaunchConfiguration newLaunchConfig = null;
			if (event.getSelection() instanceof IStructuredSelection sel) {
				if (sel.size() == 1 && sel.getFirstElement() instanceof ILaunchConfiguration launchConfig) {
					newLaunchConfig = launchConfig;
				}
			}
			launchConfig = newLaunchConfig;
			updateLaunchModes(launchModeViewer);
			updateButtons();
		});
		launchModeViewer.addSelectionChangedListener(event -> {
			if (event.getSelection() instanceof IStructuredSelection sel && sel.getFirstElement() instanceof Set mode) {
				launchMode = (Set<String>) mode;
			} else {
				launchMode = Collections.emptySet();
			}
			updateButtons();
		});
		return res;
	}

	@Override
	protected Control createContents(Composite parent) {
		Control res = super.createContents(parent);
		updateButtons();
		return res;
	}

	public IContentType getContentType() {
		return castNonNull(this.contentType);
	}

	public ILaunchConfiguration getLaunchConfiguration() {
		return castNonNull(this.launchConfig);
	}

	private void updateButtons() {
		getButton(OK).setEnabled(contentType != null && launchConfig != null && !launchMode.isEmpty());
	}

	private void updateLaunchModes(ComboViewer launchModeViewer) {
		final var launchConfig = this.launchConfig;
		if (launchConfig == null) {
			launchModeViewer.setInput(Collections.emptyList());
		} else {
			Set<Set<String>> modes = null;
			try {
				modes = launchConfig.getType().getSupportedModeCombinations();
			} catch (CoreException e) {
				LanguageServerPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, e.getMessage(), e));
			}
			if (modes == null) {
				modes = Set.of(Set.of(ILaunchManager.RUN_MODE));
			}
			launchModeViewer.setInput(modes);
			Object currentMode = null;
			if (!launchModeViewer.getStructuredSelection().isEmpty()) {
				currentMode = launchModeViewer.getStructuredSelection().getFirstElement();
			}
			if (currentMode == null || !modes.contains(currentMode)) {
				launchModeViewer.setSelection(new StructuredSelection());
				currentMode = null;
			}
			if (currentMode == null) {
				for (Set<String> mode : modes) {
					if (mode.size() == 1 && ILaunchManager.RUN_MODE.equals(mode.iterator().next())) {
						currentMode = mode;
						launchModeViewer.setSelection(new StructuredSelection(currentMode));
					}
				}
			}
			if (currentMode == null && !modes.isEmpty()) {
				launchModeViewer.setSelection(new StructuredSelection(modes.iterator().next()));
			}
		}
		updateButtons();
	}

	public Set<String> getLaunchMode() {
		return this.launchMode;
	}

}
