/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistoryListener;
import org.eclipse.core.commands.operations.OperationHistoryEvent;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IBlockTextSelection;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IMultiTextSelection;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextInputListener;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.ITextViewerExtension5;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.contentassist.ContentAssistEvent;
import org.eclipse.jface.text.contentassist.ICompletionListener;
import org.eclipse.jface.text.contentassist.ICompletionListenerExtension2;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.source.ContentAssistantFacade;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.ISourceViewerExtension4;
import org.eclipse.jface.text.source.ISourceViewerExtension5;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.CancellationSupport;
import org.eclipse.lsp4e.internal.CancellationUtil;
import org.eclipse.lsp4e.internal.GhostTextRegistry;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.operations.inlinecompletion.CurrentCompletionInfo;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextLayout.Kind;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextLayout.MiningSpec;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextMinings.GhostMining;
import org.eclipse.lsp4e.ui.InlineCompletionPreferencePage;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.InlineCompletionContext;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionList;
import org.eclipse.lsp4j.InlineCompletionParams;
import org.eclipse.lsp4j.InlineCompletionTriggerKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.InsertTextMode;
import org.eclipse.lsp4j.SelectedCompletionInfo;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.VerifyKeyListener;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorExtension2;

/**
 * Requests, shows, accepts and dismisses inline completions in one text viewer.
 * <p>
 * Completions are requested automatically, a moment after the user edits at the caret, or explicitly.
 * The candidates of the latest request form a {@link InlineCompletionSession}; the one shown is laid
 * out by {@link GhostTextLayout} and drawn by code minings this controller hands to its
 * {@link InlineCompletionProvider}. As long as the user types through the ghost text (or deletes back
 * within the replaced range), the session is kept and the ghost text updated without a new request.
 * Any other edit, moving the caret or losing focus dismisses it. Tab accepts, Escape dismisses, and
 * commands cycle through the candidates.
 * <p>
 * While the completion popup is open, completions are requested with the selected proposal as
 * {@code selectedCompletionInfo}, and only candidates extending it are shown.
 */
public final class InlineCompletionController {

	/** The context active while ghost text is shown, which enables the cycling key bindings. */
	private static final String VISIBLE_CONTEXT_ID = "org.eclipse.lsp4e.inlineCompletion.visible"; //$NON-NLS-1$

	/** The controllers of the open viewers; used on the UI thread only. */
	private static final Map<ISourceViewer, InlineCompletionController> CONTROLLERS = new HashMap<>();

	/** What is shown: the ghost text, the caret it was computed for and its layout. */
	private record Snapshot(GhostText ghost, int caret, List<MiningSpec> specs) {
	}

	/** The proposal selected in the completion popup: the range it replaces and its text. */
	private record SelectedCompletion(int start, int end, String text) {
	}

	private record ServerResponse(LanguageServerWrapper wrapper,
			@Nullable Either<List<InlineCompletionItem>, InlineCompletionList> response, @Nullable Throwable error) {
	}

	/**
	 * The request in flight. Inline completions are requested one at a time: while the user keeps
	 * editing at the caret, the request is kept, and its reply is shown if it still fits what was typed
	 * meanwhile. For that it records the document as it was when sent and the edits made since.
	 */
	private static final class PendingRequest {
		final long id;
		final String text;
		final int caret;
		final InlineCompletionTriggerKind triggerKind;
		final @Nullable SelectedCompletion selected;
		final CancellationSupport cancellation = new CancellationSupport();
		final long sentAt = System.nanoTime();
		/** Edits made at the caret since the request was sent: offset, deleted length, inserted length. */
		final List<int[]> edits = new ArrayList<>();
		int remainingResponses;
		boolean cancelled;
		private @Nullable Document sentDocument;

		PendingRequest(long id, String text, int caret, InlineCompletionTriggerKind triggerKind,
				@Nullable SelectedCompletion selected) {
			this.id = id;
			this.text = text;
			this.caret = caret;
			this.triggerKind = triggerKind;
			this.selected = selected;
		}

		/** Returns the document as it was when the request was sent, which replies refer to. */
		Document sentDocument() {
			Document document = sentDocument;
			if (document == null) {
				document = new Document(text);
				sentDocument = document;
			}
			return document;
		}
	}

	/** After this long without a reply, a request no longer holds back new requests (a hung server). */
	private long staleRequestNanos = TimeUnit.SECONDS.toNanos(10);

	private final ISourceViewer viewer;
	private final StyledText textWidget;
	private final Display display;
	private final @Nullable ITextEditor editor;

	private @Nullable IDocument document;
	private @Nullable InlineCompletionSession session;
	private long sessionRequestId = -1;
	private @Nullable Snapshot snapshot;
	private List<GhostMining> shownMinings = List.of();
	private List<GhostMining> replacedMinings = List.of();
	private final List<Position> trackedPositions = new ArrayList<>();

	private long requestId;
	private @Nullable PendingRequest pending;
	/** Servers whose last request failed, so that a failing server is logged once rather than per request. */
	private final Set<LanguageServerWrapper> failingServers = new HashSet<>();
	private boolean requestedAlternatives;

