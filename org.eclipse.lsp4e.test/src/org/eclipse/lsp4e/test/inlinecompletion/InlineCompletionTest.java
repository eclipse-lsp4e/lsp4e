/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.inlinecompletion;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;
import static org.eclipse.lsp4e.test.inlinecompletion.GhostTextModelTest.movedSpec;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.ITextPresentationListener;
import org.eclipse.jface.text.ITextViewerExtension2;
import org.eclipse.jface.text.ITextViewerExtension4;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.ISourceViewerExtension5;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.FileBufferListenerAdapter;
import org.eclipse.lsp4e.internal.LSPMethods;
import org.eclipse.lsp4e.internal.SupportedFeatures;
import org.eclipse.lsp4e.operations.codelens.CodeLensProvider;
import org.eclipse.lsp4e.operations.inlayhint.InlayHintProvider;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostText;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextLayout.Kind;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.GhostTextLayout.MiningSpec;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionController;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.ui.InlineCompletionPreferencePage;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionList;
import org.eclipse.lsp4j.InlineCompletionParams;
import org.eclipse.lsp4j.InlineCompletionTriggerKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.StringValue;
import org.eclipse.lsp4j.StringValueKind;
import org.eclipse.lsp4j.Unregistration;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.GlyphMetrics;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.TextStyle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.handlers.IHandlerService;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests of inline completions in the generic editor, against the mock language server.
 * What ghost text is computed and how it is laid out is tested by {@link GhostTextModelTest}; these
 * tests cover requests, keys, accepting and the interplay with the editor.
 * <p>
 * Ghost text is dismissed when the editor loses focus, so these tests need the workbench window to
 * keep the focus while they run.
 */
public class InlineCompletionTest extends AbstractTestWithProject {

	private static final int TIMEOUT = 5_000;
	private static final String FAILURE = "mock inline completion failure";

	private static final String AUTO_ACTIVATION = "contentAssistant.autoActivation";
	private static final String AUTO_ACTIVATION_ON_TYPE = "contentAssistant.autoActivationOnType";

	private final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
	/**
	 * The generic editor opens the completion popup while typing, and the mock server proposes a
	 * completion: its selection would be sent along and change what is requested. Popup interplay is
	 * tested by {@link InlineCompletionWithCompletionPopupTest}; here it is switched off.
	 */
	private final IPreferenceStore genericEditorStore = new ScopedPreferenceStore(InstanceScope.INSTANCE,
			"org.eclipse.ui.genericeditor");

	@BeforeEach
	public void setUpPreferences() {
		store.setValue(InlineCompletionPreferencePage.PREF_AUTOMATIC, true);
		store.setValue(InlineCompletionPreferencePage.PREF_DELAY, 20);
		genericEditorStore.setValue(AUTO_ACTIVATION, false);
		genericEditorStore.setValue(AUTO_ACTIVATION_ON_TYPE, false);
	}

	@AfterEach
	public void resetPreferences() {
		store.setToDefault(InlineCompletionPreferencePage.PREF_AUTOMATIC);
		store.setToDefault(InlineCompletionPreferencePage.PREF_DELAY);
		genericEditorStore.setToDefault(AUTO_ACTIVATION);
		genericEditorStore.setToDefault(AUTO_ACTIVATION_ON_TYPE);
	}

	static ServerCapabilities capabilities() {
		final ServerCapabilities capabilities = MockLanguageServer.defaultServerCapabilities();
		capabilities.setInlineCompletionProvider(true);
		return capabilities;
	}

	static Function<InlineCompletionParams, Either<List<InlineCompletionItem>, InlineCompletionList>> items(
			InlineCompletionItem... items) {
		return params -> Either.forLeft(List.of(items));
	}

	static InlineCompletionItem item(String text) {
		final var item = new InlineCompletionItem();
		item.setInsertText(text);
		return item;
	}

	static InlineCompletionItem item(String text, int line, int startCharacter, int endCharacter) {
		final var item = new InlineCompletionItem();
		item.setInsertText(text);
		item.setRange(new Range(new Position(line, startCharacter), new Position(line, endCharacter)));
		return item;
	}

