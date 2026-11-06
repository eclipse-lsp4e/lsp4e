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
import static org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEventKind.*;
import static org.eclipse.lsp4e.test.inlinecompletion.InlineCompletionTest.*;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEvent;
import org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEventKind;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.ui.InlineCompletionPreferencePage;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionList;
import org.eclipse.lsp4j.InlineCompletionParams;
import org.eclipse.lsp4j.InlineCompletionTriggerKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests of the events the inline completion engine fires, as the contributed listeners receive them.
 */
public class InlineCompletionEventsTest extends AbstractTestWithProject {

	private static final int TIMEOUT = 5_000;

	private final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
	/** The generic editor would open the completion popup while typing, see {@link InlineCompletionTest}. */
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

	private ISourceViewer open(MockLanguageServerFactory factory, String content,
			Function<InlineCompletionParams, Either<List<InlineCompletionItem>, InlineCompletionList>> responder)
			throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		factory.withConfiguration((index, server) -> server.getTextDocumentService().setInlineCompletionResponder(responder));
		return openEditor(TestUtils.createUniqueTestFile(project, content));
	}

	private static List<InlineCompletionEvent> events(URI uri) {
		return RecordingEventListener.EVENTS.stream().filter(event -> uri.equals(event.uri())).toList();
	}

	private static URI uri(ISourceViewer viewer) {
		final URI uri = LSPEclipseUtils.toUri(viewer.getDocument());
		assertNotNull(uri);
		return uri;
	}

	private static List<InlineCompletionEventKind> kinds(URI uri) {
		return events(uri).stream().map(InlineCompletionEvent::kind).toList();
	}

	private static void waitForKinds(URI uri, InlineCompletionEventKind... kinds) {
		waitForAndAssertCondition("events " + List.of(kinds), TIMEOUT, () -> assertEquals(List.of(kinds), kinds(uri)));
	}

	private static InlineCompletionEvent last(URI uri) {
		return events(uri).getLast();
	}

	/** Shows the ghost text and waits until the view is reported. */
	private static void showAndView(ISourceViewer viewer, String text) {
		controller(viewer).trigger();
		waitForGhostText(viewer, text);
		waitForKinds(uri(viewer), FETCH_FIRST, VIEW);
	}

	@Test
	public void aRequestIsFetchedViewedAndAcceptedUnderOneUuid(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		showAndView(viewer, " world");
		controller(viewer).accept();
		waitForKinds(uri, FETCH_FIRST, VIEW, ACCEPT_FULL);
		assertEquals("hello world\n", viewer.getDocument().get());

		final List<InlineCompletionEvent> events = events(uri);
		assertEquals(1, events.stream().map(InlineCompletionEvent::uuid).distinct().count());
		final InlineCompletionEvent fetch = events.get(0);
		assertNull(fetch.text());
		assertNull(fetch.elapsedTime());
		final InlineCompletionEvent view = events.get(1);
		assertNull(view.text());
		assertNotNull(view.elapsedTime());
		final InlineCompletionEvent accept = events.get(2);
		assertEquals(" world", accept.text());
		assertNotNull(accept.elapsedTime());
	}

	@Test
	public void acceptingAfterTypingReportsWhatIsInserted(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		showAndView(viewer, " world");
		type(viewer, ' ');
		waitForGhostText(viewer, "world");
		controller(viewer).accept();
		waitForKinds(uri, FETCH_FIRST, VIEW, ACCEPT_FULL);
		assertEquals("world", last(uri).text());
	}

	@Test
	public void dismissingReportsTheWholeSuggestion(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		showAndView(viewer, " world");
		type(viewer, ' ');
		waitForGhostText(viewer, "world");
		type(viewer, SWT.ESC);
		waitForKinds(uri, FETCH_FIRST, VIEW, DISMISS);
		assertEquals(" world", last(uri).text());
		assertNotNull(last(uri).elapsedTime());
	}

	@Test
	public void escapeWithNothingShownIsNotADismissal(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items());
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		waitForKinds(uri, FETCH_FIRST);
		type(viewer, SWT.ESC);
		processEvents(200);
		assertEquals(List.of(FETCH_FIRST), kinds(uri));
	}

	@Test
	public void losingTheFocusDisablesWhatIsShown(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		showAndView(viewer, " world");
		viewer.getTextWidget().notifyListeners(SWT.FocusOut, new Event());
		waitForKinds(uri, FETCH_FIRST, VIEW, DISABLED);
		assertNull(last(uri).text());
		assertNotNull(last(uri).elapsedTime());

		viewer.getTextWidget().notifyListeners(SWT.FocusOut, new Event());
		processEvents(200);
		assertEquals(List.of(FETCH_FIRST, VIEW, DISABLED), kinds(uri), "nothing shown any more");
	}

	@Test
	public void closingTheEditorDisablesWhatIsShownOnce(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" world")));
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		showAndView(viewer, " world");
		final IEditorPart editor = UI.getActivePage().getActiveEditor();
		UI.getActivePage().closeEditor(editor, false);
		waitForKinds(uri, FETCH_FIRST, VIEW, DISABLED);
		processEvents(200);
		assertEquals(List.of(FETCH_FIRST, VIEW, DISABLED), kinds(uri));
	}

	@Test
	public void movingTheCaretAwayHidesWithoutAnEvent(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\nsecond\n", items(item(" world")));
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		showAndView(viewer, " world");
		setCaret(viewer, 8);
		waitForNoGhost(viewer);
		processEvents(200);
		assertEquals(List.of(FETCH_FIRST, VIEW), kinds(uri));
	}

	@Test
	public void askingForAlternativesKeepsTheUuidAndReportsTheirView(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n",
				params -> Either.forLeft(params.getContext().getTriggerKind() == InlineCompletionTriggerKind.Automatic
						? List.of(item(" one"))
						: List.of(item(" one"), item(" two"))));
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		// typed at the caret, so that the request is an automatic one
		type(viewer, 'x');
		waitForGhostText(viewer, " one");
		waitForKinds(uri, FETCH_FIRST, VIEW);

		controller(viewer).cycle(true);
		waitForAndAssertCondition(TIMEOUT, () -> assertEquals(2, castNonNull(controller(viewer).getSession()).size()));
		controller(viewer).cycle(true);
		waitForGhostText(viewer, " two");
		waitForKinds(uri, FETCH_FIRST, VIEW, FETCH_ADDITIONAL, VIEW);
		assertEquals(1, events(uri).stream().map(InlineCompletionEvent::uuid).distinct().count());
	}

	@Test
	public void aServerWithoutTheCapabilityFetchesNothing(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(MockLanguageServer::defaultServerCapabilities);
		final ISourceViewer viewer = openEditor(TestUtils.createUniqueTestFile(project, "hello\n"));
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		controller(viewer).trigger();
		type(viewer, 'x');
		processEvents(300);
		assertEquals(List.of(), kinds(uri));
	}

	@Test
	public void partsAreReportedAndTheTimeSinceTheViewOnlyOnce(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, "hello\n", items(item(" big\nworld")));
		final URI uri = uri(viewer);
		setCaret(viewer, 5);
		showAndView(viewer, " big\nworld");

		controller(viewer).acceptNextWord();
		waitForKinds(uri, FETCH_FIRST, VIEW, ACCEPT_WORD);
		assertEquals(" ", last(uri).text());
		assertNotNull(last(uri).elapsedTime());
		waitForGhostText(viewer, "big\nworld");

		controller(viewer).acceptNextLine();
		waitForKinds(uri, FETCH_FIRST, VIEW, ACCEPT_WORD, ACCEPT_LINE);
		assertEquals("big\n", last(uri).text());
		assertNull(last(uri).elapsedTime());
		waitForGhostText(viewer, "world");

		controller(viewer).acceptNextWord();
		waitForKinds(uri, FETCH_FIRST, VIEW, ACCEPT_WORD, ACCEPT_LINE, ACCEPT_WORD);
		assertEquals("world", last(uri).text());
		assertNull(last(uri).elapsedTime());
		assertEquals("hello big\nworld\n", viewer.getDocument().get());
		assertEquals(1, events(uri).stream().map(InlineCompletionEvent::uuid).distinct().count());
	}
}
