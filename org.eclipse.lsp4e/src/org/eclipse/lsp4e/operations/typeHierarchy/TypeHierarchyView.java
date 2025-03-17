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

import static org.eclipse.lsp4e.internal.NullSafetyHelper.lateNonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IFileBufferListener;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.viewers.DecoratingStyledCellLabelProvider;
import org.eclipse.jface.viewers.DecorationContext;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.FileBufferListenerAdapter;
import org.eclipse.lsp4e.internal.NullSafetyHelper;
import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4e.outline.SymbolsModel;
import org.eclipse.lsp4e.outline.SymbolsModel.DocumentSymbolWithURI;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.views.HierarchyViewInput;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.FilteredTree;
import org.eclipse.ui.dialogs.PatternFilter;
import org.eclipse.ui.internal.progress.ProgressManager;
import org.eclipse.ui.part.PageBook;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.PendingUpdateAdapter;

public class TypeHierarchyView extends ViewPart {

	private static final class SymbolsContainer {
		private final SymbolsModel symbolsModel;
		private volatile boolean isDirty = true;
		private boolean temporaryLoadedDocument = false;
		private volatile URI uri = lateNonNull();

		SymbolsContainer(URI uri) {
			this.symbolsModel = new SymbolsModel();
			setUri(uri);
		}

		public @Nullable IDocument getDocument() {
			IDocument document = null;
			var file = LSPEclipseUtils.getFileHandle(uri);
			if (file == null) {
				//load external file:
				document = LSPEclipseUtils.getDocument(uri);
				temporaryLoadedDocument = document != null;
			} else {
				document = LSPEclipseUtils.getExistingDocument(file);
				if (document == null) {
					document = LSPEclipseUtils.getDocument(file);
					temporaryLoadedDocument = document != null;
				}
			}
			return document;
		}

		public void setUri(URI uri) {
			dispose(); // disconnect old file
			this.uri = uri;
			this.symbolsModel.setUri(uri);
		}

		public void dispose() {
			if (temporaryLoadedDocument) {
				var file = LSPEclipseUtils.getFileHandle(uri);
				if (file != null) {
					try {
						FileBuffers.getTextFileBufferManager().disconnect(file.getFullPath(), LocationKind.IFILE,
								new NullProgressMonitor());
					} catch (CoreException e) {
						LanguageServerPlugin.logError(e);
					}
				} else {
					try {
						ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager();
						if (bufferManager != null) {
							bufferManager.disconnectFileStore(EFS.getStore(uri), new NullProgressMonitor());
						}
					} catch (CoreException e) {
						LanguageServerPlugin.logError(e);
					}
				}
			}
		}
	}

	public static final String ID = "org.eclipse.lsp4e.operations.typeHierarchy.TypeHierarchyView"; //$NON-NLS-1$

	private TypeHierarchyViewContentProvider contentProvider = new TypeHierarchyViewContentProvider();
	private DecoratingStyledCellLabelProvider symbolsLabelProvider = new DecoratingStyledCellLabelProvider(new SymbolsLabelProvider(), PlatformUI.getWorkbench().getDecoratorManager().getLabelDecorator(), DecorationContext.DEFAULT_CONTEXT);

	// widgets
	private PageBook pagebook = lateNonNull();
	private SashForm splitter = lateNonNull();
	private CLabel memberLabel = lateNonNull();

	// viewers
	private TableViewer memberViewer = lateNonNull();
	private TreeViewer treeViewer = lateNonNull();

	private HashMap<URI, SymbolsContainer> cachedSymbols = new HashMap<>();

	private final IFileBufferListener fileBufferListener = new FileBufferListenerAdapter() {
		@Override
		public void dirtyStateChanged(IFileBuffer buffer, boolean isDirty) {
			if (isDirty) {
				// check if this file has been cached:
				var cachedSymbol = getSymbolsContainer(buffer);
				if (cachedSymbol != null) {
					cachedSymbol.isDirty = true;
				}
			}
		}

		@Override
		public void underlyingFileMoved(@Nullable IFileBuffer buffer, IPath path) {
			var symbolsContainer = getSymbolsContainer(buffer);
			// check if this file has been cached:
			if (symbolsContainer != null && buffer != null) {
				var uri = LSPEclipseUtils.toUri(buffer);
				if (uri != null) {
					//update old cache:
					symbolsContainer.setUri(uri);
					//create new cache element under new URI
					cachedSymbols.put(uri, new SymbolsContainer(uri));
				}
			}
		}

		private @Nullable SymbolsContainer getSymbolsContainer(@Nullable IFileBuffer buffer) {
			if (buffer != null) {
				return cachedSymbols.get(LSPEclipseUtils.toUri(buffer));
			}
			return null;
		}

	};

