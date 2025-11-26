/*******************************************************************************
 * Copyright (c) 2016, 2019 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.BoldStylerProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.outline.CNFOutlinePage;
import org.eclipse.lsp4e.outline.SymbolsLabelProvider;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.FilteredItemsSelectionDialog;

public class LSPSymbolInWorkspaceDialog extends FilteredItemsSelectionDialog {

	private static final String DIALOG_SETTINGS = LSPSymbolInWorkspaceDialog.class.getName();

	private static final class InternalSymbolsLabelProvider extends SymbolsLabelProvider {

		private @Nullable String pattern;
		private final BoldStylerProvider stylerProvider;

		public InternalSymbolsLabelProvider(BoldStylerProvider stylerProvider) {
			super(true, InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID)
					.getBoolean(CNFOutlinePage.SHOW_KIND_PREFERENCE, false));
			this.stylerProvider = stylerProvider;
		}

		@Override
		public StyledString getStyledText(@Nullable Object element) {
			StyledString styledString = super.getStyledText(element);
			final var pattern = this.pattern;
			if (pattern != null) {
				int index = styledString.getString().toLowerCase().indexOf(pattern);
				if (index != -1) {
					styledString.setStyle(index, pattern.length(), stylerProvider.getBoldStyler());
				}
			}
			return styledString;
		}

		@Override
		protected int getMaxSeverity(IResource resource, IDocument doc, Range range)
				throws CoreException, BadLocationException {
			int maxSeverity = -1;
			for (IMarker marker : resource.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)) {
				int offset = marker.getAttribute(IMarker.CHAR_START, -1);
				if (offset != -1) {
					maxSeverity = Math.max(maxSeverity, marker.getAttribute(IMarker.SEVERITY, -1));
				}
			}
			return maxSeverity;
		}

		public void setPattern(@Nullable String pattern) {
			this.pattern = pattern;
		}
	}

	private final class InternalItemsFilter extends ItemsFilter {

		@Override
		public boolean matchItem(@Nullable Object item) {
			return true;
		}

		@Override
		public boolean isConsistentItem(@Nullable Object item) {
			return true;
		}

		@Override
		public boolean isSubFilter(@Nullable ItemsFilter filter) {
			return false;
		}
	}

	private final InternalSymbolsLabelProvider labelProvider;

	private final IProject project;

	private @Nullable List<CompletableFuture<@Nullable Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>>> request;

	public LSPSymbolInWorkspaceDialog(Shell shell, IProject project, BoldStylerProvider stylerProvider) {
		super(shell);
		this.project = project;
		this.labelProvider = new InternalSymbolsLabelProvider(stylerProvider);
		setMessage(Messages.LSPSymbolInWorkspaceDialog_DialogLabel);
		setTitle(Messages.LSPSymbolInWorkspaceDialog_DialogTitle);
		setListLabelProvider(labelProvider);
	}

	@Override
	protected ItemsFilter createFilter() {
		final var itemsFilter = new InternalItemsFilter();
		labelProvider.setPattern(itemsFilter.getPattern());
		return itemsFilter;
	}

	@Override
	protected void fillContentProvider(AbstractContentProvider contentProvider, ItemsFilter itemsFilter,
			IProgressMonitor monitor) throws CoreException {
		if (request != null) {
			request.forEach(f -> f.cancel(true));
		}
		if (itemsFilter.getPattern().isEmpty()) {
			return;
		}
		final var params = new WorkspaceSymbolParams(itemsFilter.getPattern());
		request = LanguageServers.forProject(project) //
				.withCapability(ServerCapabilities::getWorkspaceSymbolProvider) //
				.computeAll((w, ls) -> ls.getWorkspaceService().symbol(params));
		request.stream().map((
				CompletableFuture<@Nullable Either<List<? extends SymbolInformation>, List<@Nullable ? extends WorkspaceSymbol>>> f) -> f
						.thenApply(LSPSymbolInWorkspaceDialog::eitherToWorkspaceSymbols))
				.forEach(cf -> {
					if (monitor.isCanceled()) {
						return;
					}
					try {
						for (Object item : cf.get(1, TimeUnit.SECONDS)) {
							contentProvider.add(item, itemsFilter);
						}
					} catch (ExecutionException e) {
						LanguageServerPlugin.logError(e);
					} catch (InterruptedException e) {
						LanguageServerPlugin.logError(e);
						Thread.currentThread().interrupt();
					} catch (TimeoutException e) {
						LanguageServerPlugin.logWarning(
								"Could not get workspace symbols due to timeout after 1 seconds in `workspace/symbol`"); //$NON-NLS-1$
					}
				});
	}

	@Override
	public String getElementName(Object item) {
		return ((WorkspaceSymbol) item).getName();
	}

	@Override
	protected Comparator<WorkspaceSymbol> getItemsComparator() {
		return (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName());
	}

	@Override
	protected IStatus validateItem(Object item) {
		return Status.OK_STATUS;
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		IDialogSettings settings = LanguageServerPlugin.getDefault().getDialogSettings().getSection(DIALOG_SETTINGS);
		if (settings == null) {
			settings = LanguageServerPlugin.getDefault().getDialogSettings().addNewSection(DIALOG_SETTINGS);
		}
		return settings;
	}

	@Override
	protected @Nullable Control createExtendedContentArea(Composite parent) {
		return null;
	}

	private static List<WorkspaceSymbol> toWorkspaceSymbols(@Nullable List<? extends SymbolInformation> source) {
		return source == null //
				? List.of()
				: source.stream() //
						.map(LSPSymbolInWorkspaceDialog::toWorkspaceSymbol) //
						.filter(Objects::nonNull) //
						.toList();
	}

	private static @Nullable WorkspaceSymbol toWorkspaceSymbol(@Nullable SymbolInformation symbolinformation) {
		if (symbolinformation == null) {
			return null;
		}
		final var res = new WorkspaceSymbol();
		res.setName(symbolinformation.getName());
		res.setLocation(Either.forLeft(symbolinformation.getLocation()));
		res.setKind(symbolinformation.getKind());
		res.setContainerName(symbolinformation.getContainerName());
		List<SymbolTag> tags = symbolinformation.getTags() != null ? new ArrayList<>(symbolinformation.getTags())
				: new ArrayList<>(1);
		if (symbolinformation.getDeprecated() != null && symbolinformation.getDeprecated().booleanValue()) {
			tags.add(SymbolTag.Deprecated);
		}
		res.setTags(tags);
		return res;
	}

	static List<@Nullable ? extends WorkspaceSymbol> eitherToWorkspaceSymbols(
			final @Nullable Either<List<? extends SymbolInformation>, List<@Nullable ? extends WorkspaceSymbol>> source) {
		return source == null //
						? Collections.emptyList()
						: source.map(LSPSymbolInWorkspaceDialog::toWorkspaceSymbols, Function.identity());
	}
}
