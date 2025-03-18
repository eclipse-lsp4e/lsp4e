/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;


import org.eclipse.jface.dialogs.PopupDialog;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.internal.NullSafetyHelper;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.navigator.CommonViewerSorter;

public class TypeHierarchyDialog extends PopupDialog {
	/**
	 * Indicates the current mode of the hierarchy dialog, if <code>true</code>
	 * super types are displayed, otherwise subtypes are displayed.
	 */
	private static boolean showSuperTypes = true;

	private final LanguageServerDefinition lsDefinition;
	private final IDocument document;
	private final ITextSelection textSelection;

	public TypeHierarchyDialog(Shell parentShell, ITextSelection textSelection, IDocument document, LanguageServerDefinition ls) {
		super(parentShell, PopupDialog.INFOPOPUPRESIZE_SHELLSTYLE, true, true, true, false, false, null, null);
		this.lsDefinition = ls;
		this.document = document;
		this.textSelection = textSelection;
		create();
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		final var filteredTree = new FilteredTree(parent, SWT.BORDER, new PatternFilter(), true, false) {
			@Override
			protected Composite createFilterControls(Composite parent) {
				final var composite = new Composite(NullSafetyHelper.castNonNull(parent), SWT.NONE);
				final var layout = new GridLayout(2, false);
				layout.horizontalSpacing=0;
				layout.marginWidth=0;
				layout.marginHeight=0;
				composite.setLayout(layout);

				Composite filterControls = super.createFilterControls(composite);
				filterControls.setLayoutData(new GridData(SWT.FILL, SWT.NONE, true, false));

				createToolBar(composite);

				return composite;
			}

			private void createToolBar(Composite composite) {
				final var toolbar = new ToolBar(composite, HOVER_SHELLSTYLE);
				final var hierchyModeItem = new ToolItem(toolbar, SWT.PUSH);
				updateHierarchyModeItem(hierchyModeItem, showSuperTypes);

				hierchyModeItem.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						showSuperTypes = !showSuperTypes;
						updateHierarchyModeItem(hierchyModeItem, showSuperTypes);
						setHierarchyMode(getViewer(), showSuperTypes);
					}
				});
			}

			private void updateHierarchyModeItem(ToolItem hierchyModeItem, boolean showSuperTypes) {
				hierchyModeItem.setImage(LSPImages.getImage(showSuperTypes ? LSPImages.IMG_SUBTYPE : LSPImages.IMG_SUPERTYPE));
				hierchyModeItem.setToolTipText(showSuperTypes ? Messages.typeHierarchy_show_subtypes : Messages.typeHierarchy_show_supertypes);
			}
		};
		TreeViewer viewer = filteredTree.getViewer();
		setHierarchyMode(viewer, showSuperTypes);
		// Maybe consider making this a CNF defined label provider
		viewer.setLabelProvider(new TypeHierarchyItemLabelProvider());
		viewer.setAutoExpandLevel(2);
		viewer.addDoubleClickListener(event -> {
			if(((IStructuredSelection)event.getSelection()).getFirstElement() instanceof final TypeHierarchyItem item) {
				LSPEclipseUtils.open(item.getUri(), item.getSelectionRange());
			}
		});

		final var sorter = new CommonViewerSorter();
		viewer.setComparator(sorter);

		viewer.setUseHashlookup(true);

		viewer.setInput(textSelection);
		return filteredTree;
	}

	private void setHierarchyMode(TreeViewer viewer, boolean showSuperTypes) {
		viewer.setContentProvider(new TypeHierarchyContentProvider(lsDefinition, document, showSuperTypes));
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(Messages.typeHierarchy);
		shell.setSize(280, 300);
	}
}
