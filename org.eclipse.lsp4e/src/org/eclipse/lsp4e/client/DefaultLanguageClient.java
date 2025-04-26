/*******************************************************************************
 * Copyright (c) 2016, 2024 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Miro Spoenemann (TypeFox) - extracted to separate file
 *  Rubén Porras Campo (Avaloq Evolution AG) - progress creation/notification implementation
 *******************************************************************************/
package org.eclipse.lsp4e.client;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.lateNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.ServerMessageHandler;
import org.eclipse.lsp4e.progress.LSPProgressManager;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;

/**
 * A default LanguageClient implementation which can be extended.
 */
public class DefaultLanguageClient implements LanguageClient {

	private Consumer<PublishDiagnosticsParams> diagnosticConsumer = lateNonNull();
	private final LSPProgressManager progressManager = new LSPProgressManager();

	private LanguageServer server = lateNonNull();
	private LanguageServerWrapper wrapper = lateNonNull();

	public final void connect(LanguageServer server, LanguageServerWrapper wrapper) {
		this.server = server;
		this.wrapper = wrapper;
		progressManager.connect(server, wrapper.serverDefinition);
	}

	public void setDiagnosticsConsumer(Consumer<PublishDiagnosticsParams> diagnosticConsumer) {
		this.diagnosticConsumer = diagnosticConsumer;
	}

	protected final LanguageServer getLanguageServer() {
		return server;
	}

	@Override
	public CompletableFuture<List<@Nullable Object>> configuration(ConfigurationParams configurationParams) {
		// override as needed
		final var list = new ArrayList<@Nullable Object>(configurationParams.getItems().size());
		for (int i = 0; i < configurationParams.getItems().size(); i++) {
			list.add(null);
		}
		return CompletableFuture.completedFuture(list);
	}

	@Override
	public void telemetryEvent(@Nullable Object object) {
		// TODO
	}

	@Override
	public final CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
		return ServerMessageHandler.showMessageRequest(wrapper, requestParams);
	}

	@Override
	public final void showMessage(MessageParams messageParams) {
		ServerMessageHandler.showMessage(wrapper.serverDefinition.label, messageParams);
	}

	@Override
	public final void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
		diagnosticConsumer.accept(diagnostics);
	}

	@Override
	public final void logMessage(MessageParams message) {
		CompletableFuture.runAsync(() -> ServerMessageHandler.logMessage(wrapper, message));
	}

	@Override
	public CompletableFuture<Void> createProgress(final WorkDoneProgressCreateParams params) {
		return progressManager.createProgress(params);
	}

	@Override
	public void notifyProgress(final ProgressParams params) {
		progressManager.notifyProgress(params);
	}

	@Override
	public final CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
		return CompletableFuture.supplyAsync(() -> {
			final var job = new Job(Messages.serverEdit) {
				@Override
				public IStatus run(IProgressMonitor monitor) {
					LSPEclipseUtils.applyWorkspaceEdit(params.getEdit(), params.getLabel());
					return Status.OK_STATUS;
				}
			};
			job.schedule();
			try {
				job.join();
				return new ApplyWorkspaceEditResponse(true);
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				Thread.currentThread().interrupt();
				return new ApplyWorkspaceEditResponse(false);
			}
		});
	}

	@Override
	public CompletableFuture<@Nullable Void> registerCapability(RegistrationParams params) {
		return CompletableFuture.runAsync(() -> wrapper.registerCapability(params));
	}

	@Override
	public CompletableFuture<@Nullable Void> unregisterCapability(UnregistrationParams params) {
		return CompletableFuture.runAsync(() -> wrapper.unregisterCapability(params));
	}

	@Override
	public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
		return CompletableFuture.completedFuture(LSPEclipseUtils.getWorkspaceFolders());
	}

	@Override
	public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
		return CompletableFuture.supplyAsync(() -> {
			UI.getDisplay().syncExec(() -> {
				var location = new Location(params.getUri(), params.getSelection());
				LSPEclipseUtils.openInEditor(location);
			});
			return new ShowDocumentResult(true);
		});
	}

	private void updateCodeMinings() {
		IWorkbenchPage activePage = UI.getActivePage();
		if (activePage == null) {
			return;
		}
		IEditorReference[] editors = activePage.getEditorReferences();
		for (IEditorReference ref : editors) {
			var editor = ref.getEditor(false);
			var textViewer = Adapters.adapt(editor, ITextViewer.class, true);
			if (textViewer != null && textViewer instanceof SourceViewer sourceViewer) {
				sourceViewer.updateCodeMinings();
			}
		}
	}

	@Override
	public CompletableFuture<@Nullable Void> refreshCodeLenses() {
		return CompletableFuture.runAsync(() -> UI.getDisplay().syncExec(this::updateCodeMinings));
	}

	@Override
	public CompletableFuture<@Nullable Void> refreshInlayHints() {
		return CompletableFuture.runAsync(() -> UI.getDisplay().syncExec(this::updateCodeMinings));
	}

	/**
	 * Dispose language client.
	 */
	public void dispose() {
		progressManager.dispose();
	}
}