	private int caretBeforeChange;
	private boolean changeInProgress;
	private boolean applying;
	private boolean undoRedoInProgress;
	private boolean painted;
	private boolean refreshScheduled;
	private boolean disposed;
	/** Whether the editor lost the focus, after which only explicit requests are sent. */
	private boolean focusLost;
	private boolean noSuggestionsShown;

	private boolean contentAssistActive;
	private boolean proposalApplied;
	private @Nullable SelectedCompletion selectedCompletion;
	private @Nullable SelectedCompletion appliedSelection;
	private List<InlineCompletionCandidate> selectionCandidates = List.of();

	private int originalBottomMargin = -1;
	private @Nullable IContextActivation contextActivation;

	private final Runnable automaticTrigger = () -> request(InlineCompletionTriggerKind.Automatic, false);

	private final IDocumentListener documentListener = new IDocumentListener() {
		@Override
		public void documentAboutToBeChanged(DocumentEvent event) {
			if (Display.getCurrent() == display) {
				caretBeforeChange = caretOffset();
				changeInProgress = true;
			}
		}

		@Override
		public void documentChanged(DocumentEvent event) {
			if (Display.getCurrent() == display) {
				onDocumentChanged(event);
			} else if (!display.isDisposed()) {
				// not the user typing, and what is shown belongs to the UI thread
				display.asyncExec(() -> {
					if (!disposed) {
						dismiss();
					}
				});
			}
		}
	};

	private final ITextInputListener inputListener = new ITextInputListener() {
		@Override
		public void inputDocumentAboutToBeChanged(@Nullable IDocument oldInput, @Nullable IDocument newInput) {
			setDocument(null);
		}

		@Override
		public void inputDocumentChanged(@Nullable IDocument oldInput, @Nullable IDocument newInput) {
			setDocument(newInput);
		}
	};

	private final CaretListener caretListener = event -> onCaretMoved();

	private final FocusListener focusListener = new FocusAdapter() {
		@Override
		public void focusGained(FocusEvent e) {
			focusLost = false;
		}

		@Override
		public void focusLost(FocusEvent e) {
			if (!contentAssistActive) {
				focusLost = true;
				dismiss();
			}
		}
	};

	private final VerifyKeyListener verifyKeyListener = this::onVerifyKey;

	/** What the layout of the shown ghost text depends on, other than the document. */
	private record Geometry(int tabs) {
	}
	/** The geometry the shown ghost text was laid out for. */
	private @Nullable Geometry laidOutFor;

	/** Lays the shown ghost text out again when the editor's tab width changed. */
	private final PaintListener geometryListener = event -> {
		if (snapshot != null && !geometry().equals(laidOutFor)) {
			event.display.asyncExec(() -> {
				final Snapshot shown = snapshot;
				if (!disposed && shown != null && !geometry().equals(laidOutFor)) {
					display(shown.ghost(), shown.caret());
				}
			});
		}
	};
	private final IOperationHistoryListener historyListener = event -> {
		switch (event.getEventType()) {
		case OperationHistoryEvent.ABOUT_TO_UNDO, OperationHistoryEvent.ABOUT_TO_REDO -> undoRedoInProgress = true;
		case OperationHistoryEvent.UNDONE, OperationHistoryEvent.REDONE, OperationHistoryEvent.OPERATION_NOT_OK -> undoRedoInProgress = false;
		default -> {
			// not relevant
		}
		}
	};

	private final ICompletionListener completionListener = new CompletionListener();

	private final Listener disposeListener = event -> uninstall();

	InlineCompletionController(ISourceViewer viewer, @Nullable ITextEditor editor) {
		this.viewer = viewer;
		this.textWidget = viewer.getTextWidget();
		this.display = textWidget.getDisplay();
		this.editor = editor;
	}

	/**
	 * Returns the suggestion currently shown.
	 *
	 * @return the suggestion, or {@link CurrentCompletionInfo#NONE} if none is shown
	 */
	public static CurrentCompletionInfo getCurrentCompletionInfo() {
		// TODO return the suggestion shown
		return CurrentCompletionInfo.NONE;
	}

	/** Returns the controller of the given viewer, or {@code null} if it has none. */
	public static @Nullable InlineCompletionController get(@Nullable ISourceViewer viewer) {
		return viewer == null ? null : CONTROLLERS.get(viewer);
	}

	void install() {
		CONTROLLERS.put(viewer, this);
		viewer.addTextInputListener(inputListener);
		setDocument(viewer.getDocument());
		textWidget.addCaretListener(caretListener);
		textWidget.addPaintListener(geometryListener);
		textWidget.addFocusListener(focusListener);
		textWidget.addListener(SWT.Dispose, disposeListener);
		if (viewer instanceof ITextViewerExtension extension) {
			extension.prependVerifyKeyListener(verifyKeyListener);
		}
		final ContentAssistantFacade facade = contentAssistantFacade();
		if (facade != null) {
			facade.addCompletionListener(completionListener);
		}
		final IOperationHistory history = operationHistory();
		if (history != null) {
			history.addOperationHistoryListener(historyListener);
		}
	}

