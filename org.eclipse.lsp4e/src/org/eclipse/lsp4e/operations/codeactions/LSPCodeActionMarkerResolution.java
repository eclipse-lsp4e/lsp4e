/*******************************************************************************
 * Copyright (c) 2016-2023 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.operations.codeactions;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.AbstractInformationControlManager;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension2;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.text.contentassist.ContentAssistant;
import org.eclipse.jface.text.quickassist.IQuickAssistAssistant;
import org.eclipse.jface.text.quickassist.QuickAssistAssistant;
import org.eclipse.jface.text.source.ISourceViewerExtension3;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerProjectExecutor;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.operations.diagnostics.LSPDiagnosticsToMarkers;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IMarkerResolution;
import org.eclipse.ui.IMarkerResolution2;
import org.eclipse.ui.IMarkerResolutionGenerator2;
import org.eclipse.ui.internal.progress.ProgressInfoItem;

public class LSPCodeActionMarkerResolution implements IMarkerResolutionGenerator2 {

	private static final IMarkerResolution[] NO_MARKER_RESOLUTIONS = new IMarkerResolution[0];

	private static final String LSP_REMEDIATION = "lspCodeActions"; //$NON-NLS-1$

	private static final IMarkerResolution2 COMPUTING = new IMarkerResolution2() {

		@Override
		public void run(IMarker marker) {
			// join on Future?
		}

		@Override
		public String getLabel() {
			return Messages.computing;
		}

		@Override
		public Image getImage() {
			// load class so image is loaded
			return JFaceResources.getImage(ProgressInfoItem.class.getPackageName() + ".PROGRESS_DEFAULT"); //$NON-NLS-1$
		}

		@Override
		public String getDescription() {
			return Messages.computing;
		}
	};

	@Override
	public IMarkerResolution[] getResolutions(IMarker marker) {
		Object att = null;
		try {
			att = marker.getAttribute(LSP_REMEDIATION);
			if (att == null) {
				checkMarkerResolution(marker);
				att = marker.getAttribute(LSP_REMEDIATION);
			}
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
			return NO_MARKER_RESOLUTIONS;
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
			return NO_MARKER_RESOLUTIONS;
		}
		if (att == COMPUTING) {
			return new IMarkerResolution[] { COMPUTING };
		} else if (att == null) {
			return NO_MARKER_RESOLUTIONS;
		}
		return ((List<Either<Command, CodeAction>>) att).stream().filter(LSPCodeActionMarkerResolution::canPerform)
				.map(command -> command.map(CommandMarkerResolution::new, CodeActionMarkerResolution::new))
				.toArray(IMarkerResolution[]::new);
	}

	private void checkMarkerResolution(IMarker marker) throws CoreException, InterruptedException, ExecutionException {
		IResource res = marker.getResource();
		if (res instanceof IFile file) {
			Object[] attributes = marker.getAttributes(new String[]{LSPDiagnosticsToMarkers.LANGUAGE_SERVER_ID, LSPDiagnosticsToMarkers.LSP_DIAGNOSTIC});
			LanguageServerProjectExecutor executor = LanguageServers.forProject(file.getProject())
					.withCapability(ServerCapabilities::getCodeActionProvider)
					// try to use same LS as the one that created the marker
					.withPreferredServer(LanguageServersRegistry.getInstance().getDefinition((String) attributes[0]));
			if (executor.anyMatching()) {
				final var diagnostic = (Diagnostic) attributes[1];
				final var context = new CodeActionContext(List.of(diagnostic));
				final var params = new CodeActionParams();
				params.setContext(context);
				params.setTextDocument(castNonNull(LSPEclipseUtils.toTextDocumentIdentifier(res)));
				params.setRange(diagnostic.getRange());
				if (marker.exists()) { // otherwise the marker has been removed by now
					marker.setAttribute(LSP_REMEDIATION, COMPUTING);
					try {
						executor.computeFirst(ls -> ls.getTextDocumentService().codeAction(params)).thenAccept(optional -> {
							try {
								marker.setAttribute(LSP_REMEDIATION, optional.orElse(Collections.emptyList()));
							} catch (CoreException e) {
								LanguageServerPlugin.logError(e);
							}
						}).thenRun(() -> {
							Display display = UI.getDisplay();
							display.asyncExec(() -> {
								ITextViewer textViewer = UI.getActiveTextViewer();
								if (textViewer != null) {
									// Do not re-invoke hover right away as hover may not be showing at all yet
									display.timerExec(500, () -> reinvokeQuickfixProposalsIfNecessary(textViewer));
								}
							});
						}).get(300, TimeUnit.MILLISECONDS);
						// wait a bit to avoid showing too much "Computing" without looking like a freeze
					} catch (TimeoutException e) {
						LanguageServerPlugin.logWarning(
								"Could not get code actions due to timeout after 300 milliseconds in `textDocument/codeAction`"); //$NON-NLS-1$
					}
				}
			}
		}
	}

	private void reinvokeQuickfixProposalsIfNecessary(ITextViewer textViewer) {
		try {
			// Quick assist proposals popup case
			if (textViewer instanceof final ISourceViewerExtension3 textViewer3) {
				IQuickAssistAssistant quickAssistant = textViewer3.getQuickAssistAssistant();
				if (quickAssistant != null) {
					Field f = QuickAssistAssistant.class.getDeclaredField("fQuickAssistAssistantImpl"); //$NON-NLS-1$
					f.setAccessible(true);
					final var ca = (ContentAssistant) f.get(quickAssistant);
					Method m = ContentAssistant.class.getDeclaredMethod("isProposalPopupActive"); //$NON-NLS-1$
					m.setAccessible(true);
					final var isProposalPopupActive = (Boolean) castNonNull(m.invoke(ca));
					if (isProposalPopupActive) {
						quickAssistant.showPossibleQuickAssists();
					}
				}
			}
			// Hover case
			if (textViewer instanceof final ITextViewerExtension2 textViewer2) {
				ITextHover hover = textViewer2.getCurrentTextHover();
				boolean hoverShowing = hover != null;
				if (hoverShowing) {
					Field f = TextViewer.class.getDeclaredField("fTextHoverManager"); //$NON-NLS-1$
					f.setAccessible(true);
					final var manager = (AbstractInformationControlManager) f.get(textViewer);
					if (manager != null) {
						manager.showInformation();
					}
				}
			}
		} catch (Exception e) {
			LanguageServerPlugin.logError(e);
		}
	}

	@Override
	public boolean hasResolutions(IMarker marker) {
		try {
			Object remediation = marker.getAttribute(LSP_REMEDIATION);
			if (remediation == null) {
				checkMarkerResolution(marker);
				remediation = marker.getAttribute(LSP_REMEDIATION);
			}
			return remediation == COMPUTING || (remediation instanceof Collection<?> collection && !collection.isEmpty());
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
		}
		return false;
	}

	static boolean canPerform(@Nullable Either<Command, CodeAction> command) {
		if (command == null) {
			return false;
		}
		if (command.isLeft()) {
			return true;
		}
		CodeAction action = command.getRight();
		if (action.getDisabled() != null) {
			return false;
		}
		WorkspaceEdit edit = action.getEdit();
		if (edit == null) {
			return true;
		}
		List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = edit.getDocumentChanges();
		if (documentChanges != null) {
			for (Either<TextDocumentEdit, ResourceOperation> change : documentChanges) {
				if (change.isLeft()) {
					TextDocumentEdit textedit = change.getLeft();
					VersionedTextDocumentIdentifier id = textedit.getTextDocument();
					URI uri = URI.create(id.getUri());
					if (LSPEclipseUtils.isReadOnly(uri)) {
						return false;
					}
				}
			}
		} else {
			Map<String, List<TextEdit>> changes = edit.getChanges();
			if (changes != null) {
				for (java.util.Map.Entry<String, List<TextEdit>> textEdit : changes.entrySet()) {
					URI uri = URI.create(textEdit.getKey());
					if (LSPEclipseUtils.isReadOnly(uri)) {
						return false;
					}
				}
			}
		}

		return true;
	}
}
