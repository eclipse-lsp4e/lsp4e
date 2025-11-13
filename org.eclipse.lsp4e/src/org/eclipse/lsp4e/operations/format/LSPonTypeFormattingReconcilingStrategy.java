/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.operations.format;

import java.util.ConcurrentModificationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextListener;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerLifecycle;
import org.eclipse.jface.text.TextEvent;
import org.eclipse.jface.text.reconciler.DirtyRegion;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.VersionedEdits;
import org.eclipse.lsp4e.internal.DocumentUtil;
import org.eclipse.lsp4e.ui.FormatterPreferencePage;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.UIJob;

public class LSPonTypeFormattingReconcilingStrategy implements IReconcilingStrategy, ITextViewerLifecycle {
	private final IPreferenceStore prefStore = LanguageServerPlugin.getDefault().getPreferenceStore();
	private boolean isOnTypeFormattingEnabled = prefStore.getBoolean(FormatterPreferencePage.PREF_ON_TYPE_FORMATTING_ENABLED);
	private final IPropertyChangeListener formattingPrefsListener = (final PropertyChangeEvent event) -> {
		final var newValue = event.getNewValue();
		if (newValue != null) {
			if (FormatterPreferencePage.PREF_ON_TYPE_FORMATTING_ENABLED.equals(event.getProperty())) {
				isOnTypeFormattingEnabled = Boolean.parseBoolean(newValue.toString());
			}
		}
	};
	@Nullable
	private ITextViewer textViewer;
	@Nullable
	private IDocument document;
	private boolean isUndoOrRedoInProgress = false;
	private final IOperationHistoryListener operationHistoryListener = new IOperationHistoryListener() {
		@Override
		public void historyNotification(OperationHistoryEvent event) {
			if (event.getEventType() == OperationHistoryEvent.UNDONE
					|| event.getEventType() == OperationHistoryEvent.REDONE) {
				isUndoOrRedoInProgress = true;
			}
		}
	};
	private final ITextListener textListener = new ITextListener() {

		private @Nullable DocumentEvent documentEvent;
		private volatile boolean isListening = true;
		private UIJob job = new UIJob("format on type") { //$NON-NLS-1$
			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				try {
 					if (textViewer != null) {
						final StyledText widget = textViewer.getTextWidget();
						if (widget == null || widget.isDisposed()) {
							return Status.CANCEL_STATUS;
						}
						if (!isUndoOrRedoInProgress) {
							isListening = false;
							doFormatOnType(textViewer, document, documentEvent);
						}
					}
				} finally {
					isListening = true;
				}
				return Status.OK_STATUS;
			}
		};

		@Override
		public void textChanged(@Nullable TextEvent event) {
			if (isOnTypeFormattingEnabled && isListening && event != null) {
				var docEvent = event.getDocumentEvent();
				if (!event.getViewerRedrawState() || docEvent == null || docEvent.getText().isEmpty()) {
					return;
				}
				// Reset the undo/redo flag after processing the document event
				isUndoOrRedoInProgress = false;
				job.cancel();
				documentEvent = docEvent;
				job.schedule(50);
			}
		}
	};

	@Override
	public void install(@Nullable ITextViewer textViewer) {
		if (textViewer == null) {
			return;
		}
		this.textViewer = textViewer;
		this.textViewer.addTextListener(textListener);
		// Register operation history listener
		IOperationHistory history = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
		history.addOperationHistoryListener(operationHistoryListener);
		prefStore.addPropertyChangeListener(formattingPrefsListener);
	}

	@Override
	public void uninstall() {
		IOperationHistory history = PlatformUI.getWorkbench().getOperationSupport().getOperationHistory();
		history.removeOperationHistoryListener(operationHistoryListener);
		prefStore.removePropertyChangeListener(formattingPrefsListener);
		if (this.textViewer != null) {
			this.textViewer.removeTextListener(textListener);
		}
		this.textViewer = null;
	}

	@Override
	public void setDocument(@Nullable IDocument document) {
		this.document = document;
	}

	@Override
	public void reconcile(@Nullable DirtyRegion dirtyRegion, @Nullable IRegion subRegion) {
	}

	@Override
	public void reconcile(@Nullable IRegion partition) {
	}

	void doFormatOnType(@Nullable ITextViewer viewer, @Nullable IDocument document, @Nullable DocumentEvent event) {
		if (document == null || event == null || event.fText.isEmpty() || viewer == null) {
			return;
		}

		// If the backing resource is read-only, prompt to make it writable
		if (LSPEclipseUtils.isReadOnly(document)) {
			IFile file = LSPEclipseUtils.getFile(document);
			if (file == null) {
				final var label = LSPEclipseUtils.toUri(document);
				MessageDialog.openInformation(UI.getActiveShell(), Messages.LSPFormatHandler_ReadOnlyEditor_title,
						NLS.bind(Messages.LSPFormatHandler_ReadOnlyEditor_inputReadonly, String.valueOf(label)));
				return;
			}

			if(!LSPFormatHandler.setWritable(file))
				return;
		}

		final String[] triggerCharArr = new String[] { "" }; //$NON-NLS-1$
		var executor = LanguageServers.forDocument(document)
			.withCapability(capabilities -> {
				var provider = capabilities.getDocumentOnTypeFormattingProvider();
				if (provider != null) {
					String firstTriggerChar = provider.getFirstTriggerCharacter();
					var spaceRemovalRegex = "[\\s&&[^" + Pattern.quote(firstTriggerChar) + "]]+"; //$NON-NLS-1$ //$NON-NLS-2$
					var filteredText = event.fText.replaceAll(spaceRemovalRegex, ""); //$NON-NLS-1$
					if (filteredText.equals(firstTriggerChar)) {
						triggerCharArr[0] = firstTriggerChar;
					} else if (provider.getMoreTriggerCharacter() != null) {
						for (String c : provider.getMoreTriggerCharacter()) {
							if (event.fText.equals(c)) {
								triggerCharArr[0] = c;
								break;
							}
						}
					}
				}
				return Either.forLeft(provider != null && !triggerCharArr[0].isEmpty());
				});
		if (!executor.anyMatching()) {
			return;
		}
		FormattingOptions formattingOptions = LSPFormatter.getFormatOptions();
		TextDocumentIdentifier textDocumentIdentifier = LSPEclipseUtils.toTextDocumentIdentifier(document);
		if (textDocumentIdentifier == null) {
			return;
		}
		try {
			long modificationStamp = DocumentUtil.getDocumentModificationStamp(document);
			int position = event.fOffset + event.fText.length();
			DocumentOnTypeFormattingParams params = new DocumentOnTypeFormattingParams(textDocumentIdentifier, formattingOptions, LSPEclipseUtils.toPosition(position, document), triggerCharArr[0]);
			var edits = executor.computeFirst(ls -> ls.getTextDocumentService().onTypeFormatting(params)).get(1, TimeUnit.SECONDS);
			if (!edits.isEmpty()) {
				int caretOffset = new VersionedEdits(modificationStamp, edits.get(), document).apply(position);
		        viewer.setSelectedRange(caretOffset, 0);
		        viewer.revealRange(caretOffset, 0);
			}
		} catch (BadLocationException | InterruptedException | ExecutionException | TimeoutException | ConcurrentModificationException e) {
			LanguageServerPlugin.logError(e);
		}
	}

}