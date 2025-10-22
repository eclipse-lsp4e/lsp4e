/*******************************************************************************
 * Copyright (c) 2017, 2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.)   - initial implementation
 *  Lucas Bullen (Red Hat Inc.)     - Bug 508472 - Outline to provide "Link with Editor"
 *                                  - Bug 517428 - Requests sent before initialization
 *  Dietrich Travkin (Solunar GmbH) - Issue 254  - Add outline view contents filtering
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.lateNonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.reconciler.AbstractReconciler;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.internal.ArrayUtil;
import org.eclipse.lsp4e.internal.CancellationUtil;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.navigator.ICommonContentExtensionSite;
import org.eclipse.ui.navigator.ICommonContentProvider;
import org.eclipse.ui.progress.PendingUpdateAdapter;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.WorkbenchViewerSetup;

public class LSSymbolsContentProvider implements ICommonContentProvider, ITreeContentProvider {

	public static final String VIEWER_PROPERTY_IS_QUICK_OUTLINE = "isQuickOutline"; //$NON-NLS-1$

	public static final class OutlineViewerInput {

		public final IDocument document;
		public final LanguageServerWrapper wrapper;
		public final @Nullable ITextEditor textEditor;
		public final @Nullable IFile documentFile;
		private final @Nullable URI documentURI;

		public OutlineViewerInput(IDocument document, LanguageServerWrapper wrapper, @Nullable ITextEditor textEditor) {
			this.document = document;
			IPath path = LSPEclipseUtils.toPath(document);
			if (path == null) {
				documentFile = null;
				URI uri = null;
				URI docUri = LSPEclipseUtils.toUri(document);
				if (docUri != null) {
					TextDocumentIdentifier docIdentifier = LSPEclipseUtils.toTextDocumentIdentifier(docUri);
					// Set the documentURI if valid URI for LSs that use custom protocols e.g. jdt://
					try {
						uri = new URI(docIdentifier.getUri());
					} catch (URISyntaxException e) {
						// Ignore
					}
				}
				documentURI = uri;
			} else {
				documentFile = LSPEclipseUtils.getFile(path);
				documentURI = documentFile == null ? LSPEclipseUtils.toUri(path) : LSPEclipseUtils.toUri(documentFile);
			}
			this.wrapper = wrapper;
			this.textEditor = textEditor;
		}
	}

	interface IOutlineUpdater {
		void install();

		void uninstall();
	}

	private final class DocumentChangedOutlineUpdater implements IDocumentListener, IOutlineUpdater {

		private final IDocument document;

		DocumentChangedOutlineUpdater(IDocument document) {
			this.document = document;
		}

		@Override
		public void install() {
			document.addDocumentListener(this);
			refreshTreeContentFromLS();
		}

		@Override
		public void uninstall() {
			document.removeDocumentListener(this);
		}

		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			// Do nothing
		}

		@Override
		public void documentChanged(DocumentEvent event) {
			refreshTreeContentFromLS();
		}

	}

	private final class PreferencesChangedOutlineUpdater implements IPreferenceChangeListener, IOutlineUpdater {

		@Override
		public void install() {
			IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
			preferences.addPreferenceChangeListener(this);
		}

		@Override
		public void uninstall() {
			IEclipsePreferences preferences = InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID);
			preferences.removePreferenceChangeListener(this);
		}

		@Override
		public void preferenceChange(PreferenceChangeEvent event) {
			final var viewer = LSSymbolsContentProvider.this.viewer;
			if (viewer == null)
				return;
			if (event.getKey().startsWith(CNFOutlinePage.HIDE_DOCUMENT_SYMBOL_KIND_PREFERENCE_PREFIX)) {
				viewer.getControl().getDisplay().asyncExec(() -> {
					if (!viewer.getTree().isDisposed()) {
						viewer.refresh();
					}
				});
			}
		}
	}

	private final class ReconcilerOutlineUpdater extends AbstractReconciler implements IOutlineUpdater {

		private final ITextViewer textViewer;

		ReconcilerOutlineUpdater(ITextViewer textViewer) {
			this.textViewer = textViewer;
			super.setIsIncrementalReconciler(false);
			super.setIsAllowedToModifyDocument(false);
		}

		@Override
		public void install() {
			super.install(textViewer);
		}

		@Override
		protected void initialProcess() {
			refreshTreeContentFromLS();
		}

		@Override
		protected void process(final DirtyRegion dirtyRegion) {
			refreshTreeContentFromLS();
		}

		@Override
		protected void reconcilerDocumentChanged(final IDocument newDocument) {
			// Do nothing
		}

		@Override
		public @Nullable IReconcilingStrategy getReconcilingStrategy(final String contentType) {
			return null;
		}
	}

	private final class ResourceChangeOutlineUpdater implements IResourceChangeListener, IOutlineUpdater {

		private final IResource resource;

		public ResourceChangeOutlineUpdater(IResource resource) {
			this.resource = resource;
		}

		@Override
		public void install() {
			resource.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE);
		}

		@Override
		public void uninstall() {
			resource.getWorkspace().removeResourceChangeListener(this);
		}

		@Override
		public void resourceChanged(IResourceChangeEvent event) {
			final var viewer = LSSymbolsContentProvider.this.viewer;
			if (viewer == null)
				return;

			if ((event.getDelta().getFlags() ^ IResourceDelta.MARKERS) != 0) {
				try {
					event.getDelta().accept(delta -> {
						if (delta.getResource().equals(this.resource)) {
							viewer.getControl().getDisplay().asyncExec(() -> {
								if (!viewer.getControl().isDisposed() && viewer instanceof StructuredViewer) {
									viewer.refresh(true);
								}
							});
						}
						return delta.getResource().getFullPath().isPrefixOf(this.resource.getFullPath());
					});
				} catch (CoreException e) {
					LanguageServerPlugin.logError(e);
				}
			}
		}
	}

	private @Nullable TreeViewer viewer;
	private volatile @Nullable Throwable lastError;
	private OutlineViewerInput outlineViewerInput = lateNonNull();

	private final SymbolsModel symbolsModel = new SymbolsModel();
	private volatile @Nullable CompletableFuture<@Nullable List<Either<SymbolInformation, DocumentSymbol>>> symbols;
	private final boolean refreshOnResourceChanged;
	private boolean isQuickOutline;
	private @Nullable IOutlineUpdater outlineUpdater;
	private IOutlineUpdater preferencesDependantOutlineUpdater;

	public LSSymbolsContentProvider() {
		this(false);
	}

	public LSSymbolsContentProvider(boolean refreshOnResourceChanged) {
		this.refreshOnResourceChanged = refreshOnResourceChanged;
		preferencesDependantOutlineUpdater = new PreferencesChangedOutlineUpdater();
	}

	@Override
	public void init(final ICommonContentExtensionSite aConfig) {
		preferencesDependantOutlineUpdater.install();
	}

	@Override
	public void inputChanged(Viewer viewer, @Nullable Object oldInput, @Nullable Object newInput) {
		if (outlineUpdater != null) {
			outlineUpdater.uninstall();
		}

		if (newInput == null) {
			// happens during org.eclipse.jface.viewers.ContentViewer#handleDispose when
			// Quick Outline is closed
			return;
		}

		this.viewer = (TreeViewer) viewer;

		// this enables limiting the number of outline entries to mitigate UI freezes
		WorkbenchViewerSetup.setupViewer(this.viewer);

		isQuickOutline = Boolean.TRUE.equals(viewer.getData(VIEWER_PROPERTY_IS_QUICK_OUTLINE));

		outlineViewerInput = (OutlineViewerInput) newInput;
		symbolsModel.setUri(outlineViewerInput.documentURI);

		// eagerly refresh the content tree, esp. important for the Quick Outline
		// because otherwise the outline will be blank for 1-2 seconds initially
		refreshTreeContentFromLS();

		if (!isQuickOutline) {
			outlineUpdater = createOutlineUpdater();
			outlineUpdater.install();
		}
	}

	private IOutlineUpdater createOutlineUpdater() {
		if (refreshOnResourceChanged && outlineViewerInput.documentFile != null) {
			return new ResourceChangeOutlineUpdater(outlineViewerInput.documentFile);
		}
		final ITextViewer textViewer = LSPEclipseUtils.getTextViewer(outlineViewerInput.textEditor);
		return textViewer == null ? new DocumentChangedOutlineUpdater(outlineViewerInput.document)
				: new ReconcilerOutlineUpdater(textViewer);
	}

	@Override
	public Object[] getElements(@Nullable Object inputElement) {
		if (symbols != null && !symbols.isDone()) {
			return new Object[] { new PendingUpdateAdapter() };
		}
		if (lastError != null && symbolsModel.getElements().length == 0) {
			return new Object[] { "An error occurred, see log for details" }; //$NON-NLS-1$
		}
		return ArrayUtil.filter(symbolsModel.getElements(), element -> !hideElement(element));
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		return ArrayUtil.filter(symbolsModel.getChildren(parentElement), element -> !hideElement(element));
	}

	private boolean hideElement(Object element) {
		SymbolKind kind = null;

		if (element instanceof DocumentSymbol documentSymbol) {
			kind = documentSymbol.getKind();
		} else if (element instanceof DocumentSymbolWithURI documentSymbolWithURI) {
			kind = documentSymbolWithURI.symbol.getKind();
		} else if (element instanceof SymbolInformation symbolInformation) {
			kind = symbolInformation.getKind();
		}

		if (kind != null) {
			return OutlineViewHideSymbolKindMenuContributor.isHideSymbolKind(kind);
		}
		return false;
	}

	@Override
	public @Nullable Object getParent(Object element) {
		return symbolsModel.getParent(element);
	}

	@Override
	public boolean hasChildren(Object parentElement) {
		return symbolsModel.hasChildren(parentElement);
	}

	protected void refreshTreeContentFromLS() {
		final var viewer = this.viewer;
		if(viewer == null)
			return;
		final URI documentURI = outlineViewerInput.documentURI;
		if (documentURI == null) {
			final var exception = new IllegalStateException("documentURI == null");  //$NON-NLS-1$
			lastError = exception;
			LanguageServerPlugin.logError(exception);
			viewer.getControl().getDisplay().asyncExec(viewer::refresh);
			return;
		}

		if (symbols != null) {
			symbols.cancel(true);
		}

		final var params = new DocumentSymbolParams(LSPEclipseUtils.toTextDocumentIdentifier(documentURI));
		final var symbols = this.symbols = outlineViewerInput.wrapper.execute(ls -> ls.getTextDocumentService().documentSymbol(params));
		symbols.thenAcceptAsync(response -> {
			symbolsModel.update(response);
			lastError = null;

			final var linkWithEditor = isQuickOutline || InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID)
					.getBoolean(CNFOutlinePage.LINK_WITH_EDITOR_PREFERENCE, true);

			viewer.getControl().getDisplay().asyncExec(() -> {
				if(viewer.getTree().isDisposed()) {
					return;
				}

				final int EXPAND_ROOT_LEVEL = 2;  // Expansion level that displays root node and its children
				if (isQuickOutline) {
					viewer.refresh();
					viewer.expandToLevel(EXPAND_ROOT_LEVEL);
				} else {
					TreePath[] expandedElements = viewer.getExpandedTreePaths();
					TreePath[] initialSelection = ((ITreeSelection) viewer.getSelection()).getPaths();
					viewer.refresh();
					if (expandedElements.length > 0) {
						viewer.setExpandedTreePaths(Arrays.stream(expandedElements)
								.map(symbolsModel::toUpdatedSymbol)
								.filter(Objects::nonNull).toArray(TreePath[]::new));
						viewer.setSelection(new TreeSelection(Arrays.stream(initialSelection)
								.map(symbolsModel::toUpdatedSymbol)
								.filter(Objects::nonNull).toArray(TreePath[]::new)));
					} else {
						viewer.expandToLevel(EXPAND_ROOT_LEVEL);
					}
				}

				if (linkWithEditor) {
					ITextEditor editor = UI.getActiveTextEditor();
					if (editor != null) {
						final var selection = (ITextSelection) editor.getSelectionProvider().getSelection();
						CNFOutlinePage.refreshTreeSelection(viewer, selection.getOffset(), outlineViewerInput.document);
					}
				}
			});
		});

		symbols.exceptionally(ex -> {
			if (!(ex instanceof CancellationException || CancellationUtil.isRequestCancelledException(ex))) {
				lastError = ex;
				LanguageServerPlugin.logError(ex);
			}
			viewer.getControl().getDisplay().asyncExec(viewer::refresh);
			return Collections.emptyList();
		});
	}

	@Override
	public void dispose() {
		if (outlineUpdater != null) {
			outlineUpdater.uninstall();
		}
		preferencesDependantOutlineUpdater.uninstall();
	}

	@Override
	public void restoreState(final IMemento aMemento) {
	}

	@Override
	public void saveState(final IMemento aMemento) {
	}
}
