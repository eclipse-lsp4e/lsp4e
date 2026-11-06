/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.core.filebuffers.IDocumentSetupParticipantExtension;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

/**
 * Starts and connects all language servers dealing with a document's content type once the document is shown in an
 * editor, so that servers which only contribute diagnostics also receive {@code didOpen}. Documents loaded without an
 * editor (by builders, search, compare or other plug-ins) are only connected to servers declaring
 * {@code connectFileBuffers="true"}.
 */
public class ConnectDocumentToLanguageServerSetupParticipant implements IDocumentSetupParticipant, IDocumentSetupParticipantExtension {

	/**
	 * Used to delay the actual document setup.
	 */
	private static ScheduledExecutorService DELAYED_EXECUTOR = createExecutor();

	private static final Set<CompletableFuture<?>> PENDING_CONNECTIONS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private static final AtomicBoolean EDITOR_TRACKER_INSTALLED = new AtomicBoolean();

	private static ScheduledExecutorService createExecutor() {
		return Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("LS-Document-Delayed-Setup").factory()); //$NON-NLS-1$
	}

	@Override
	public void setup(IDocument document) {
		ITextFileBuffer buffer = ITextFileBufferManager.DEFAULT.getTextFileBuffer(document);
		if (buffer == null || buffer.getLocation() == null) {
			return;
		}
		setup(document, buffer.getLocation(), LocationKind.IFILE);
	}

	@Override
	public void setup(final IDocument document, IPath location, LocationKind locationKind) {
		installEditorTracker();
		// Delay to ensure the document is initialized and can be resolved by LSPEclipseUtils.toUri
		schedule(document, () -> {
			final List<CompletableFuture<?>> connections = new ArrayList<>();
			for (LanguageServerWrapper wrapper : LanguageServiceAccessor.getLSWrappers(document,
					definition -> definition.connectFileBuffers)) {
				final CompletableFuture<?> connection = wrapper.connectDocument(document);
				if (connection != null) {
					connections.add(connection);
				}
			}
			if (!connections.isEmpty()) {
				PENDING_CONNECTIONS.add(CompletableFuture.allOf(connections.toArray(CompletableFuture[]::new)));
			}
		});
	}

	// same delay as for file buffers, so that editors opened and closed right away do not start servers
	private static void connectToAllServers(IDocument document) {
		schedule(document, () -> PENDING_CONNECTIONS.add(
				LanguageServers.forDocument(document).collectAll(ls -> CompletableFuture.completedFuture(null))));
	}

	private static void schedule(IDocument document, Runnable task) {
		try {
			DELAYED_EXECUTOR.schedule(() -> {
				// the buffer may have been disposed during the delay, connecting would then leak a running server
				if (ITextFileBufferManager.DEFAULT.getTextFileBuffer(document) != null) {
					task.run();
				}
			}, 1, TimeUnit.SECONDS);
		} catch (RejectedExecutionException e) {
			// executor is being replaced by waitForAll()
		}
	}

	private static void installEditorTracker() {
		if (EDITOR_TRACKER_INSTALLED.get() || !PlatformUI.isWorkbenchRunning()) {
			return;
		}
		final IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.getDisplay().asyncExec(() -> {
			if (workbench.isClosing() || !EDITOR_TRACKER_INSTALLED.compareAndSet(false, true)) {
				return;
			}
			workbench.addWindowListener(EditorTracker.INSTANCE);
			for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
				EditorTracker.INSTANCE.windowOpened(window);
			}
		});
	}

	/**
	 * Connects the documents of text editors to their language servers when the editors are opened or get a new
	 * input.
	 */
	private static final class EditorTracker implements IWindowListener, IPartListener2 {

		static final EditorTracker INSTANCE = new EditorTracker();

		@Override
		public void windowOpened(IWorkbenchWindow window) {
			window.getPartService().addPartListener(this);
			for (IWorkbenchPage page : window.getPages()) {
				for (IEditorReference reference : page.getEditorReferences()) {
					connect(reference);
				}
			}
		}

		@Override
		public void windowClosed(IWorkbenchWindow window) {
			window.getPartService().removePartListener(this);
		}

		@Override
		public void windowActivated(IWorkbenchWindow window) {
		}

		@Override
		public void windowDeactivated(IWorkbenchWindow window) {
		}

		@Override
		public void partOpened(IWorkbenchPartReference partRef) {
			connect(partRef);
		}

		// lazily restored editors are only instantiated once they become visible
		@Override
		public void partVisible(IWorkbenchPartReference partRef) {
			connect(partRef);
		}

		@Override
		public void partInputChanged(IWorkbenchPartReference partRef) {
			connect(partRef);
		}

		// a closed and reopened file gets a new document instance, so identity is enough to skip repeated events
		private final Set<IDocument> connectedDocuments = Collections.newSetFromMap(new WeakHashMap<>());

		private void connect(IWorkbenchPartReference partRef) {
			if (!(partRef instanceof IEditorReference)) {
				return;
			}
			final IDocument document = getDocument(partRef.getPart(false));
			if (document != null && connectedDocuments.add(document)) {
				connectToAllServers(document);
			}
		}

		private static @Nullable IDocument getDocument(@Nullable IWorkbenchPart part) {
			if (part == null) {
				return null;
			}
			final ITextEditor editor = part instanceof ITextEditor textEditor ? textEditor
					: part.getAdapter(ITextEditor.class);
			if (editor == null) {
				return null;
			}
			final IEditorInput input = editor.getEditorInput();
			final IDocumentProvider provider = editor.getDocumentProvider();
			return input == null || provider == null ? null : provider.getDocument(input);
		}
	}

	/**
	 * Testing hook to ensure teardown doesn't remove documents while the LSP has in-flight async
	 * jobs trying to attach to them
	 */
	public static void waitForAll() {
		// Don't accept any more document setups
		DELAYED_EXECUTOR.shutdownNow();
		try {
			DELAYED_EXECUTOR.awaitTermination(1, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			LanguageServerPlugin.logError("Failed to await termination of delayed document setup", e); //$NON-NLS-1$
		}

		// Now we can wait for the pending connection to finish.
		PENDING_CONNECTIONS.forEach(future -> {
			try {
				future.get(1, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				LanguageServerPlugin.logError("Interrupted trying to cancel document setup", e); //$NON-NLS-1$ ;
			}
		});

		// Create new executor for next setup.
		DELAYED_EXECUTOR = createExecutor();
	}

}