	void uninstall() {
		if (disposed) {
			return;
		}
		disposed = true;
		CONTROLLERS.remove(viewer);
		cancelPending();
		session = null;
		final Snapshot shown = snapshot;
		snapshot = null;
		if (shown != null || noSuggestionsShown) {
			setStatusMessage(null);
		}
		final IDocument current = document;
		if (current != null) {
			GhostTextRegistry.clear(current);
		}
		setDocument(null);
		viewer.removeTextInputListener(inputListener);
		if (!textWidget.isDisposed()) {
			restoreBottomMargin();
			textWidget.removeCaretListener(caretListener);
			textWidget.removePaintListener(geometryListener);
			textWidget.removeFocusListener(focusListener);
			textWidget.removeListener(SWT.Dispose, disposeListener);
		}
		if (viewer instanceof ITextViewerExtension extension) {
			extension.removeVerifyKeyListener(verifyKeyListener);
		}
		final ContentAssistantFacade facade = contentAssistantFacade();
		if (facade != null) {
			facade.removeCompletionListener(completionListener);
		}
		final IOperationHistory history = operationHistory();
		if (history != null) {
			history.removeOperationHistoryListener(historyListener);
		}
		setVisibleContext(false);
	}

	private void setDocument(@Nullable IDocument newDocument) {
		final IDocument old = document;
		if (old == newDocument) {
			return;
		}
		if (old != null) {
			cancelPending();
			old.removeDocumentListener(documentListener);
			removeTrackedPositions(old);
			GhostTextRegistry.clear(old);
			endSession();
		}
		document = newDocument;
		if (newDocument != null) {
			newDocument.addDocumentListener(documentListener);
		}
	}

	// ---- public operations, used by the commands -----------------------------------------------------

	/** Requests inline completions explicitly ({@link InlineCompletionTriggerKind#Invoked}). */
	public void trigger() {
		request(InlineCompletionTriggerKind.Invoked, false);
	}

	/** Shows the next ({@code forward}) or previous candidate. */
	public void cycle(boolean forward) {
		final InlineCompletionSession current = session;
		final IDocument doc = document;
		if (current == null || doc == null) {
			return;
		}
		final int caret = caretOffset();
		if (!requestedAlternatives && current.getTriggerKind() == InlineCompletionTriggerKind.Automatic
				&& current.position(doc, caret)[1] <= 1) {
			// an automatic request may have returned a single item: ask for alternatives to cycle through
			requestedAlternatives = true;
			request(InlineCompletionTriggerKind.Invoked, true);
		}
		final GhostText ghost = current.cycle(doc, caret, forward);
		if (ghost != null) {
			display(ghost, caret);
		}
	}

	/** Dismisses the ghost text, if any, and gives up on the inline completions requested or about to be. */
	public void dismiss() {
		cancelPending();
		endSession();
	}

	private void endSession() {
		session = null;
		sessionRequestId = -1;
		requestedAlternatives = false;
		hide();
	}

