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
 *  Lucas Bullen (Red Hat Inc.) - Bug 520053 - Clicking nodes in the 'Outline' should navigate
 *******************************************************************************/
package org.eclipse.lsp4e.outline;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.views.contentoutline.ContentOutline;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class EditorToOutlineAdapterFactory implements IAdapterFactory {

	private static final String OUTLINE_VIEW_ID = "org.eclipse.ui.views.ContentOutline"; //$NON-NLS-1$

	private static final Map<IEditorPart, LanguageServerWrapper> LANG_SERVER_CACHE = Collections
			.synchronizedMap(new WeakHashMap<>());

	@Override
	public <T> @Nullable T getAdapter(@Nullable Object adaptableObject, @Nullable Class<T> adapterType) {
		if ((adapterType == IContentOutlinePage.class || adapterType == CNFOutlinePage.class)
				&& adaptableObject instanceof IEditorPart editorPart) {
			final IEditorInput editorInput = editorPart.getEditorInput();

			if (editorInput != null && LanguageServersRegistry.getInstance().canUseLanguageServer(editorInput)) {

				// first try to get / remove language server from cache from a previous call
				LanguageServerWrapper server = LANG_SERVER_CACHE.remove(editorPart);
				if (server != null && server.isActive()) {
					return adapterType.cast(createOutlinePage(editorPart, server));
				}

				IDocument document = LSPEclipseUtils.getDocument(editorInput);
				if (document != null) {
					CompletableFuture<Optional<LanguageServerWrapper>> languageServer = LanguageServers
							.forDocument(document) //
							.withCapability(ServerCapabilities::getDocumentSymbolProvider) //
							.computeFirst((wrapper, ls) -> CompletableFuture.completedFuture(wrapper));
					try {
						return languageServer.get(50, TimeUnit.MILLISECONDS) //
								.filter(LanguageServerWrapper::isActive)
								.map(s -> adapterType.cast(createOutlinePage(editorPart, s))) //
								.orElse(null);
					} catch (TimeoutException e) {
						refreshContentOutlineAsync(languageServer, editorPart);
					} catch (ExecutionException e) {
						LanguageServerPlugin.logError(e);
					} catch (InterruptedException e) {
						LanguageServerPlugin.logError(e);
						Thread.currentThread().interrupt();
					}
				}
			}
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class<?>[] { IContentOutlinePage.class, CNFOutlinePage.class };
	}

	private static CNFOutlinePage createOutlinePage(IEditorPart editorPart, LanguageServerWrapper wrapper) {
		return new CNFOutlinePage(wrapper, UI.asTextEditor(editorPart));
	}

	private static void refreshContentOutlineAsync(CompletableFuture<Optional<LanguageServerWrapper>> wrapperFuture,
			IEditorPart editorPart) {
		// try to get language server asynchronously
		wrapperFuture.thenAcceptAsync(wrapper -> {
			if (wrapper.isPresent()) {
				Display.getDefault().asyncExec(() -> {
					var page = UI.getActivePage();
					if (page != null) {
						IViewPart viewPart = page.findView(OUTLINE_VIEW_ID);
						if (viewPart instanceof ContentOutline contentOutline) {
							LANG_SERVER_CACHE.put(editorPart, wrapper.get());
							contentOutline.partActivated(editorPart);
						}
					}
				});
			}
		});
	}
}