	@Override
	public void setFocus() {
		pagebook.setFocus();
	}

	@Override
	public void createPartControl(Composite parent) {
		pagebook = new PageBook(parent, SWT.NULL);
		splitter = new SashForm(pagebook, SWT.VERTICAL);
		splitter.setLayoutData(new GridData(GridData.FILL_BOTH));
		splitter.addControlListener(new ControlListener() {
			@Override
			public void controlMoved(ControlEvent e) {
			}

			@Override
			public void controlResized(ControlEvent e) {
				splitter.setOrientation(getBestOrientation());
				splitter.layout();
			}
		});

		treeViewer = getFilteredTree(splitter).getViewer();
		treeViewer.setContentProvider(contentProvider);

		treeViewer.setLabelProvider(new TypeHierarchyItemLabelProvider());

		treeViewer.setUseHashlookup(true);
		treeViewer.getControl().setEnabled(false);
		treeViewer.addDoubleClickListener(event -> {
			var selection = ((IStructuredSelection) event.getSelection()).getFirstElement();
			if (selection instanceof TypeHierarchyItem item) {
				LSPEclipseUtils.open(item.getUri(), item.getSelectionRange());
			}
		});
		treeViewer.addSelectionChangedListener(this::onHierarchySelectionChanged);

		final var memberViewForm = new ViewForm(splitter, SWT.NONE);
		Control memberControl = createMemberControl(memberViewForm);
		memberControl.setEnabled(false);
		memberViewForm.setContent(memberControl);

		memberLabel = new CLabel(memberViewForm, SWT.NONE);
		memberViewForm.setTopLeft(memberLabel);

		final var infoText = new Label(pagebook, SWT.TOP | SWT.LEFT | SWT.WRAP);
		infoText.setText(Messages.TH_diplay_hint);
		pagebook.showPage(infoText);

		FileBuffers.getTextFileBufferManager().addFileBufferListener(fileBufferListener);
	}

	@Override
	public void dispose() {
		FileBuffers.getTextFileBufferManager().removeFileBufferListener(fileBufferListener);
		cachedSymbols.forEach((uri, container) -> container.dispose());
		super.dispose();
	}

	public void initialize(final IDocument document, final int offset) {
		treeViewer.setInput(new HierarchyViewInput(document, offset));
		pagebook.showPage(splitter);
	}

	private void onHierarchySelectionChanged(SelectionChangedEvent event) {
		if (event.getSelection() instanceof TreeSelection selection
				&& selection.getFirstElement() instanceof TypeHierarchyItem item) {
			URI uri = null;
			try {
				uri = new URI(item.getUri());
			} catch (URISyntaxException e) {
				LanguageServerPlugin.logError(e);
				return;
			}
			refreshMemberViewer(getSymbolsContainer(uri), item.getName(), false);
		}
	}