	/** Accepts the ghost text shown, if any. */
	public void accept() {
		final Snapshot current = upToDateSnapshot();
		final IDocument doc = document;
		if (current == null || doc == null) {
			return;
		}
		final GhostText ghost = current.ghost();
		final InlineCompletionCandidate candidate = ghost.candidate();
		dismiss();
		if (editor instanceof ITextEditorExtension2 extension && !extension.validateEditorInputState()) {
			return;
		}
		if (ghost.replaceStart() < 0 || ghost.replaceStart() > ghost.replaceEnd() || ghost.replaceEnd() > doc.getLength()) {
			return;
		}
		final IRewriteTarget rewriteTarget = editor == null ? null : editor.getAdapter(IRewriteTarget.class);
		applying = true;
		if (rewriteTarget != null) {
			rewriteTarget.beginCompoundChange();
		}
		try {
			final LanguageServerWrapper wrapper = castNonNull(candidate.source());
			final String snippet = candidate.snippet();
			final var item = new CompletionItem(""); //$NON-NLS-1$
			item.setTextEdit(Either.forLeft(new TextEdit(LSPEclipseUtils.toRange(ghost.replaceStart(), ghost.replaceEnd(), doc),
					snippet != null ? snippet : candidate.text())));
			item.setInsertTextFormat(snippet != null ? InsertTextFormat.Snippet : InsertTextFormat.PlainText);
			item.setInsertTextMode(InsertTextMode.AsIs);
			if (candidate.command() != null && wrapper.isActive()) {
				item.setCommand(candidate.command());
			}
			final int caret = caretOffset();
			final var proposal = new LSCompletionProposal(doc, caret, item, wrapper);
			proposal.apply(viewer, '\0', 0, caret);
			final Point selection = proposal.getSelection(doc);
			if (selection != null && !LinkedModeModel.hasInstalledModel(doc)) {
				viewer.setSelectedRange(selection.x, selection.y);
				viewer.revealRange(selection.x, selection.y);
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
		} finally {
			if (rewriteTarget != null) {
				rewriteTarget.endCompoundChange();
			}
			applying = false;
		}
	}

	// ---- requests ------------------------------------------------------------------------------------

	private void request(InlineCompletionTriggerKind triggerKind, boolean appendToSession) {
		display.timerExec(-1, automaticTrigger);
		final IDocument doc = document;
		if (disposed || doc == null || !canRequest(doc)) {
			return;
		}
		final URI uri = LSPEclipseUtils.toUri(doc);
		if (uri == null) {
			return;
		}
		final PendingRequest inFlight = pending;
		if (inFlight != null) {
			final boolean stale = System.nanoTime() - inFlight.sentAt > staleRequestNanos;
			if (triggerKind == InlineCompletionTriggerKind.Automatic && !appendToSession && !stale) {
				// one request at a time: its reply is shown if it still fits, otherwise a new request follows
				return;
			}
			cancelPending();
		}
		// with the popup open but no LSP proposal selected (editors open it while typing), request as usual
		final SelectedCompletion selected = contentAssistActive ? selectedCompletion : null;
		final int caret = caretOffset();
		final InlineCompletionParams params;
		try {
			final SelectedCompletionInfo selectedInfo = selected == null ? null
					: new SelectedCompletionInfo(LSPEclipseUtils.toRange(selected.start(), selected.end(), doc), selected.text());
			params = new InlineCompletionParams(LSPEclipseUtils.toTextDocumentIdentifier(uri),
					LSPEclipseUtils.toPosition(caret, doc), new InlineCompletionContext(triggerKind, selectedInfo));
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			return;
		}
		final long id = appendToSession ? sessionRequestId : ++requestId;
		final var request = new PendingRequest(id, doc.get(), caret, triggerKind, selected);
		final List<CompletableFuture<@Nullable ServerResponse>> responses = LanguageServers.forDocument(doc)
				.withCapability(ServerCapabilities::getInlineCompletionProvider)
				.computeAll((wrapper, server) -> request.cancellation
						.execute(server.getTextDocumentService().inlineCompletion(params))
						.handle((response, error) -> new ServerResponse(wrapper, response, error)));
		if (responses.isEmpty()) {
			return;
		}
		pending = request;
		request.remainingResponses = responses.size();
		for (final CompletableFuture<@Nullable ServerResponse> response : responses) {
			response.whenComplete((result, error) -> display.asyncExec(() -> onResponse(request, result, error)));
		}
	}

	/** Cancels the request in flight and the automatic request about to be sent, if any. */
	private void cancelPending() {
		display.timerExec(-1, automaticTrigger);
		final PendingRequest inFlight = pending;
		pending = null;
		if (inFlight != null) {
			inFlight.cancelled = true;
			inFlight.cancellation.cancel();
		}
	}

	private void onResponse(PendingRequest request, @Nullable ServerResponse result, @Nullable Throwable error) {
		request.remainingResponses--;
		final boolean last = request.remainingResponses == 0;
		if (last && pending == request) {
			pending = null;
		}
		final IDocument doc = document;
		if (disposed || doc == null || request.cancelled) {
			return;
		}
		if (error != null) {
			logFailure(null, error);
		} else if (result != null && result.error() != null) {
			logFailure(result.wrapper(), castNonNull(result.error()));
		} else if (result != null) {
			failingServers.remove(result.wrapper());
			final Document sent = request.sentDocument();
			final List<InlineCompletionCandidate> candidates = new ArrayList<>();
			for (final InlineCompletionCandidate candidate : CandidateNormalizer.normalize(result.response(), sent,
					request.caret, result.wrapper(), snippet -> expandSnippet(result.wrapper(), doc, snippet, caretOffset()))) {
				final SelectedCompletion selected = request.selected;
				if (selected != null && !GhostTextComputer.extendsSelectedCompletion(candidate, selected.start(),
						selected.end(), selected.text())) {
					continue;
				}
				// carry it over the edits made at the caret since the request was sent
				final InlineCompletionCandidate current = replay(candidate, request.edits);
				if (current != null) {
					candidates.add(current);
				}
			}
			if (!candidates.isEmpty()) {
				InlineCompletionSession current = session;
				if (current == null || sessionRequestId != request.id) {
					current = new InlineCompletionSession(request.triggerKind, request.selected != null);
					session = current;
					sessionRequestId = request.id;
					requestedAlternatives = false;
					selectionCandidates = List.of();
				}
				current.addCandidates(candidates);
				if (request.selected != null) {
					final var all = new ArrayList<>(selectionCandidates);
					all.addAll(candidates);
					selectionCandidates = List.copyOf(all);
				}
				show();
			}
		}
		if (!last) {
			return;
		}
		if (snapshot == null && !request.edits.isEmpty()) {
			// the user moved on while waiting and nothing fits any more: ask again for where they are now
			final boolean explicit = request.triggerKind == InlineCompletionTriggerKind.Invoked;
			if (explicit || LanguageServerPlugin.getDefault().getPreferenceStore()
					.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC)) {
				display.asyncExec(() -> {
					if (!disposed && pending == null && snapshot == null) {
						request(request.triggerKind, false);
					}
				});
			}
		} else if (request.triggerKind == InlineCompletionTriggerKind.Invoked && session == null) {
			setStatusMessage(Messages.InlineCompletion_noSuggestions);
			noSuggestionsShown = true;
		}
	}

