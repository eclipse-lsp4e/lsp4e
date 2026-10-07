/*******************************************************************************
 * Copyright (c) 2026 🌘🧑‍💻⚗️ (EclipseDevLab) and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria - 🌘🧑‍💻⚗️ (EclipseDevLab) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.diagnostics;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerLifecycle;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.FullDocumentDiagnosticReport;
import org.eclipse.lsp4j.PublishDiagnosticsParams;

public class PullDiagnosticsReconcilingStrategy implements IReconcilingStrategy, ITextViewerLifecycle {

	private @Nullable IDocument document;
	private boolean enabled = true;

	@Override
	public void install(@Nullable ITextViewer textViewer) {
		enabled = true;
		updateDiagnostics();
	}

	@Override
	public void uninstall() {
		enabled = false;
	}

	@Override
	public void setDocument(@Nullable IDocument document) {
		this.document = document;
		// delay update on document set as server could be initializing and the registration options for textdocument/diagnostic
		// capability not yet sent. Delaying can then prevent from synchronization issues without being perceived as annoyance
		// by users at this stage as the editor is initializing in other ways as well.
		CompletableFuture.runAsync(this::updateDiagnostics, CompletableFuture.delayedExecutor(2000, TimeUnit.MILLISECONDS));
	}

	private void updateDiagnostics() {
		final var doc = this.document;
		if (doc == null || !enabled) {
			return;
		}
		var id = LSPEclipseUtils.toTextDocumentIdentifier(doc);
		if (id == null) {
			return;
		}
		DocumentDiagnosticParams param = new DocumentDiagnosticParams(id);
		LanguageServers.forDocument(doc)
			.withFilter(cap -> cap.getTextDocument().getDiagnostic() != null)
			.collectAll((wrapper, ls) -> ls.getTextDocumentService().diagnostic(param).thenAccept(report -> {
				if (report.isLeft() && report.getLeft() instanceof FullDocumentDiagnosticReport docReport) {
					var uri = LSPEclipseUtils.toUri(this.document);
					var client = wrapper.getLanguageClient();
					if (uri != null && client != null) {
						var params = new PublishDiagnosticsParams(uri.toString(), docReport.getItems());
						client.publishDiagnostics(params);
					}
				}
			}));
	}

	@Override
	public void reconcile(@Nullable DirtyRegion dirtyRegion, @Nullable IRegion subRegion) {
		updateDiagnostics();
	}

	@Override
	public void reconcile(@Nullable IRegion partition) {
		updateDiagnostics();
	}

}
