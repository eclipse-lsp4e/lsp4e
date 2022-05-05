/*******************************************************************************
 * Copyright (c) 2016, 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Michał Niewrzał (Rogue Wave Software Inc.)
 *  Rubén Porras Campo (Avaloq Evolution AG) - documentAboutToBeSaved implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSaveReason;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageServer;

final class DocumentContentSynchronizer implements IDocumentListener {

	private final @NonNull LanguageServerWrapper languageServerWrapper;
	private final @NonNull IDocument document;
	private final @NonNull URI fileUri;
	private final TextDocumentSyncKind syncKind;

	private int version = 0;
	private DidChangeTextDocumentParams changeParams;
	private long modificationStamp;
	private CompletableFuture<LanguageServer> lastChangeFuture;
	private long documentModificationStamp;
	private LanguageServer languageServer;

	/**
	 * Synchronization guard to protect <code>lastChangeFuture</code> and <code>documentModificationStamp</code>
	 * from race conditions
	 */
	private final Object syncRoot = new Object();

	public DocumentContentSynchronizer(@NonNull LanguageServerWrapper languageServerWrapper,
			@NonNull IDocument document, TextDocumentSyncKind syncKind) {
		this.languageServerWrapper = languageServerWrapper;
		this.fileUri = LSPEclipseUtils.toUri(document);
		try {
			IFileStore store = EFS.getStore(fileUri);
			this.modificationStamp = store.fetchInfo().getLastModified();
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
			this.modificationStamp = new File(fileUri).lastModified();
		}
		this.syncKind = syncKind != null ? syncKind : TextDocumentSyncKind.Full;

		this.document = document;

		// If we have been constructed on a modified document, make sure the stamp is up
		// to date
		documentModificationStamp = document instanceof Document ? ((Document) document).getModificationStamp() : 0;

		// add a document buffer
		TextDocumentItem textDocument = new TextDocumentItem();
		textDocument.setUri(fileUri.toString());
		textDocument.setText(document.get());

		List<IContentType> contentTypes = LSPEclipseUtils.getDocumentContentTypes(this.document);

		String languageId = languageServerWrapper.getLanguageId(contentTypes.toArray(new IContentType[0]));

		IPath fromPortableString = Path.fromPortableString(this.fileUri.getPath());
		if (languageId == null) {
			languageId = fromPortableString.getFileExtension();
			if (languageId == null) {
				languageId = fromPortableString.lastSegment();
			}
		}

		textDocument.setLanguageId(languageId);
		textDocument.setVersion(++version);
		lastChangeFuture = languageServerWrapper.getInitializedServer().thenApplyAsync(ls -> {
			this.languageServer = ls;
			ls.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(textDocument));
			return ls;
		});
	}

	/**
	 * Submit an asynchronous call (i.e. to the language server) that will only be executed if the
	 * expected document version matches the stored one (which tracks change events).
	 *
	 * @param <U> Computation return type
	 * @param expectedDocumentStamp The current version of the document according to the calling code
	 * @param fn Asynchronous computation on the language server
	 * @return A future that will throw a <code>ConcurrentModificationException</code> on <code>get()</code>
	 * if the document has changed in the meantime.
	 */
	<U> @NonNull CompletableFuture<U> executeOnCurrentVersionAsync(long expectedDocumentStamp,
			Function<LanguageServer, ? extends CompletionStage<U>> fn) {
		synchronized (syncRoot) {
			if (expectedDocumentStamp == documentModificationStamp) {
				// We will be issuing this request on the correct version of the document, so
				// queue it.
				CompletableFuture<U> valueFuture = lastChangeFuture.thenComposeAsync(fn);
				// We ignore any exceptions that happen when executing the given future
				lastChangeFuture = valueFuture.handle((value, error) -> {
					return this.languageServer;
				});
				return valueFuture;
			} else {
				// If we were to issue this request now, it would happen on the wrong version of
				// the document
				CompletableFuture<U> future = new CompletableFuture<>();
				future.completeExceptionally(new ConcurrentModificationException());
				return future;
			}
		}
	}

	CompletableFuture<LanguageServer> lastChangeFuture() {
		return lastChangeFuture;
	}

	@Override
	public void documentChanged(DocumentEvent event) {
		checkEvent(event);
		if (syncKind == TextDocumentSyncKind.Full) {
			createChangeEvent(event);
		}
		final DidChangeTextDocumentParams changeParamsToSend = changeParams;
		synchronized (syncRoot) {
			if (changeParamsToSend != null) {
				changeParams = null;
				changeParamsToSend.getTextDocument().setVersion(++version);
				lastChangeFuture = lastChangeFuture.thenApplyAsync(ls -> {
					ls.getTextDocumentService().didChange(changeParamsToSend);
					return ls;
				});
			}
			documentModificationStamp = event.getModificationStamp();
		}
	}

	@Override
	public void documentAboutToBeChanged(DocumentEvent event) {
		checkEvent(event);
		if (syncKind == TextDocumentSyncKind.Incremental) {
			// this really needs to happen before event gets actually
			// applied, to properly compute positions
			createChangeEvent(event);
		}
	}

	/**
	 * Convert Eclipse {@link DocumentEvent} to LS according
	 * {@link TextDocumentSyncKind}. {@link TextDocumentContentChangeEventImpl}.
	 *
	 * @param event
	 *            Eclipse {@link DocumentEvent}
	 * @return true if change event is ready to be sent
	 */
	private boolean createChangeEvent(DocumentEvent event) {
		Assert.isTrue(changeParams == null);
		changeParams = new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(),
				Collections.singletonList(new TextDocumentContentChangeEvent()));
		changeParams.getTextDocument().setUri(fileUri.toString());

		IDocument document = event.getDocument();
		TextDocumentContentChangeEvent changeEvent = null;
		TextDocumentSyncKind syncKind = getTextDocumentSyncKind();
		switch (syncKind) {
		case None:
			return false;
		case Full:
			changeParams.getContentChanges().get(0).setText(event.getDocument().get());
			break;
		case Incremental:
			changeEvent = changeParams.getContentChanges().get(0);
			String newText = event.getText();
			int offset = event.getOffset();
			int length = event.getLength();
			try {
				// try to convert the Eclipse start/end offset to LS range.
				Range range = new Range(LSPEclipseUtils.toPosition(offset, document),
						LSPEclipseUtils.toPosition(offset + length, document));
				changeEvent.setRange(range);
				changeEvent.setText(newText);
				changeEvent.setRangeLength(length);
			} catch (BadLocationException e) {
				// error while conversion (should never occur)
				// set the full document text as changes.
				changeEvent.setText(document.get());
			}
			break;
		}
		return true;
	}

	private boolean serverSupportsWillSaveWaitUntil() {
		ServerCapabilities serverCapabilities = languageServerWrapper.getServerCapabilities();
		if(serverCapabilities != null ) {
			Either<TextDocumentSyncKind, TextDocumentSyncOptions> textDocumentSync = serverCapabilities.getTextDocumentSync();
			if(textDocumentSync.isRight()) {
				TextDocumentSyncOptions saveOptions = textDocumentSync.getRight();
				return saveOptions != null && saveOptions.getWillSaveWaitUntil();
			}
		}
		return false;
	}

	private static final int WILL_SAVE_WAIT_UNTIL_TIMEOUT_IN_SECONDS = 2;
	private static final int WILL_SAVE_WAIT_UNTIL_COUNT_THRESHOLD = 3;
	private static final Map<String, Integer> WILL_SAVE_WAIT_UNTIL_TIMEOUT_MAP = new ConcurrentHashMap<>();

	public void documentAboutToBeSaved() {
		if (!serverSupportsWillSaveWaitUntil()) {
			return;
		}

		String uri = fileUri.toString();
		if (WILL_SAVE_WAIT_UNTIL_TIMEOUT_MAP.getOrDefault(uri, 0) > WILL_SAVE_WAIT_UNTIL_COUNT_THRESHOLD) {
			return;
		}

		TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
		// Use @link{TextDocumentSaveReason.Manual} as the platform does not give enough information to be accurate
		WillSaveTextDocumentParams params = new WillSaveTextDocumentParams(identifier, TextDocumentSaveReason.Manual);
		List<TextEdit> edits = null;
		try {
			edits = languageServerWrapper.getInitializedServer()
				.thenComposeAsync(ls -> ls.getTextDocumentService().willSaveWaitUntil(params))
				.get(WILL_SAVE_WAIT_UNTIL_TIMEOUT_IN_SECONDS, TimeUnit.SECONDS);
		} catch (ExecutionException e) {
			LanguageServerPlugin.logError(e);
		} catch (TimeoutException e) {
			Integer timeoutCount = WILL_SAVE_WAIT_UNTIL_TIMEOUT_MAP.compute(uri,
					(k, v) -> v == null ? 1 : Integer.valueOf(v + 1));
			String message = "WillSaveWaitUntil timeouted out after " + Integer.valueOf(WILL_SAVE_WAIT_UNTIL_TIMEOUT_IN_SECONDS).toString() +" seconds for " + uri;  //$NON-NLS-1$//$NON-NLS-2$
			if (timeoutCount > WILL_SAVE_WAIT_UNTIL_COUNT_THRESHOLD) {
				message = message + ", it will no longer be called for this document"; //$NON-NLS-1$
			}
			LanguageServerPlugin.logWarning(message, e);
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError(e);
			Thread.currentThread().interrupt();
		}
		LSPEclipseUtils.applyEdits(document, edits);
	}

	public void documentSaved(long timestamp) {
		if (modificationStamp >= timestamp) {
			// Old event
			return;
		}
		this.modificationStamp = timestamp;
		ServerCapabilities serverCapabilities = languageServerWrapper.getServerCapabilities();
		if (serverCapabilities != null) {
			Either<TextDocumentSyncKind, TextDocumentSyncOptions> textDocumentSync = serverCapabilities
					.getTextDocumentSync();
			if (textDocumentSync.isRight() && textDocumentSync.getRight().getSave() == null) {
				return;
			}
		}
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(fileUri.toString());
		DidSaveTextDocumentParams params = new DidSaveTextDocumentParams(identifier, document.get());
		++version;
		synchronized (syncRoot) {
			lastChangeFuture = lastChangeFuture.thenApplyAsync(ls -> {
				ls.getTextDocumentService().didSave(params);
				return ls;
			});
		}
	}

	public void documentClosed() {
		String uri = fileUri.toString();
		WILL_SAVE_WAIT_UNTIL_TIMEOUT_MAP.remove(uri);
		// When LS is shut down all documents are being disconnected. No need to send
		// "didClose" message to the LS that is being shut down or not yet started
		if (languageServerWrapper.isActive()) {
			TextDocumentIdentifier identifier = new TextDocumentIdentifier(uri);
			DidCloseTextDocumentParams params = new DidCloseTextDocumentParams(identifier);
			languageServerWrapper.getInitializedServer()
					.thenAcceptAsync(ls -> ls.getTextDocumentService().didClose(params));
		}
	}

	/**
	 * Returns the text document sync kind capabilities of the server and
	 * {@link TextDocumentSyncKind#Full} otherwise.
	 *
	 * @return the text document sync kind capabilities of the server and
	 *         {@link TextDocumentSyncKind#Full} otherwise.
	 */
	private TextDocumentSyncKind getTextDocumentSyncKind() {
		return syncKind;
	}

	public IDocument getDocument() {
		return this.document;
	}

	int getVersion() {
		return version;
	}

	private void checkEvent(DocumentEvent event) {
		if (this.document != event.getDocument()) {
			throw new IllegalStateException("Synchronizer should apply to only a single document, which is the one it was instantiated for"); //$NON-NLS-1$
		}
	}
}