	/**
	 * Returns the candidate with its range carried over the given edits, or {@code null} if an edit
	 * removed its start (the same rule as {@link InlineCompletionSession#documentChanged}).
	 */
	private static @Nullable InlineCompletionCandidate replay(InlineCompletionCandidate candidate, List<int[]> edits) {
		TrackedRange range = new TrackedRange(candidate.start(), candidate.end());
		for (final int[] edit : edits) {
			if (edit[0] < range.start()) {
				return null;
			}
			range = range.apply(edit[0], edit[1], edit[2]);
			if (range == null) {
				return null;
			}
		}
		return new InlineCompletionCandidate(candidate.source(), range.start(), range.end(), candidate.text(),
				candidate.snippet(), candidate.filterText(), candidate.command());
	}

	/** Logs a failed request, once per server until it succeeds again. */
	private void logFailure(@Nullable LanguageServerWrapper wrapper, Throwable error) {
		Throwable cause = error;
		while (cause instanceof CompletionException && cause.getCause() != null) {
			cause = castNonNull(cause.getCause());
		}
		if (cause instanceof CancellationException || CancellationUtil.isRequestCancelledException(cause)
				|| isContentModified(cause)) {
			return;
		}
		if (wrapper == null || failingServers.add(wrapper)) {
			LanguageServerPlugin.logError(cause);
		}
	}

	/** Returns whether the server gave up because the document changed, which typing makes usual. */
	private static boolean isContentModified(Throwable error) {
		return error instanceof ResponseErrorException responseError && responseError.getResponseError() != null
				&& responseError.getResponseError().getCode() == ResponseErrorCode.ContentModified.getValue();
	}

	private String expandSnippet(LanguageServerWrapper wrapper, IDocument doc, String snippet, int offset) {
		return new LSCompletionProposal(doc, offset, new CompletionItem(""), wrapper).expandSnippet(viewer, snippet, offset); //$NON-NLS-1$
	}

	private boolean canRequest(IDocument doc) {
		if (!viewer.isEditable() || textWidget.isDisposed() || textWidget.getSelectionCount() > 0
				|| textWidget.getBlockSelection() || LinkedModeModel.hasInstalledModel(doc) || isComposing()) {
			return false;
		}
		final ISelection selection = viewer.getSelectionProvider().getSelection();
		return !(selection instanceof IBlockTextSelection)
				&& !(selection instanceof IMultiTextSelection multi && multi.getRegions().length > 1);
	}

	private void scheduleAutomaticRequest() {
		final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		if (!disposed && !focusLost && store.getBoolean(InlineCompletionPreferencePage.PREF_AUTOMATIC)) {
			display.timerExec(Math.max(0, store.getInt(InlineCompletionPreferencePage.PREF_DELAY)), automaticTrigger);
		}
	}

	// ---- edits, caret, keys ----------------------------------------------------------------------------

	private void onCaretMoved() {
		clearNoSuggestions();
		if (changeInProgress || applying) {
			// moved by an edit, which is handled as such
			return;
		}
		// a request for where the caret was is of no use any more
		cancelPending();
		// Check once pending edits are processed: a caret move caused by typing through the ghost text
		// has then updated where it is shown, while any other move leaves it behind.
		display.asyncExec(() -> {
			if (disposed || applying || changeInProgress) {
				return;
			}
			final Snapshot current = snapshot;
			if (current != null && (caretOffset() != current.caret() || textWidget.getSelectionCount() > 0)) {
				endSession();
			}
		});
	}

	private void onVerifyKey(VerifyEvent event) {
		if (!event.doit) {
			return;
		}
		if (event.character == SWT.ESC && (event.stateMask & SWT.MODIFIER_MASK) == 0) {
			// dismiss, but let Escape do whatever else it does
			dismiss();
			return;
		}
		final Snapshot current = upToDateSnapshot();
		if (current != null && KeyPolicy.tabAccepts(event.character, event.stateMask, painted, linePrefix(current.caret()),
				current.ghost().text(), textWidget.getTabs())) {
			event.doit = false;
			accept();
		}
	}

	/**
	 * Returns the ghost text shown for the current document and caret. Edits and caret moves update it
	 * once pending UI events are processed, so a key typed right after them, before that, would act on
	 * the ghost text shown before them.
	 */
	private @Nullable Snapshot upToDateSnapshot() {
		final Snapshot current = snapshot;
		if (current == null || (!changeInProgress && current.caret() == caretOffset())) {
			return current;
		}
		return show() ? snapshot : null;
	}

