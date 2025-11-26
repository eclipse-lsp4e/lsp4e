/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.symbols;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.quickaccess.IQuickAccessComputer;
import org.eclipse.ui.quickaccess.IQuickAccessComputerExtension;
import org.eclipse.ui.quickaccess.QuickAccessElement;

public class WorkspaceSymbolsQuickAccessProvider implements IQuickAccessComputer, IQuickAccessComputerExtension {

	private static final QuickAccessElement[] NO_QUICK_ACCESS_ELEMENTS = new QuickAccessElement[0];

	private @Nullable List<LanguageServerWrapper> usedLanguageServerWrappers;

	@Override
	public QuickAccessElement[] computeElements() {
		return NO_QUICK_ACCESS_ELEMENTS;
	}

	@Override
	public void resetState() {
	}

	@Override
	public boolean needsRefresh() {
		final var usedLanguageServerWrappers = this.usedLanguageServerWrappers;
		return usedLanguageServerWrappers == null
				|| !usedLanguageServerWrappers.equals(LanguageServiceAccessor.getStartedWrappers(capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider()), true));
	}

	@Override
	public QuickAccessElement[] computeElements(String query, IProgressMonitor monitor) {
		final var usedLanguageServerWrappers = this.usedLanguageServerWrappers = LanguageServiceAccessor
				.getStartedWrappers(capabilities -> LSPEclipseUtils.hasCapability(capabilities.getWorkspaceSymbolProvider()), true);
		if (usedLanguageServerWrappers.isEmpty()) {
			return NO_QUICK_ACCESS_ELEMENTS;
		}
		final var params = new WorkspaceSymbolParams(query);
		final var res = Collections.synchronizedList(new ArrayList<QuickAccessElement>());

		try {
			CompletableFuture.allOf(usedLanguageServerWrappers.stream()
					.map(w -> w.execute(ls -> ls.getWorkspaceService().symbol(params).thenAcceptAsync((@Nullable Either<List<? extends SymbolInformation>, List<@Nullable ? extends WorkspaceSymbol>> symbols) -> {
						if (symbols != null) {
							res.addAll(LSPSymbolInWorkspaceDialog.eitherToWorkspaceSymbols(symbols).stream().filter(Objects::nonNull).map(WorkspaceSymbolQuickAccessElement::new)
									.toList());
						}
					}))).toArray(CompletableFuture[]::new)).get(1, TimeUnit.SECONDS);
		}
		catch (ExecutionException | InterruptedException e) {
			LanguageServerPlugin.logError(e);
		} catch (TimeoutException e) {
			LanguageServerPlugin.logWarning("Could not get workspace symbols due to timeout after 1 second in `workspace/symbol`"); //$NON-NLS-1$
		}

		return res.toArray(QuickAccessElement[]::new);
	}

}