	/** A server error with {@link #FAILURE} as its message. */
	private static Either<List<InlineCompletionItem>, InlineCompletionList> fail() {
		throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InternalError, FAILURE, null));
	}

	private ISourceViewer open(MockLanguageServerFactory factory, String content,
			Function<InlineCompletionParams, Either<List<InlineCompletionItem>, InlineCompletionList>> responder)
			throws Exception {
		return open(factory, "lspt", content, InlineCompletionTest::capabilities, responder);
	}

	private ISourceViewer open(MockLanguageServerFactory factory, String extension, String content,
			Supplier<ServerCapabilities> capabilities,
			Function<InlineCompletionParams, Either<List<InlineCompletionItem>, InlineCompletionList>> responder)
			throws Exception {
		factory.withCapabilities(capabilities);
		factory.withConfiguration((index, server) -> server.getTextDocumentService().setInlineCompletionResponder(responder));
		return openEditor(TestUtils.createUniqueTestFile(project, extension, content));
	}

	/** Opens the file and waits until inline completions can be requested in it. */
	static ISourceViewer openEditor(IFile file) throws Exception {
		final var viewer = (ISourceViewer) TestUtils.openTextViewer(file);
		waitForAndAssertCondition(TIMEOUT, () -> InlineCompletionController.get(viewer) != null);
		waitForAndAssertCondition(TIMEOUT, () -> LanguageServers.forDocument(viewer.getDocument()).anyMatching());
		viewer.getTextWidget().forceFocus();
		return viewer;
	}

	static InlineCompletionController controller(ISourceViewer viewer) {
		final InlineCompletionController controller = InlineCompletionController.get(viewer);
		assertNotNull(controller);
		return controller;
	}

	static GhostText waitForGhost(ISourceViewer viewer) {
		waitForAndAssertCondition("ghost text shown", TIMEOUT, () -> controller(viewer).getShownGhostText() != null);
		return castNonNull(controller(viewer).getShownGhostText());
	}

	static void waitForGhostText(ISourceViewer viewer, String text) {
		waitForAndAssertCondition("ghost text \"" + text + "\" shown", TIMEOUT, () -> {
			final GhostText ghost = controller(viewer).getShownGhostText();
			assertNotNull(ghost);
			assertEquals(text, ghost.text());
		});
	}

	static void waitForNoGhost(ISourceViewer viewer) {
		waitForAndAssertCondition("no ghost text", TIMEOUT, () -> controller(viewer).getShownGhostText() == null);
	}

	/** Lets queued UI work run, for checks that something does not happen. */
	static void processEvents(int millis) {
		final Display display = UI.getDisplay();
		DisplayHelper.sleep(display, millis);
	}

	static void setCaret(ISourceViewer viewer, int offset) {
		viewer.setSelectedRange(offset, 0);
	}

	/** Types like the user: through StyledText's key handling, verify-key listeners included. */
	static void type(ISourceViewer viewer, char character) {
		final StyledText widget = viewer.getTextWidget();
		final var event = new Event();
		event.widget = widget;
		event.character = character;
		event.keyCode = character;
		widget.notifyListeners(SWT.KeyDown, event);
		processEvents(0);
	}

	/** Presses a key without processing the UI events it causes, as when keys are typed in quick succession. */
	static void press(ISourceViewer viewer, char character) {
		final StyledText widget = viewer.getTextWidget();
		final var event = new Event();
		event.widget = widget;
		event.character = character;
		event.keyCode = character;
		widget.notifyListeners(SWT.KeyDown, event);
	}

	static void type(ISourceViewer viewer, String text) {
		for (final char c : text.toCharArray()) {
			type(viewer, c);
		}
	}

	private static MockLanguageServer server(MockLanguageServerFactory factory) {
		return factory.getServer();
	}

	/**
	 * Makes the mock server hold back its inline completion replies until the returned future is
	 * completed, so that what a test does during a request cannot race with the reply.
	 */
	private static CompletableFuture<Void> holdReplies(MockLanguageServerFactory factory) {
		final var replies = new CompletableFuture<Void>();
		server(factory).getTextDocumentService().holdInlineCompletionsUntil(replies);
		return replies;
	}

	private static int requestCount(MockLanguageServerFactory factory) {
		return server(factory).getTextDocumentService().inlineCompletionRequests.size();
	}

	/** Counts the cancelled inline completion requests, leaving out the code minings' requests. */
	private static long inlineCancellations(MockLanguageServerFactory factory) {
		return factory.cancelledRequests(LSPMethods.TEXT_DOCUMENT_INLINE_COMPLETION);
	}

	private static InlineCompletionParams lastRequest(MockLanguageServerFactory factory) {
		final List<InlineCompletionParams> requests = List.copyOf(server(factory).getTextDocumentService().inlineCompletionRequests);
		assertFalse(requests.isEmpty());
		return requests.get(requests.size() - 1);
	}

	/** Counts the errors LSP4E logs because of {@link #fail()}. */
	private static final class FailureCounter implements ILogListener, AutoCloseable {
		final AtomicInteger count = new AtomicInteger();

		FailureCounter() {
			Platform.addLogListener(this);
		}

		@Override
		public void logging(IStatus status, String plugin) {
			if (status.getSeverity() == IStatus.ERROR && LanguageServerPlugin.PLUGIN_ID.equals(status.getPlugin())
					&& String.valueOf(status.getMessage()).contains(FAILURE)) {
				count.incrementAndGet();
			}
		}

		@Override
		public void close() {
			Platform.removeLogListener(this);
		}
	}

	// ---- capabilities, servers and commands ------------------------------------------------------------

	@Test
	public void requestsAreSentOnlyWhileTheCapabilityIsRegistered(MockLanguageServerFactory factory) throws Exception {
		final var inlineCompletion = SupportedFeatures.getTextDocumentClientCapabilities().getInlineCompletion();
		assertNotNull(inlineCompletion);
		assertEquals(Boolean.TRUE, inlineCompletion.getDynamicRegistration());

		final ISourceViewer viewer = open(factory, "lspt", "hello\n", MockLanguageServer::defaultServerCapabilities,
				items(item(" world")));
		final String id = UUID.randomUUID().toString();
		server(factory).getRemoteProxy()
				.registerCapability(new RegistrationParams(List.of(new Registration(id, LSPMethods.TEXT_DOCUMENT_INLINE_COMPLETION))))
				.get(TIMEOUT, TimeUnit.MILLISECONDS);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");

		controller(viewer).dismiss();
		server(factory).getRemoteProxy()
				.unregisterCapability(new UnregistrationParams(List.of(new Unregistration(id, LSPMethods.TEXT_DOCUMENT_INLINE_COMPLETION))))
				.get(TIMEOUT, TimeUnit.MILLISECONDS);
		final int requests = requestCount(factory);
		controller(viewer).trigger();
		processEvents(300);
		assertEquals(requests, requestCount(factory));
	}

	@Test
	public void candidatesOfSeveralServersAreMerged(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		factory.withConfiguration((index, server) -> server.getTextDocumentService()
				.setInlineCompletionResponder(items(item(" from server " + index))));
		final ISourceViewer viewer = openEditor(TestUtils.createUniqueTestFile(project, "lsptmultils", "hello\n"));
		waitForAndAssertCondition(TIMEOUT, () -> factory.getServerCount() == 2);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> {
			final var session = controller(viewer).getSession();
			assertNotNull(session);
			assertEquals(2, session.size());
		});
	}

	@Test
	public void aFailingServerDoesNotHideTheOthersSuggestions(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		factory.withConfiguration((index, server) -> server.getTextDocumentService()
				.setInlineCompletionResponder(index == 0 ? params -> fail() : items(item(" from server " + index))));
		final ISourceViewer viewer = openEditor(TestUtils.createUniqueTestFile(project, "lsptmultils", "hello\n"));
		waitForAndAssertCondition(TIMEOUT, () -> factory.getServerCount() == 2);
		try (var failures = new FailureCounter()) {
			setCaret(viewer, 5);
			controller(viewer).trigger();
			waitForGhostText(viewer, " from server 1");
			waitForAndAssertCondition(TIMEOUT, () -> failures.count.get() == 1);
		}
	}

	@Test
	public void aFailingServerIsLoggedOnce(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", params -> fail());
		try (var failures = new FailureCounter()) {
			setCaret(viewer, 5);
			for (int i = 1; i <= 3; i++) {
				controller(viewer).trigger();
				final int expected = i;
				waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == expected);
				waitForAndAssertCondition(TIMEOUT, () -> failures.count.get() == 1);
			}
			processEvents(100);
			assertEquals(1, failures.count.get());
		}
	}

	@Test
	public void aReplyThatTheContentWasModifiedIsNotLogged(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", params -> {
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.ContentModified, FAILURE, null));
		});
		try (var failures = new FailureCounter()) {
			setCaret(viewer, 5);
			controller(viewer).trigger();
			waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
			processEvents(200);
			assertEquals(0, failures.count.get());
		}
	}

	@Test
	public void theCyclingKeysAndTheirContextAreActiveOnlyWhileGhostTextIsShown(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" a"), item(" b")));
		final IContextService contexts = PlatformUI.getWorkbench().getService(IContextService.class);
		final IBindingService bindings = PlatformUI.getWorkbench().getService(IBindingService.class);
		final String next = "org.eclipse.lsp4e.inlineCompletion.next";
		final String visible = "org.eclipse.lsp4e.inlineCompletion.visible";
		assertFalse(contexts.getActiveContextIds().contains(visible));
		assertEquals(0, bindings.getActiveBindingsFor(next).length);

		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " a");
		waitForAndAssertCondition(TIMEOUT, () -> contexts.getActiveContextIds().contains(visible));
		waitForAndAssertCondition(TIMEOUT, () -> bindings.getActiveBindingsFor(next).length > 0);

		controller(viewer).dismiss();
		waitForAndAssertCondition(TIMEOUT, () -> !contexts.getActiveContextIds().contains(visible));
		waitForAndAssertCondition(TIMEOUT, () -> bindings.getActiveBindingsFor(next).length == 0);
	}

	@Test
	public void triggerIsBoundToThePlatformsKey(MockLanguageServerFactory factory) throws Exception {
		open(factory, "hello\n", items(item(" world")));
		final IBindingService bindings = PlatformUI.getWorkbench().getService(IBindingService.class);
		// macOS: Option+key types a character, so Ctrl is used there
		final boolean mac = Platform.OS_MACOSX.equals(Platform.getOS());
		final KeySequence expected = KeySequence.getInstance(mac ? "M4+\\" : "M3+\\");
		final List<TriggerSequence> active = List.of(bindings.getActiveBindingsFor("org.eclipse.lsp4e.inlineCompletion.trigger"));
		assertTrue(active.contains(expected), "active bindings: " + active);
		if (mac) {
			assertFalse(active.contains(KeySequence.getInstance("M3+\\")), "active bindings: " + active);
		}
	}

	@Test
	public void commandsWorkThroughTheHandlerService(MockLanguageServerFactory factory) throws Exception {
		// as a key binding runs them, so that the handlers' activeWhen is evaluated
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" a"), item(" b")));
		setCaret(viewer, 5);
		final IHandlerService handlers = PlatformUI.getWorkbench().getService(IHandlerService.class);
		handlers.executeCommand("org.eclipse.lsp4e.inlineCompletion.trigger", null);
		waitForGhostText(viewer, " a");
		assertEquals(InlineCompletionTriggerKind.Invoked, lastRequest(factory).getContext().getTriggerKind());
		handlers.executeCommand("org.eclipse.lsp4e.inlineCompletion.next", null);
		waitForGhostText(viewer, " b");
		handlers.executeCommand("org.eclipse.lsp4e.inlineCompletion.previous", null);
		waitForGhostText(viewer, " a");
		handlers.executeCommand("org.eclipse.lsp4e.inlineCompletion.accept", null);
		assertEquals("hello a\n", viewer.getDocument().get());
	}

	// ---- requests and keys -----------------------------------------------------------------------------

	@Test
	public void explicitTriggerSendsAnInvokedRequestAndShowsTheGhostAtTheEndOfALine(
			MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\nsecond line\n", items(item(" world")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");

		final InlineCompletionParams params = lastRequest(factory);
		assertEquals(InlineCompletionTriggerKind.Invoked, params.getContext().getTriggerKind());
		assertNull(params.getContext().getSelectedCompletionInfo());
		assertEquals(new Position(0, 5), params.getPosition());
		// drawn on the line delimiter
		assertEquals(List.of(new MiningSpec(Kind.CONTENT, 5, 1, " world", List.of())), controller(viewer).getShownLayout());
	}

	@Test
	public void typingThroughShrinksTheGhostWithoutNewRequests(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hel\n", items(item("o world")));
		setCaret(viewer, 3);
		type(viewer, 'l');
		waitForGhostText(viewer, "o world");
		assertEquals(InlineCompletionTriggerKind.Automatic, lastRequest(factory).getContext().getTriggerKind());
		final int requests = requestCount(factory);

		type(viewer, "o w");
		waitForGhostText(viewer, "orld");
		assertEquals(requests, requestCount(factory), "typing through must not request again");

		// fully typed: the session ends and a new automatic request follows
		type(viewer, "orld");
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) > requests);
	}

	@Test
	public void aNonMatchingCharacterDismissesAndRequestsAgain(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");
		final int requests = requestCount(factory);

		final CompletableFuture<Void> replies = holdReplies(factory);
		type(viewer, 'x');
		waitForNoGhost(viewer);
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) > requests);
		replies.complete(null);
	}

	@Test
	public void backspaceInsideTheReplacedRangeGrowsTheGhostBack(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "co\n", items(item("console", 0, 0, 2)));
		setCaret(viewer, 2);
		controller(viewer).trigger();
		waitForGhostText(viewer, "nsole");

		type(viewer, SWT.BS);
		waitForGhostText(viewer, "onsole");
	}

	@Test
	public void tabAcceptsAndUndoRestores(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");
		waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());

		type(viewer, SWT.TAB);
		assertEquals("hello world\n", viewer.getDocument().get());
		assertEquals(11, viewer.getSelectedRange().x);
		waitForNoGhost(viewer);

		final var target = (ITextOperationTarget) viewer;
		target.doOperation(ITextOperationTarget.UNDO);
		assertEquals("hello\n", viewer.getDocument().get());
		target.doOperation(ITextOperationTarget.REDO);
		assertEquals("hello world\n", viewer.getDocument().get());
		processEvents(200);
		assertNull(controller(viewer).getShownGhostText(), "no ghost text reappears after redo");
	}

	@Test
	public void tabRightAfterAnEditOrACaretMoveActsOnTheGhostTextOfTheNewState(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "x = \nnext\n", items(item("foo")));
		setCaret(viewer, 4);
		controller(viewer).trigger();
		waitForGhostText(viewer, "foo");
		waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());
		// Tab arrives before the edit is processed: it accepts what is left after typing "f"
		press(viewer, 'f');
		press(viewer, SWT.TAB);
		assertEquals("x = foo\nnext\n", viewer.getDocument().get());

		processEvents(100);
		controller(viewer).trigger();
		waitForGhostText(viewer, "foo");
		waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());
		// Tab arrives before the caret move is processed: the ghost text was left behind
		setCaret(viewer, 12);
		press(viewer, SWT.TAB);
		assertEquals("x = foo\nnext\t\n", viewer.getDocument().get());
	}

	@Test
	public void tabWithoutGhostKeepsItsMeaning(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items());
		store.setValue(InlineCompletionPreferencePage.PREF_AUTOMATIC, false);
		setCaret(viewer, 5);
		type(viewer, SWT.TAB);
		assertEquals("hello\t\n", viewer.getDocument().get());
	}

	@Test
	public void escapeDismisses(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");
		type(viewer, SWT.ESC);
		waitForNoGhost(viewer);
		assertEquals("hello\n", viewer.getDocument().get());
	}

	@Test
	public void escapeCancelsARequestAlthoughNoGhostIsShownYet(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final CompletableFuture<Void> replies = holdReplies(factory);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
		type(viewer, SWT.ESC);
		waitForAndAssertCondition(TIMEOUT, () -> inlineCancellations(factory) == 1);
		replies.complete(null);
		processEvents(200);
		assertNull(controller(viewer).getShownGhostText());
	}

	@Test
	public void theDismissCommandCancelsARequest(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final CompletableFuture<Void> replies = holdReplies(factory);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
		controller(viewer).dismiss();
		waitForAndAssertCondition(TIMEOUT, () -> inlineCancellations(factory) == 1);
		replies.complete(null);
		processEvents(200);
		assertNull(controller(viewer).getShownGhostText());
	}

	@Test
	public void noAutomaticRequestFollowsEscapeOrACaretMoveOrLosingTheFocus(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "hello\nsecond\n", items(item(" world")));
		store.setValue(InlineCompletionPreferencePage.PREF_DELAY, 500);
		setCaret(viewer, 5);
		type(viewer, 'x');
		type(viewer, SWT.ESC);
		type(viewer, 'x');
		setCaret(viewer, 9);
		type(viewer, 'x');
		viewer.getTextWidget().notifyListeners(SWT.FocusOut, new Event());
		processEvents(700);
		assertEquals(0, requestCount(factory));
	}

	@Test
	public void noAutomaticRequestsWithoutTheFocus(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		setCaret(viewer, 5);
		viewer.getTextWidget().notifyListeners(SWT.FocusOut, new Event());
		// as when the document is edited in another editor
		viewer.getDocument().replace(5, 0, "!");
		processEvents(200);
		assertEquals(0, requestCount(factory));

		viewer.getTextWidget().notifyListeners(SWT.FocusIn, new Event());
		type(viewer, 'x');
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
	}

	@Test
	public void anEditFromAnotherThreadDismissesAndCancels(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\nsecond\n", items(item(" world")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");
		final CompletableFuture<Void> replies = holdReplies(factory);
		// the text viewer itself does not support this and logs errors of its own
		final var errors = new AtomicInteger();
		final ILogListener listener = (status, plugin) -> {
			final Throwable exception = status.getException();
			if (exception != null && Arrays.stream(exception.getStackTrace())
					.anyMatch(frame -> frame.getClassName().startsWith(InlineCompletionController.class.getName()))) {
				errors.incrementAndGet();
			}
		};
		Platform.addLogListener(listener);
		try {
			final IDocument document = viewer.getDocument();
			final var edit = CompletableFuture.runAsync(() -> {
				try {
					document.replace(12, 0, "!");
				} catch (BadLocationException e) {
					throw new IllegalStateException(e);
				}
			});
			waitForAndAssertCondition(TIMEOUT, edit::isDone);
			assertFalse(edit.isCompletedExceptionally(), "a document listener threw on the editing thread");
			waitForNoGhost(viewer);
			assertEquals(0, errors.get());
		} finally {
			Platform.removeLogListener(listener);
			replies.complete(null);
		}
	}

	@Test
	public void changingTheViewersDocumentDismissesAndCancels(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final CompletableFuture<Void> replies = holdReplies(factory);
		final IDocument original = viewer.getDocument();
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
		viewer.setDocument(new Document("other\n"));
		waitForAndAssertCondition(TIMEOUT, () -> inlineCancellations(factory) == 1);
		replies.complete(null);
		processEvents(200);
		assertNull(controller(viewer).getShownGhostText());
		assertEquals("hello\n", original.get());
	}

	@Test
	public void movingTheCaretDismissesAlsoAfterAnEarlierAccept(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\nhello\n", items(item(" world")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");
		controller(viewer).accept();
		assertEquals("hello world\nhello\n", viewer.getDocument().get());

		setCaret(viewer, 17);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");
		setCaret(viewer, 3);
		waitForNoGhost(viewer);
	}

	@Test
	public void anEditAwayFromTheCaretDismissesAndCancels(MockLanguageServerFactory factory) throws Exception {
		// as a formatter or a refactoring makes them; this one does not move the caret
		final ISourceViewer viewer = open(factory, "hello\nsecond\n", items(item(" world")));
		final IDocument document = viewer.getDocument();
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");
		document.replace(12, 0, "!");
		waitForNoGhost(viewer);

		final CompletableFuture<Void> replies = holdReplies(factory);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 2);
		document.replace(13, 0, "!");
		waitForAndAssertCondition(TIMEOUT, () -> inlineCancellations(factory) == 1);
		replies.complete(null);
		processEvents(200);
		assertNull(controller(viewer).getShownGhostText());
	}

	@Test
	public void losingTheFocusDismissesAndCancels(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");
		viewer.getTextWidget().notifyListeners(SWT.FocusOut, new Event());
		waitForNoGhost(viewer);

		final CompletableFuture<Void> replies = holdReplies(factory);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 2);
		viewer.getTextWidget().notifyListeners(SWT.FocusOut, new Event());
		waitForAndAssertCondition(TIMEOUT, () -> inlineCancellations(factory) == 1);
		replies.complete(null);
	}

	@Test
	public void cyclingASingleAutomaticResultRequestsAlternatives(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hel\n", params -> Either.forLeft(
				params.getContext().getTriggerKind() == InlineCompletionTriggerKind.Automatic ? List.of(item("o"))
						: List.of(item("o"), item("p"))));
		setCaret(viewer, 3);
		type(viewer, 'l');
		waitForGhostText(viewer, "o");
		controller(viewer).cycle(true);
		waitForAndAssertCondition(TIMEOUT, () -> {
			assertEquals(InlineCompletionTriggerKind.Invoked, lastRequest(factory).getContext().getTriggerKind());
			final var session = controller(viewer).getSession();
			assertNotNull(session);
			assertEquals(2, session.size());
		});
	}

	@Test
	public void automaticRequestsCanBeDisabled(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hel\n", items(item("o")));
		store.setValue(InlineCompletionPreferencePage.PREF_AUTOMATIC, false);
		setCaret(viewer, 3);
		type(viewer, 'l');
		processEvents(300);
		assertEquals(0, requestCount(factory));
		// explicit requests still work
		controller(viewer).trigger();
		waitForGhostText(viewer, "o");
	}

	@Test
	public void noRequestWithASelectionOrInAReadOnlyViewer(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		viewer.setSelectedRange(0, 5);
		controller(viewer).trigger();
		viewer.setEditable(false);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		processEvents(300);
		assertEquals(0, requestCount(factory));
	}

	// ---- accepting -------------------------------------------------------------------------------------

	@Test
	public void closingParenthesisAfterTheCaretIsNotDoubled(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "print()\n", items(item("\"hi\")", 0, 6, 7)));
		setCaret(viewer, 6);
		controller(viewer).trigger();
		// the ")" already in the document is not part of the ghost text
		waitForGhostText(viewer, "\"hi\"");
		controller(viewer).accept();
		assertEquals("print(\"hi\")\n", viewer.getDocument().get());
	}

	@Test
	public void multiLineGhostContinuesAboveTheNextLineAndIsInsertedAsIs(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "def f():\nx\n", items(item("\n    return\t1")));
		setCaret(viewer, 8);
		controller(viewer).trigger();
		waitForGhost(viewer);
		// no inline part: the line is drawn above line 1, with its tab expanded
		final List<MiningSpec> layout = controller(viewer).getShownLayout();
		assertEquals(1, layout.size());
		assertEquals(Kind.HEADER, layout.get(0).kind());
		assertEquals(9, layout.get(0).offset());
		assertEquals("", layout.get(0).firstRow());
		assertFalse(layout.get(0).rows().get(0).contains("\t"), "drawn: " + layout.get(0).rows());
		// inserted as is: neither re-indented nor with the tab expanded
		controller(viewer).accept();
		assertEquals("def f():\n    return\t1\nx\n", viewer.getDocument().get());
	}

	/** Whether each character of the given range is hidden: drawn without width. */
	private static void assertHidden(ISourceViewer viewer, int offset, int length, boolean expected) {
		for (int i = offset; i < offset + length; i++) {
			final StyleRange style = viewer.getTextWidget().getStyleRangeAtOffset(i);
			assertEquals(expected, style != null && style.metrics != null && style.metrics.width == 0,
					"hidden at " + i + ", metrics: " + (style == null ? "no style" : style.metrics));
		}
	}

	@Test
	public void multiLineGhostInTheMiddleOfALineShowsWhereTheRestOfTheLineMoves(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "call(arg)\nnext\n", items(item("x,\n  y, ")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhost(viewer);
		// accepting breaks the line: the rest of it, "arg)", ends the last line of the preview, as it will
		// end the last line of the suggestion, and is hidden where it is now; the first line is drawn
		// after it, on the line delimiter
		assertEquals(List.of(new MiningSpec(Kind.CONTENT, 9, 1, "x,", List.of()),
				movedSpec(Kind.HEADER, 10, 1, "", List.of("  y, arg)"), 4)), controller(viewer).getShownLayout());
		waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 5, 4, true));
		assertHidden(viewer, 0, 5, false);
		final StyledText widget = viewer.getTextWidget();
		assertEquals(widget.getLocationAtOffset(5).x, widget.getLocationAtOffset(9).x,
				"the caret stays right before the ghost text");

		controller(viewer).accept();
		assertEquals("call(x,\n  y, arg)\nnext\n", viewer.getDocument().get());
		waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 13, 4, false));
	}

	@Test
	public void theHiddenTextFollowsTypingThroughAndComesBackWithoutTheGhostText(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "call(arg)\nnext\n", items(item("x,\n  y, ")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 5, 4, true));

		type(viewer, 'x');
		waitForGhostText(viewer, ",\n  y, ");
		assertEquals(List.of("  y, arg)"), controller(viewer).getShownLayout().get(1).rows());
		waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 6, 4, true));
		assertHidden(viewer, 5, 1, false);

		type(viewer, SWT.ESC);
		waitForNoGhost(viewer);
		waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 6, 4, false));

		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 6, 4, true));
		controller(viewer).accept();
		((ITextOperationTarget) viewer).doOperation(ITextOperationTarget.UNDO);
		assertEquals("call(xarg)\nnext\n", viewer.getDocument().get());
		waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 6, 4, false));
	}

	@Test
	public void movedTextShownAgainByJFaceIsHiddenAgainHoweverLongTheCoveringMiningLasts(
			MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "call(arg)\nnext\n", items(item("x,\n  y, ")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());
		waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 5, 4, true));
		final StyledText widget = viewer.getTextWidget();
		final var extension = (ITextViewerExtension4) viewer;
		// a line content mining from before still covers the first moved character, so JFace keeps it wide
		// through some paints, however hard the controller tries to hide it
		final ITextPresentationListener covering = presentation -> {
			final var style = new StyleRange(5, 1, null, null);
			style.metrics = new GlyphMetrics(0, 0, 20);
			presentation.mergeStyleRange(style);
		};
		extension.addTextPresentationListener(covering);
		try {
			for (int i = 0; i < 5; i++) {
				((ITextViewerExtension2) viewer).invalidateTextPresentation(5, 1);
				widget.redraw();
				processEvents(100);
			}
			assertEquals(20, widget.getStyleRangeAtOffset(5).metrics.width);
		} finally {
			extension.removeTextPresentationListener(covering);
		}
		// then the mining is removed and JFace resets the character's style
		final StyleRange style = widget.getStyleRangeAtOffset(5);
		style.metrics = null;
		widget.setStyleRange(style);
		waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 5, 4, true));
	}

	@Test
	public void theMovedTextIsDrawnInTheEditorsStyle(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "call(arg + 1)\nnext\n", items(item("x,\n  y, ")));
		final StyledText widget = viewer.getTextWidget();
		final var foreground = new Color(0, 0, 160);
		final var keyword = new Color(200, 0, 0);
		widget.setForeground(foreground);
		// like syntax highlighting: "arg" bold and red
		((ITextViewerExtension4) viewer).addTextPresentationListener(
				presentation -> presentation.mergeStyleRange(new StyleRange(5, 3, keyword, null, SWT.BOLD)));
		((ITextViewerExtension2) viewer).invalidateTextPresentation(0, viewer.getDocument().getLength());
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhost(viewer);
		waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());

		final List<TextStyle> styles = controller(viewer).getMovedTextStyles();
		assertEquals("arg + 1)".length(), styles.size());
		for (int i = 0; i < styles.size(); i++) {
			final TextStyle style = styles.get(i);
			final boolean isKeyword = i < 3;
			assertEquals(isKeyword ? keyword : foreground, style.foreground, "foreground at " + i);
			assertNull(style.background, "background at " + i);
			assertNull(style.metrics, "hidden where it is, not where it is drawn: " + i);
			assertEquals(isKeyword ? SWT.BOLD : SWT.NORMAL, style.font.getFontData()[0].getStyle() & SWT.BOLD,
					"font style at " + i);
			assertEquals(widget.getFont().getFontData()[0].getHeight(), style.font.getFontData()[0].getHeight());
		}
	}

	@Test
	public void ghostTextFollowsFontChanges(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "call(arg)\nnext\n", items(item("xxxxxxxx,\n  y, ")));
		final StyledText widget = viewer.getTextWidget();
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhost(viewer);
		waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());

		final Font original = widget.getFont();
		final FontData data = original.getFontData()[0];
		final var bigger = new Font(widget.getDisplay(), data.getName(), data.getHeight() * 2, SWT.NORMAL);
		try {
			widget.setFont(bigger);
			final GC gc = new GC(widget);
			final int labelWidth;
			try {
				gc.setFont(bigger);
				labelWidth = gc.stringExtent("xxxxxxxx,").x;
			} finally {
				gc.dispose();
			}
			// the room made for the first line on the line delimiter grows with the font
			waitForAndAssertCondition(TIMEOUT, () -> {
				final StyleRange delimiter = widget.getStyleRangeAtOffset(9);
				assertNotNull(delimiter);
				assertNotNull(delimiter.metrics);
				assertTrue(delimiter.metrics.width >= labelWidth, delimiter.metrics.width + " < " + labelWidth);
			});
			for (final TextStyle style : controller(viewer).getMovedTextStyles()) {
				assertEquals(bigger, style.font);
			}
		} finally {
			widget.setFont(original);
			bigger.dispose();
		}
	}

	@Test
	public void ghostTextFollowsTabWidthChanges(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "a\nb\n", items(item("x\n\ty")));
		final StyledText widget = viewer.getTextWidget();
		final int tabs = widget.getTabs();
		try {
			widget.setTabs(4);
			setCaret(viewer, 1);
			controller(viewer).trigger();
			waitForGhost(viewer);
			waitForAndAssertCondition(TIMEOUT, () -> assertEquals(List.of("    y"), controller(viewer).getShownLayout().get(1).rows()));
			widget.setTabs(8);
			waitForAndAssertCondition(TIMEOUT, () -> assertEquals(List.of("        y"), controller(viewer).getShownLayout().get(1).rows()));
		} finally {
			widget.setTabs(tabs);
		}
	}

	/** Returns the offsets at which the widget starts a visual line, in the given model line. */
	private static List<Integer> visualLineStarts(ISourceViewer viewer, int line) throws Exception {
		final StyledText widget = viewer.getTextWidget();
		final IDocument doc = viewer.getDocument();
		final var starts = new ArrayList<Integer>();
		int y = -1;
		for (int offset = doc.getLineOffset(line); offset < doc.getLineOffset(line) + doc.getLineLength(line); offset++) {
			final int top = widget.getTextBounds(offset, offset).y;
			if (top != y) {
				starts.add(offset);
				y = top;
			}
		}
		return starts;
	}

	private static List<String> previewRows(ISourceViewer viewer) {
		final var rows = new ArrayList<String>();
		for (final MiningSpec spec : controller(viewer).getShownLayout()) {
			if (!spec.firstRow().isEmpty()) {
				rows.add(spec.firstRow());
			}
			rows.addAll(spec.rows());
		}
		return rows;
	}

	@Test
	public void withWordWrapGhostTextWrapsWhereTheEditorWill(MockLanguageServerFactory factory) throws Exception {
		final String words = String.join(" ", Collections.nCopies(60, "word"));
		final ISourceViewer viewer = open(factory, "x = \nnext\n", items(item(words)));
		final StyledText widget = viewer.getTextWidget();
		widget.setWordWrap(true);
		try {
			setCaret(viewer, 4);
			controller(viewer).trigger();
			waitForGhostText(viewer, words);
			waitForAndAssertCondition(TIMEOUT, () -> assertTrue(previewRows(viewer).size() > 1, "wrapped: " + previewRows(viewer)));
			assertEquals(words, String.join("", previewRows(viewer)));

			// switching word wrap off lays the ghost text out again, on one row
			widget.setWordWrap(false);
			waitForAndAssertCondition(TIMEOUT, () -> assertEquals(List.of(words), previewRows(viewer)));
			widget.setWordWrap(true);
			waitForAndAssertCondition(TIMEOUT, () -> assertTrue(previewRows(viewer).size() > 1));
			processEvents(200);
			final List<String> rows = previewRows(viewer);

			// a bigger font takes more rows
			final Font original = widget.getFont();
			final FontData data = original.getFontData()[0];
			final var bigger = new Font(widget.getDisplay(), data.getName(), data.getHeight() * 2, SWT.NORMAL);
			try {
				widget.setFont(bigger);
				waitForAndAssertCondition(TIMEOUT, () -> assertTrue(previewRows(viewer).size() > rows.size(),
						previewRows(viewer) + " after " + rows));
			} finally {
				widget.setFont(original);
				waitForAndAssertCondition(TIMEOUT, () -> assertEquals(rows, previewRows(viewer)));
				bigger.dispose();
			}

			// accepted, the text wraps where the preview did
			final List<String> shown = previewRows(viewer);
			controller(viewer).accept();
			final var expected = new ArrayList<Integer>(List.of(0));
			int offset = "x = ".length();
			for (final String row : shown.subList(0, shown.size() - 1)) {
				offset += row.length();
				expected.add(offset);
			}
			assertEquals(expected, visualLineStarts(viewer, 0));
		} finally {
			widget.setWordWrap(false);
		}
	}

	@Test
	public void withWordWrapTheRestOfTheLineMovesWhenTheFirstLineGoesOnBelow(MockLanguageServerFactory factory)
			throws Exception {
		final String words = String.join(" ", Collections.nCopies(60, "word"));
		final ISourceViewer viewer = open(factory, "call(arg)\nnext\n", items(item(words)));
		final StyledText widget = viewer.getTextWidget();
		try {
			setCaret(viewer, 5);
			controller(viewer).trigger();
			waitForGhostText(viewer, words);
			waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());
			assertHidden(viewer, 5, 4, false);
			// laid out again: the first line moves from the anchor to the end of the line
			widget.setWordWrap(true);
			waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 5, 4, true));
			processEvents(200);
			final List<String> rows = previewRows(viewer);
			assertEquals(words + "arg)", String.join("", rows));

			controller(viewer).accept();
			waitForAndAssertCondition(TIMEOUT, () -> assertHidden(viewer, 5, words.length() + 4, false));
			final var expected = new ArrayList<Integer>(List.of(0));
			int offset = "call(".length();
			for (final String row : rows.subList(0, rows.size() - 1)) {
				offset += row.length();
				expected.add(offset);
			}
			assertEquals(expected, visualLineStarts(viewer, 0));
		} finally {
			widget.setWordWrap(false);
		}
	}

	@Test
	public void hugeSuggestionIsInsertedWhole(MockLanguageServerFactory factory) throws Exception {
		// the preview is truncated (see GhostTextModelTest), the insertion is not
		final var text = new StringBuilder();
		for (int i = 1; i <= 60; i++) {
			text.append("\nline ").append(i);
		}
		final ISourceViewer viewer = open(factory, "x\ny\n", items(item(text.toString())));
		setCaret(viewer, 1);
		controller(viewer).trigger();
		waitForGhost(viewer);
		controller(viewer).accept();
		assertEquals("x" + text + "\ny\n", viewer.getDocument().get());
	}

	@Test
	public void endOfDocumentReservesRoomBelowTheLastLine(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "abc", items(item("x\ny\nz")));
		final int margin = viewer.getTextWidget().getBottomMargin();
		setCaret(viewer, 3);
		controller(viewer).trigger();
		waitForGhost(viewer);
		assertEquals(List.of(new MiningSpec(Kind.END_OF_DOCUMENT, 3, 0, "x", List.of("y", "z"))),
				controller(viewer).getShownLayout());
		assertTrue(viewer.getTextWidget().getBottomMargin() > margin);

		controller(viewer).dismiss();
		assertEquals(margin, viewer.getTextWidget().getBottomMargin());
	}

	/** Asking to repaint what no paint reaches would keep the UI thread busy for good. */
	@Test
	public void endOfDocumentGhostTextBelowTheVisibleAreaIsNotRepaintedWithoutEnd(MockLanguageServerFactory factory)
			throws Exception {
		final var content = new StringBuilder();
		for (int i = 0; i < 200; i++) {
			content.append("line ").append(i).append('\n');
		}
		content.append("abc");
		final ISourceViewer viewer = open(factory, content.toString(), items(item("x" + "\nmore".repeat(20))));
		final int end = viewer.getDocument().getLength();
		setCaret(viewer, end);
		viewer.revealRange(end, 0);
		controller(viewer).trigger();
		waitForGhost(viewer);
		waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());
		processEvents(300);

		final var paints = new AtomicInteger();
		final Listener counter = event -> paints.incrementAndGet();
		viewer.getTextWidget().addListener(SWT.Paint, counter);
		try {
			processEvents(500);
		} finally {
			viewer.getTextWidget().removeListener(SWT.Paint, counter);
		}
		assertTrue(paints.get() < 10, "paints while nothing changes: " + paints.get());
	}

	@Test
	public void emptyDocument(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "", items(item("first line")));
		setCaret(viewer, 0);
		controller(viewer).trigger();
		waitForGhostText(viewer, "first line");
		waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());
		controller(viewer).accept();
		assertEquals("first line", viewer.getDocument().get());
	}

	@Test
	public void snippetsArePreviewedExpandedAndAcceptedInLinkedMode(MockLanguageServerFactory factory) throws Exception {
		final var snippet = new InlineCompletionItem();
		snippet.setInsertText(new StringValue(StringValueKind.SNIPPET, "foo(${1:a}, ${2:b})$0"));
		final ISourceViewer viewer = open(factory, "\n", items(snippet));
		setCaret(viewer, 0);
		controller(viewer).trigger();
		waitForGhostText(viewer, "foo(a, b)");
		controller(viewer).accept();
		final IDocument document = viewer.getDocument();
		assertEquals("foo(a, b)\n", document.get());
		assertTrue(LinkedModeModel.hasInstalledModel(document));
		// the first placeholder is selected
		assertEquals(new Point(4, 1), viewer.getSelectedRange());

		// typing a placeholder does not request inline completions
		final int requests = requestCount(factory);
		type(viewer, 'z');
		processEvents(300);
		assertEquals("foo(z, b)\n", document.get());
		assertEquals(requests, requestCount(factory));
	}

	@Test
	public void snippetVariablesArePreviewedAsTheyAreInserted(MockLanguageServerFactory factory) throws Exception {
		final var snippet = new InlineCompletionItem();
		snippet.setInsertText(new StringValue(StringValueKind.SNIPPET, "class ${TM_FILENAME_BASE}"));
		final ISourceViewer viewer = open(factory, "\n", items(snippet));
		setCaret(viewer, 0);
		controller(viewer).trigger();
		final GhostText ghost = waitForGhost(viewer);
		assertNotEquals("class ", ghost.text(), "the variable was not resolved");
		controller(viewer).accept();
		assertEquals(ghost.text() + "\n", viewer.getDocument().get());
	}

	@Test
	public void commandIsExecutedAfterAccept(MockLanguageServerFactory factory) throws Exception {
		final InlineCompletionItem withCommand = item(" world");
		withCommand.setCommand(new Command("after", MockLanguageServer.SUPPORTED_COMMAND_ID, List.of("arg")));
		final ISourceViewer viewer = open(factory, "hello\n", items(withCommand));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhost(viewer);
		controller(viewer).accept();
		final ExecuteCommandParams executed = server(factory).getWorkspaceService().getExecutedCommand().get(TIMEOUT,
				TimeUnit.MILLISECONDS);
		assertEquals(MockLanguageServer.SUPPORTED_COMMAND_ID, executed.getCommand());
		assertEquals("hello world\n", viewer.getDocument().get());
	}

	@Test
	public void acceptedTextIsNotTreatedAsTyping(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhost(viewer);
		final int requests = requestCount(factory);
		controller(viewer).accept();
		processEvents(300);
		assertEquals(requests, requestCount(factory), "accepting must not trigger an automatic request");
	}

	// ---- server latency: one request at a time, kept while the user types at the caret -----------------

	@Test
	public void typingThatDoesNotFitDropsTheReplyAndAsksAgain(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final CompletableFuture<Void> replies = holdReplies(factory);
		store.setValue(InlineCompletionPreferencePage.PREF_AUTOMATIC, false);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
		// typing that the reply cannot fit: it is not shown, and the request is repeated for the new text
		type(viewer, '!');
		replies.complete(null);
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 2);
		assertEquals(InlineCompletionTriggerKind.Invoked, lastRequest(factory).getContext().getTriggerKind());
		assertEquals(new Position(0, 6), lastRequest(factory).getPosition());
		final GhostText ghost = waitForGhost(viewer);
		assertEquals(6, ghost.anchor());
		assertEquals(0, inlineCancellations(factory), "typing does not cancel the request");
	}

	@Test
	public void typingThatFitsKeepsTheRequestAndShowsItsReplyShortened(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world"), item(" wide")));
		final CompletableFuture<Void> replies = holdReplies(factory);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
		// the automatic request this typing would trigger is held back
		type(viewer, " w");
		processEvents(100);
		assertEquals(1, requestCount(factory));
		replies.complete(null);
		waitForGhostText(viewer, "orld");
		// the reply is still the explicit request's: all its alternatives are there
		controller(viewer).cycle(true);
		waitForGhostText(viewer, "ide");
		assertEquals(1, requestCount(factory), "one request at a time");
		assertEquals(0, inlineCancellations(factory));
	}

	@Test
	public void movingTheCaretCancelsTheRequest(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\nsecond\n", items(item(" world")));
		final CompletableFuture<Void> replies = holdReplies(factory);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
		setCaret(viewer, 8);
		waitForAndAssertCondition(TIMEOUT, () -> inlineCancellations(factory) == 1);
		replies.complete(null);
		processEvents(200);
		assertNull(controller(viewer).getShownGhostText());
	}

	@Test
	public void anExplicitRequestReplacesOneTheServerDoesNotAnswer(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final CompletableFuture<Void> replies = holdReplies(factory);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 2);
		waitForAndAssertCondition(TIMEOUT, () -> inlineCancellations(factory) == 1);
		replies.complete(null);
	}

	@Test
	public void aRequestTheServerDoesNotAnswerStopsHoldingBackAutomaticRequests(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final CompletableFuture<Void> replies = holdReplies(factory);
		setCaret(viewer, 5);
		type(viewer, 'x');
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
		type(viewer, 'x');
		processEvents(100);
		assertEquals(1, requestCount(factory), "held back while the request is young");

		controller(viewer).setStaleRequestTimeout(0);
		type(viewer, 'x');
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 2);
		replies.complete(null);
	}

	@Test
	public void typingFasterThanTheRequestDelaySendsOneRequest(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "x\n", items(item(" world")));
		store.setValue(InlineCompletionPreferencePage.PREF_DELAY, 1_000);
		setCaret(viewer, 1);
		type(viewer, " we say hello wo");
		waitForGhost(viewer);
		assertEquals(1, requestCount(factory));
	}

	@Test
	public void closingTheEditorDuringARequestIsHarmless(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final CompletableFuture<Void> replies = holdReplies(factory);
		setCaret(viewer, 5);
		final InlineCompletionController controller = controller(viewer);
		controller.trigger();
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
		final var errors = new AtomicInteger();
		final ILogListener listener = (status, plugin) -> {
			if (status.getSeverity() == IStatus.ERROR) {
				errors.incrementAndGet();
			}
		};
		Platform.addLogListener(listener);
		try {
			final IEditorPart editor = TestUtils.getActiveEditor();
			TestUtils.closeEditor(editor, false);
			waitForAndAssertCondition(TIMEOUT, () -> inlineCancellations(factory) == 1);
			replies.complete(null);
			processEvents(200);
			assertEquals(0, errors.get());
			assertNull(InlineCompletionController.get(viewer));
		} finally {
			Platform.removeLogListener(listener);
		}
	}

	// ---- other code minings ----------------------------------------------------------------------------

	@Test
	public void inlayHintAtTheCaretMakesRoomForTheGhost(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "lspt", "hello\n", () -> {
			final ServerCapabilities capabilities = capabilities();
			capabilities.setInlayHintProvider(true);
			return capabilities;
		}, items(item(" world")));
		server(factory).getTextDocumentService()
				.setInlayHints(List.of(new InlayHint(new Position(0, 5), Either.forLeft(": hint"))));
		final var inlayHints = new InlayHintProvider();
		assertEquals(1, minings(inlayHints, viewer).size());

		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhost(viewer);
		assertEquals(0, minings(inlayHints, viewer).size(), "the hint at the caret is left out");

		controller(viewer).dismiss();
		assertEquals(1, minings(inlayHints, viewer).size(), "the hint comes back");
	}

	@Test
	public void inlayHintAtTheCaretMakesRoomAlsoWhenTheGhostContinuesBelowTheLastLine(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "lspt", "hello\n", () -> {
			final ServerCapabilities capabilities = capabilities();
			capabilities.setInlayHintProvider(true);
			return capabilities;
		}, items(item("x\ny")));
		server(factory).getTextDocumentService()
				.setInlayHints(List.of(new InlayHint(new Position(0, 5), Either.forLeft(": hint"))));
		final var inlayHints = new InlayHintProvider();
		assertEquals(1, minings(inlayHints, viewer).size());

		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhost(viewer);
		assertEquals(List.of(new MiningSpec(Kind.CONTENT, 5, 1, "x", List.of()),
				new MiningSpec(Kind.END_OF_DOCUMENT, 6, 0, "", List.of("y"))), controller(viewer).getShownLayout());
		assertEquals(0, minings(inlayHints, viewer).size(), "the hint at the caret is left out");
	}

	@Test
	public void codeLensOnTheLineTheGhostContinuesAboveIsLeftOut(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "if x:\npass\n", items(item("\n    y()")));
		server(factory).setCodeLens(List.of(new CodeLens(new Range(new Position(1, 0), new Position(1, 1)),
				new Command("lens", "dummy"), null)));
		final var codeLenses = new CodeLensProvider();
		assertEquals(1, minings(codeLenses, viewer).size());

		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhost(viewer);
		assertEquals(0, minings(codeLenses, viewer).size());
		controller(viewer).dismiss();
		assertEquals(1, minings(codeLenses, viewer).size());
	}

	@Test
	public void codeMiningsAreReusedUntilTheDocumentChangesOrTheServerAsksForARefresh(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, "lspt", "hello\n", () -> {
			final ServerCapabilities capabilities = capabilities();
			capabilities.setInlayHintProvider(true);
			return capabilities;
		}, items(item(" world")));
		final AtomicInteger inlayHintRequests = server(factory).getTextDocumentService().inlayHintRequests;
		waitForAndAssertCondition(TIMEOUT, () -> inlayHintRequests.get() > 0);
		processEvents(300);
		final int initial = inlayHintRequests.get();

		// showing and dismissing ghost text updates the code minings of an unchanged document
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForGhost(viewer);
		controller(viewer).dismiss();
		processEvents(300);
		assertEquals(initial, inlayHintRequests.get(), "the document did not change: inlay hints are reused");

		// the client refreshes on the UI thread: not waiting for its reply here
		server(factory).getRemoteProxy().refreshInlayHints();
		waitForAndAssertCondition(TIMEOUT, () -> inlayHintRequests.get() > initial);

		final int refreshed = inlayHintRequests.get();
		viewer.getDocument().replace(0, 0, "x");
		((ISourceViewerExtension5) viewer).updateCodeMinings();
		waitForAndAssertCondition(TIMEOUT, () -> inlayHintRequests.get() > refreshed);
	}

	private static List<? extends ICodeMining> minings(ICodeMiningProvider provider, ISourceViewer viewer)
			throws Exception {
		final var future = provider.provideCodeMinings(viewer, new NullProgressMonitor());
		assertNotNull(future);
		return future.get(TIMEOUT, TimeUnit.MILLISECONDS);
	}

	// ---- enablement and saving -----------------------------------------------------------------------

	@Test
	public void nothingIsRequestedWhileDisabled(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		setCaret(viewer, 5);
		SwitchableEnablement.enabled = false;
		try {
			type(viewer, 'x');
			controller(viewer).trigger();
			processEvents(300);
			assertEquals(0, requestCount(factory));
		} finally {
			SwitchableEnablement.enabled = true;
		}
		controller(viewer).trigger();
		waitForGhostText(viewer, " world");
	}

	@Test
	public void editsMadeWhileSavingDoNotTriggerARequest(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items());
		setCaret(viewer, 5);
		store.setValue(InlineCompletionPreferencePage.PREF_AUTOMATIC, false);
		type(viewer, 'x');
		processEvents(100);
		store.setValue(InlineCompletionPreferencePage.PREF_AUTOMATIC, true);
		final var formatOnSave = new FileBufferListenerAdapter() {
			@Override
			public void stateChanging(IFileBuffer buffer) {
				try {
					viewer.getDocument().replace(viewer.getSelectedRange().x, 0, "y");
				} catch (BadLocationException e) {
					throw new IllegalStateException(e);
				}
			}
		};
		FileBuffers.getTextFileBufferManager().addFileBufferListener(formatOnSave);
		try {
			castNonNull(UI.getActiveTextEditor()).doSave(null);
		} finally {
			FileBuffers.getTextFileBufferManager().removeFileBufferListener(formatOnSave);
		}
		assertTrue(viewer.getDocument().get().contains("y"), "the edit was made");
		processEvents(300);
		assertEquals(0, requestCount(factory));
		type(viewer, 'z');
		waitForAndAssertCondition(TIMEOUT, () -> requestCount(factory) == 1);
	}
}