	private void onDocumentChanged(DocumentEvent event) {
		if (applying) {
			// our own edit: nothing to update, but caret moves must be noticed again afterwards
			changeInProgress = false;
			return;
		}
		clearNoSuggestions();
		final String text = event.getText() == null ? "" : event.getText(); //$NON-NLS-1$
		final EditKind kind = EditKind.classify(caretBeforeChange, event.getOffset(), event.getLength(), text);
		final InlineCompletionSession current = session;
		if (current != null) {
			if (current.isSelectionBound()) {
				// keep the candidates in case this change is the application of the selected proposal
				appliedSelection = selectedCompletion;
			}
			if (kind == EditKind.OTHER) {
				endSession();
			} else {
				current.documentChanged(event.getOffset(), event.getLength(), text.length());
			}
		}
		display.timerExec(-1, automaticTrigger);
		final PendingRequest inFlight = pending;
		if (inFlight != null) {
			if (kind == EditKind.OTHER) {
				cancelPending();
			} else {
				inFlight.edits.add(new int[] { event.getOffset(), event.getLength(), text.length() });
			}
		}
		final boolean userEdit = kind.triggersAutomatically() && !undoRedoInProgress;
		display.asyncExec(() -> afterDocumentChanged(userEdit));
	}

	private void afterDocumentChanged(boolean userEdit) {
		changeInProgress = false;
		if (disposed) {
			return;
		}
		if (session != null && show()) {
			return;
		}
		endSession();
		if (userEdit) {
			scheduleAutomaticRequest();
		}
	}

	// ---- showing -------------------------------------------------------------------------------------

	private boolean show() {
		final InlineCompletionSession current = session;
		final IDocument doc = document;
		if (current == null || doc == null) {
			hide();
			return false;
		}
		final int caret = caretOffset();
		final GhostText ghost = current.current(doc, caret);
		if (ghost == null) {
			hide();
			return false;
		}
		return display(ghost, caret);
	}

	private boolean display(GhostText ghost, int caret) {
		final IDocument doc = document;
		if (doc == null) {
			return false;
		}
		final List<MiningSpec> specs;
		final Geometry geometry = geometry();
		try {
			specs = GhostTextLayout.layout(doc, ghost, textWidget.getTabs(), this::isLineVisible, contentAssistActive);
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			hide();
			return false;
		}
		final Snapshot old = snapshot;
		snapshot = new Snapshot(ghost, caret, specs);
		laidOutFor = geometry;
		registerOccupied(doc, specs);
		updateBottomMargin(specs);
		setVisibleContext(true);
		updateStatus(doc, caret);
		if (old != null && hasSameStructure(shownMinings, specs)) {
			for (int i = 0; i < specs.size(); i++) {
				shownMinings.get(i).update(specs.get(i));
			}
			// the minings these replace are drawn until the code mining manager has all providers' minings
			if (hasSameStructure(replacedMinings, specs)) {
				for (int i = 0; i < specs.size(); i++) {
					replacedMinings.get(i).update(specs.get(i));
				}
			}
			textWidget.redraw();
		} else {
			painted = false;
			refreshCodeMinings();
		}
		return true;
	}

	private Geometry geometry() {
		return new Geometry(textWidget.getTabs());
	}

	private void hide() {
		final IDocument doc = document;
		final Snapshot shown = snapshot;
		if (shown == null) {
			return;
		}
		snapshot = null;
		if (doc != null) {
			GhostTextRegistry.clear(doc);
		}
		restoreBottomMargin();
		setVisibleContext(false);
		setStatusMessage(null);
		refreshCodeMinings();
	}

	/**
	 * Returns whether the shown minings can draw the given layout: same kinds at the same (tracked)
	 * positions, so that updating their labels is enough.
	 */
	private static boolean hasSameStructure(List<GhostMining> minings, List<MiningSpec> specs) {
		if (minings.size() != specs.size()) {
			return false;
		}
		for (int i = 0; i < specs.size(); i++) {
			final MiningSpec spec = specs.get(i);
			final GhostMining mining = minings.get(i);
			final Position position = mining.getPosition();
			if (position.isDeleted() || position.getOffset() != spec.offset() || position.getLength() != spec.length()
					|| kindOf(mining) != spec.kind()) {
				return false;
			}
		}
		return true;
	}

	private static Kind kindOf(GhostMining mining) {
		if (mining instanceof GhostTextMinings.Header) {
			return Kind.HEADER;
		}
		return mining instanceof GhostTextMinings.EndOfDocument ? Kind.END_OF_DOCUMENT : Kind.CONTENT;
	}

	/**
	 * Creates the minings drawing the shown ghost text; called by the provider on every refresh, on the
	 * UI thread.
	 */
	List<GhostMining> createMinings(InlineCompletionProvider provider) {
		final IDocument doc = document;
		removeTrackedPositions(doc);
		replacedMinings = shownMinings;
		final Snapshot current = snapshot;
		if (current == null || doc == null) {
			shownMinings = List.of();
			return shownMinings;
		}
		final var minings = new ArrayList<GhostMining>(current.specs().size());
		try {
			for (final MiningSpec spec : current.specs()) {
				final var position = new Position(spec.offset(), spec.length());
				doc.addPosition(position);
				trackedPositions.add(position);
				minings.add(GhostTextMinings.create(spec, position, provider, this));
			}
		} catch (BadLocationException e) {
			LanguageServerPlugin.logError(e);
			removeTrackedPositions(doc);
			minings.clear();
		}
		shownMinings = List.copyOf(minings);
		return shownMinings;
	}