	private void refreshMemberViewer(@Nullable SymbolsContainer symbolsContainer, String typeName, boolean documentModified) {
		memberViewer.setInput(new PendingUpdateAdapter());
		memberLabel.setImage(JFaceResources.getImage(ProgressManager.WAITING_JOB_KEY));
		memberLabel.setText(Messages.outline_computingSymbols);
		PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
			if (symbolsContainer != null) {
				refreshSymbols(symbolsContainer, documentModified);
				var symbol = getDocumentSymbol(symbolsContainer, typeName);
				memberViewer.setInput(symbol);
				memberLabel.setText(typeName + getFilePath(symbolsContainer.uri));
				if (symbol != null ) {
					memberViewer.getControl().setEnabled(true);
					memberViewer.setSelection(new StructuredSelection(symbol));
					memberLabel.setImage(symbolsLabelProvider.getImage(symbol));
				}
			} else {
				memberViewer.setInput(null); // null clears it
				memberLabel.setText(Messages.TH_cannot_find_file);
				memberLabel.setImage(null); // null clears it
			}
		});
	}

	private String getFilePath(URI uri) {
		final String path = uri.getPath();
		return path == null ? "" : " - " + path; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private Control createMemberControl(ViewForm parent) {
		memberViewer = new TableViewer(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		memberViewer.setContentProvider(new TypeMemberContentProvider());
		memberViewer.setLabelProvider(symbolsLabelProvider);
		memberViewer.addOpenListener(event -> {
			if (((IStructuredSelection) event.getSelection()).getFirstElement() instanceof DocumentSymbolWithURI container) {
				var symbolsContainer = cachedSymbols.get(container.uri);
				if (symbolsContainer != null) {
					LSPEclipseUtils.open(symbolsContainer.uri.toASCIIString(), container.symbol.getRange());
				}
			}
		});
		return memberViewer.getControl();
	}

	private int getBestOrientation() {
		Point size = splitter.getSize();
		if (size.x != 0 && size.y != 0 && 3 * size.x < 2 * size.y) {
			return SWT.VERTICAL;
		}
		return SWT.HORIZONTAL;
	}

	private FilteredTree getFilteredTree(Composite parent) {
		return new FilteredTree(parent, SWT.BORDER, new PatternFilter(), true, false) {
			@Override
			protected Composite createFilterControls(@Nullable Composite parent) {
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
				final var toolbar = new ToolBar(composite, org.eclipse.jface.dialogs.PopupDialog.HOVER_SHELLSTYLE);
				final var hierchyModeItem = new ToolItem(toolbar, SWT.PUSH);
				updateHierarchyModeItem(hierchyModeItem, contentProvider.showSuperTypes);

				hierchyModeItem.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						contentProvider.showSuperTypes = !contentProvider.showSuperTypes;
						updateHierarchyModeItem(hierchyModeItem, contentProvider.showSuperTypes);
						getViewer().refresh();
					}
				});
			}

			private void updateHierarchyModeItem(ToolItem hierchyModeItem, boolean showSuperTypes) {
				hierchyModeItem.setImage(LSPImages.getImage(showSuperTypes ? LSPImages.IMG_SUBTYPE : LSPImages.IMG_SUPERTYPE));
				hierchyModeItem.setToolTipText(showSuperTypes ? Messages.typeHierarchy_show_subtypes : Messages.typeHierarchy_show_supertypes);
			}
		};
	}

	private SymbolsContainer getSymbolsContainer(URI uri) {
		return cachedSymbols.computeIfAbsent(uri, entry -> new SymbolsContainer(uri));
	}

	private synchronized void refreshSymbols(@Nullable SymbolsContainer symbolsContainer, boolean documentModified) {
		if (symbolsContainer == null || (!symbolsContainer.isDirty && !documentModified)) {
			return;
		}
		final IDocument document = symbolsContainer.getDocument();
		try {
			if (document == null) {
				symbolsContainer.symbolsModel.update(null);
				return;
			}
			final var identifier = LSPEclipseUtils.toTextDocumentIdentifier(document);
			if(identifier == null) {
				symbolsContainer.symbolsModel.update(null);
				return;
			}
			CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> symbols;
			final var params = new DocumentSymbolParams(identifier);
			CompletableFuture<Optional<LanguageServerWrapper>> languageServer = LanguageServers
					.forDocument(document)
					.withCapability(ServerCapabilities::getDocumentSymbolProvider)
					.computeFirst((w, ls) -> CompletableFuture.completedFuture(w));
			try {
				symbols = languageServer.get(500, TimeUnit.MILLISECONDS).filter(Objects::nonNull)
						.filter(LanguageServerWrapper::isActive)
						.map(s -> s.execute(ls -> ls.getTextDocumentService().documentSymbol(params)))
						.orElse(CompletableFuture.completedFuture(null));
			} catch (TimeoutException | ExecutionException | InterruptedException e) {
				LanguageServerPlugin.logError(e);
				symbols = CompletableFuture.completedFuture(null);
				if (e instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
			}
			symbols.thenAcceptAsync(response -> {
				symbolsContainer.symbolsModel.update(response);
				symbolsContainer.isDirty = false;
			}).join();
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private @Nullable DocumentSymbolWithURI getDocumentSymbol(@Nullable SymbolsContainer symbolsContainer, String typeName) {
		if (symbolsContainer != null) {
			var elements = symbolsContainer.symbolsModel.getElements();
			for (var element : elements) {
				if (element instanceof DocumentSymbolWithURI symbolContainer) {
					if (isClass(symbolContainer.symbol.getKind()) && symbolContainer.symbol.getName().equals(typeName)) {
						return new DocumentSymbolWithURI(symbolContainer.symbol, symbolContainer.uri);
					}
					var grandchild = searchInChildren(symbolContainer.symbol.getChildren(), typeName);
					if (grandchild != null) {
						return new DocumentSymbolWithURI(grandchild, symbolContainer.uri);
					}
				}
			}
		}
		return null;
	}

	private boolean isClass(SymbolKind kind) {
		return SymbolKind.Class.equals(kind) || SymbolKind.Struct.equals(kind)  ;
	}

	private @Nullable DocumentSymbol searchInChildren(@Nullable List<DocumentSymbol> children, String typeName) {
		if (children == null) {
			return null;
		}
		for (var child : children) {
			if (child.getName().equals(typeName) && isClass(child.getKind())) {
				return child;
			}
			var grandchild = searchInChildren(child.getChildren(), typeName);
			if (grandchild != null) {
				return grandchild;
			}
		}
		return null;
	}
}
