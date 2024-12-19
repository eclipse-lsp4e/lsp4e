/*******************************************************************************
 * Copyright (c) 2023 Bachmann electronic GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Gesa Hentschke (Bachmann electronic GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.typeHierarchy;

import static org.eclipse.lsp4e.internal.ArrayUtil.NO_OBJECTS;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.internal.Pair;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.views.HierarchyViewInput;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.ui.PlatformUI;

public class TypeHierarchyViewContentProvider implements ITreeContentProvider {
	private @Nullable TreeViewer treeViewer;
	private @Nullable LanguageServerWrapper languageServerWrapper;
	private List<TypeHierarchyItem> hierarchyItems = Collections.emptyList();
	public boolean showSuperTypes = true;
	public @Nullable IDocument document;

	@Override
	public Object[] getElements(@Nullable Object inputElement) {
		if (hierarchyItems.isEmpty()) {
			return new Object[] { Messages.TH_no_type_hierarchy };
		}
		return hierarchyItems.toArray();
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof TypeHierarchyItem parentItem && languageServerWrapper != null) {
			try {
				return languageServerWrapper.execute(ls -> {
					TextDocumentService textDocumentService = ls.getTextDocumentService();

					return showSuperTypes
							? textDocumentService.typeHierarchySupertypes(new TypeHierarchySupertypesParams(parentItem))
							: textDocumentService.typeHierarchySubtypes(new TypeHierarchySubtypesParams(parentItem));
				})
					.thenApply(list -> list == null ? NO_OBJECTS : list.toArray())
					.get(500, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return NO_OBJECTS;
	}

	@Override
	public @Nullable Object getParent(Object element) {
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		return true;
	}

	@Override
	public void inputChanged(final Viewer viewer, final @Nullable Object oldInput, final @Nullable Object newInput) {
		ITreeContentProvider.super.inputChanged(viewer, oldInput, newInput);

		if (newInput instanceof HierarchyViewInput viewInput) {
			try {
				initialise(viewInput.getDocument(), viewInput.getOffset(), (TreeViewer) viewer);
			} catch (BadLocationException e) {
				handleRootError();
			}
		} else {
			handleRootError();
		}

	}

	private void initialise(final IDocument document, final int offset, TreeViewer viewer) throws BadLocationException {
		LanguageServerDocumentExecutor executor = LanguageServers.forDocument(document)
				.withCapability(ServerCapabilities::getTypeHierarchyProvider);
		if (!executor.anyMatching()) {
			handleRootError();
			return;
		}
		TypeHierarchyPrepareParams prepareParams = toTypeHierarchyPrepareParams(offset, document);
		if (prepareParams == null) {
			return;
		}
		executor.computeFirst((w, ls) -> ls.getTextDocumentService().prepareTypeHierarchy(prepareParams)
				.thenApply(result -> new Pair<>(w, result))).thenAccept(o -> o.ifPresentOrElse(p -> {
					languageServerWrapper = p.first();
					if (!p.second().isEmpty()) {
						hierarchyItems = p.second();
						treeViewer = viewer;
						PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
							final var treeViewer = this.treeViewer;
							if (treeViewer != null) {
								treeViewer.refresh();
								treeViewer.expandToLevel(2);
								if (!hierarchyItems.isEmpty()) { // if handleRootError() has been called in the meantime
									treeViewer.getControl().setEnabled(true);
									treeViewer.setSelection(new StructuredSelection(hierarchyItems.get(0)));
								}
							}
						});
					}
				}, this::handleRootError)).handle((result, error) -> {
					if (error != null) {
						handleRootError();
					}
					return result;
				});
	}

	private static @Nullable TypeHierarchyPrepareParams toTypeHierarchyPrepareParams(int offset, final IDocument document) throws BadLocationException {
		Position position =  LSPEclipseUtils.toPosition(offset, document);
		TextDocumentIdentifier documentIdentifier = LSPEclipseUtils.toTextDocumentIdentifier(document);
		if(documentIdentifier == null)
			return null;
		return new TypeHierarchyPrepareParams(documentIdentifier, position);
	}

	private void handleRootError() {
		hierarchyItems = Collections.emptyList();
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			if (treeViewer != null) {
				treeViewer.refresh();
			}
		});
	}

	@Override
	public void dispose() {
		if (treeViewer != null) {
			treeViewer.getControl().dispose();
			treeViewer = null;
		}
	}

}