	private void removeTrackedPositions(@Nullable IDocument doc) {
		if (doc != null) {
			trackedPositions.forEach(doc::removePosition);
		}
		trackedPositions.clear();
	}

	/** Refreshes the code minings once the current UI event is processed, coalescing requests. */
	private void refreshCodeMinings() {
		if (refreshScheduled || disposed) {
			return;
		}
		refreshScheduled = true;
		display.asyncExec(() -> {
			refreshScheduled = false;
			if (!disposed && !textWidget.isDisposed()) {
				((ISourceViewerExtension5) viewer).updateCodeMinings();
			}
		});
	}

	private void registerOccupied(IDocument doc, List<MiningSpec> specs) {
		int contentOffset = -1;
		int headerLine = -1;
		for (final MiningSpec spec : specs) {
			if (spec.kind() == Kind.HEADER) {
				try {
					headerLine = doc.getLineOfOffset(spec.offset());
				} catch (BadLocationException e) {
					// ignore
				}
			} else if (spec.kind() == Kind.CONTENT || contentOffset < 0) {
				// the first line, at the caret, rather than further lines below the end of the document
				contentOffset = spec.offset();
			}
		}
		GhostTextRegistry.set(doc, contentOffset, headerLine);
	}

	private void updateBottomMargin(List<MiningSpec> specs) {
		final int rows = specs.stream().filter(spec -> spec.kind() == Kind.END_OF_DOCUMENT)
				.mapToInt(spec -> spec.rows().size()).sum();
		if (rows == 0) {
			restoreBottomMargin();
			return;
		}
		if (originalBottomMargin < 0) {
			originalBottomMargin = textWidget.getBottomMargin();
		}
		textWidget.setBottomMargin(originalBottomMargin + rows * (textWidget.getLineHeight() + textWidget.getLineSpacing()));
	}

	private void restoreBottomMargin() {
		if (originalBottomMargin >= 0 && !textWidget.isDisposed()) {
			textWidget.setBottomMargin(originalBottomMargin);
			textWidget.redraw();
		}
		originalBottomMargin = -1;
	}

	private void setVisibleContext(boolean visible) {
		final IContextService contextService = editor == null ? null : editor.getSite().getService(IContextService.class);
		if (visible && contextActivation == null && contextService != null) {
			contextActivation = contextService.activateContext(VISIBLE_CONTEXT_ID);
		} else if (!visible && contextActivation != null) {
			if (contextService != null) {
				contextService.deactivateContext(contextActivation);
			}
			contextActivation = null;
		}
	}

	private void updateStatus(IDocument doc, int caret) {
		final InlineCompletionSession current = session;
		if (current != null) {
			final int[] position = current.position(doc, caret);
			setStatusMessage(NLS.bind(Messages.InlineCompletion_status, position[0], position[1]));
		}
	}

	private void clearNoSuggestions() {
		if (noSuggestionsShown) {
			noSuggestionsShown = false;
			setStatusMessage(null);
		}
	}

	private void setStatusMessage(@Nullable String message) {
		final IEditorSite site = editor == null ? null : editor.getEditorSite();
		if (site != null) {
			final IStatusLineManager statusLine = site.getActionBars().getStatusLineManager();
			if (statusLine != null) {
				statusLine.setMessage(message);
			}
		}
	}

	// ---- called by the minings -------------------------------------------------------------------------

	boolean isOwnWidget(StyledText widget) {
		return widget == textWidget;
	}

	void ghostPainted() {
		painted = true;
	}

	// ---- completion popup ------------------------------------------------------------------------------

	private final class CompletionListener implements ICompletionListener, ICompletionListenerExtension2 {

		@Override
		public void assistSessionStarted(ContentAssistEvent event) {
			contentAssistActive = true;
			proposalApplied = false;
			selectedCompletion = null;
			appliedSelection = null;
			selectionCandidates = List.of();
			// the popup covers the lines below the caret: show only the first line
			if (session != null) {
				show();
			}
		}

		@Override
		public void assistSessionEnded(ContentAssistEvent event) {
			// applied() may be notified after this, so decide once the current event is processed
			display.asyncExec(() -> {
				contentAssistActive = false;
				selectedCompletion = null;
				if (disposed) {
					return;
				}
				final InlineCompletionSession current = session;
				final PendingRequest inFlight = pending;
				if (!proposalApplied && (current != null && current.isSelectionBound()
						|| inFlight != null && inFlight.selected != null)) {
					// what was shown or asked for extends a proposal that is gone
					dismiss();
					scheduleAutomaticRequest();
				} else if (current != null) {
					// show the further lines that were hidden under the popup
					show();
				}
			});
		}

		@Override
		public void selectionChanged(@Nullable ICompletionProposal proposal, boolean smartToggle) {
			final SelectedCompletion selected = toSelectedCompletion(proposal);
			if (selected == null ? selectedCompletion == null : selected.equals(selectedCompletion)) {
				return;
			}
			selectedCompletion = selected;
			selectionCandidates = List.of();
			// a reply for the previously selected proposal would not extend the new one
			cancelPending();
			if (selected != null) {
				// what was shown does not extend the newly selected proposal
				endSession();
				scheduleAutomaticRequest();
			} else if (session != null && session.isSelectionBound()) {
				endSession();
			}
		}

		@Override
		public void applied(@Nullable ICompletionProposal proposal) {
			proposalApplied = true;
			final SelectedCompletion selected = appliedSelection != null ? appliedSelection : selectedCompletion;
			final List<InlineCompletionCandidate> candidates = selectionCandidates;
			appliedSelection = null;
			selectionCandidates = List.of();
			final IDocument doc = document;
			if (selected == null || candidates.isEmpty() || doc == null) {
				return;
			}
			// the selected text now replaces the selected range: continue with what the candidates add to it
			final int end = selected.start() + selected.text().length();
			final var moved = candidates.stream().map(candidate -> new InlineCompletionCandidate(candidate.source(),
					selected.start(), end, candidate.text(), candidate.snippet(), candidate.filterText(), candidate.command()))
					.toList();
			final var current = new InlineCompletionSession(InlineCompletionTriggerKind.Automatic, false);
			current.addCandidates(moved);
			session = current;
			sessionRequestId = ++requestId;
			display.asyncExec(() -> {
				if (!disposed && session == current) {
					show();
				}
			});
		}
	}

	private @Nullable SelectedCompletion toSelectedCompletion(@Nullable ICompletionProposal proposal) {
		final IDocument doc = document;
		if (!(proposal instanceof LSCompletionProposal lsProposal) || doc == null) {
			return null;
		}
		try {
			final TextEdit edit = lsProposal.getEffectiveEdit(viewer, caretOffset());
			final var range = edit.getRange();
			if (range.getStart().getLine() != range.getEnd().getLine()) {
				return null;
			}
			final int start = LSPEclipseUtils.toOffset(range.getStart(), doc);
			final int end = LSPEclipseUtils.toOffset(range.getEnd(), doc);
			final String text = edit.getNewText();
			// a proposal replacing typed text by something else could not be extended as an insertion
			if (!text.startsWith(doc.get(start, caretOffset() - start))) {
				return null;
			}
			return new SelectedCompletion(start, end, text);
		} catch (BadLocationException e) {
			return null;
		} catch (RuntimeException e) {
			// a failure here must not get in the way of the completion popup
			LanguageServerPlugin.logError(e);
			return null;
		}
	}

	// ---- helpers ---------------------------------------------------------------------------------------

	private int caretOffset() {
		return viewer.getSelectedRange().x;
	}

	private String linePrefix(int offset) {
		final IDocument doc = document;
		if (doc == null) {
			return ""; //$NON-NLS-1$
		}
		try {
			final IRegion line = doc.getLineInformationOfOffset(offset);
			return doc.get(line.getOffset(), offset - line.getOffset());
		} catch (BadLocationException e) {
			return ""; //$NON-NLS-1$
		}
	}

	private boolean isLineVisible(int modelLine) {
		return !(viewer instanceof ITextViewerExtension5 extension) || extension.modelLine2WidgetLine(modelLine) >= 0;
	}

	private boolean isComposing() {
		final var ime = textWidget.getIME();
		return ime != null && ime.getText() != null && !ime.getText().isEmpty();
	}

	private @Nullable ContentAssistantFacade contentAssistantFacade() {
		return viewer instanceof ISourceViewerExtension4 extension ? extension.getContentAssistantFacade() : null;
	}

	private static @Nullable IOperationHistory operationHistory() {
		return PlatformUI.isWorkbenchRunning() ? PlatformUI.getWorkbench().getOperationSupport().getOperationHistory() : null;
	}

	// ---- test support ----------------------------------------------------------------------------------

	/** Returns the ghost text shown, or {@code null}; for tests. */
	public @Nullable GhostText getShownGhostText() {
		final Snapshot current = snapshot;
		return current == null ? null : current.ghost();
	}

	/** Returns the layout of the ghost text shown, empty if none; for tests. */
	public List<MiningSpec> getShownLayout() {
		final Snapshot current = snapshot;
		return current == null ? List.of() : current.specs();
	}

	/** Returns the session of the latest request, or {@code null}; for tests. */
	public @Nullable InlineCompletionSession getSession() {
		return session;
	}

	/** Returns whether the ghost text shown has been drawn; for tests. */
	public boolean isGhostPainted() {
		return painted;
	}

	/** Sets how long a request holds back new requests without a reply; for tests. */
	public void setStaleRequestTimeout(long millis) {
		staleRequestNanos = TimeUnit.MILLISECONDS.toNanos(millis);
	}
}
