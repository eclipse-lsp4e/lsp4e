/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.inlinecompletion;

import static org.eclipse.lsp4e.test.inlinecompletion.InlineCompletionTest.*;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.LSPMethods;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.ui.InlineCompletionPreferencePage;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SelectedCompletionInfo;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests of inline completions requested while the completion popup is open, with the
 * selected proposal as {@code selectedCompletionInfo}.
 */
public class InlineCompletionWithCompletionPopupTest extends AbstractTestWithProject {

	private static final int TIMEOUT = 5_000;

	private final IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();

	@BeforeEach
	public void setUpPreferences() {
		store.setValue(InlineCompletionPreferencePage.PREF_AUTOMATIC, true);
		store.setValue(InlineCompletionPreferencePage.PREF_DELAY, 20);
	}

	@AfterEach
	public void resetPreferences() {
		store.setToDefault(InlineCompletionPreferencePage.PREF_AUTOMATIC);
		store.setToDefault(InlineCompletionPreferencePage.PREF_DELAY);
	}

	private static CompletionItem completion(String label, String newText, int startCharacter, int endCharacter) {
		final var item = new CompletionItem(label);
		item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(0, startCharacter), new Position(0, endCharacter)), newText)));
		return item;
	}

	/** Answers like the LSP specification's example: extends the selected ".log" into ".log()". */
	private static List<InlineCompletionItem> respond(InlineCompletionParams params) {
		final SelectedCompletionInfo selected = params.getContext().getSelectedCompletionInfo();
		if (selected == null) {
			return List.of(item("// plain", 0, 8, 8));
		}
		return List.of(item(selected.getText() + "()", selected.getRange().getStart().getLine(),
				selected.getRange().getStart().getCharacter(), selected.getRange().getEnd().getCharacter()));
	}

	private ISourceViewer openWithPopup(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		factory.withConfiguration((index, server) -> {
			server.setCompletionList(new CompletionList(false, List.of(completion(".log", ".log", 7, 8))));
			server.getTextDocumentService().setInlineCompletionResponder(params -> Either.forLeft(respond(params)));
		});
		final ISourceViewer viewer = openEditor(TestUtils.createUniqueTestFile(project, "console.\n"));
		setCaret(viewer, 8);
		((ITextOperationTarget) viewer).doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS);
		return viewer;
	}

	@Test
	public void selectedProposalIsSentAndExtended(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = openWithPopup(factory);
		waitForAndAssertCondition(TIMEOUT, () -> factory.getServer().getTextDocumentService().inlineCompletionRequests
				.stream().anyMatch(params -> params.getContext().getSelectedCompletionInfo() != null));
		final SelectedCompletionInfo selected = factory.getServer().getTextDocumentService().inlineCompletionRequests
				.stream().map(params -> params.getContext().getSelectedCompletionInfo()).filter(info -> info != null)
				.findFirst().orElseThrow();
		assertEquals(".log", selected.getText());
		assertEquals(new Range(new Position(0, 7), new Position(0, 8)), selected.getRange());
		waitForGhostText(viewer, "log()");
	}

	@Test
	public void applyingTheProposalKeepsWhatTheGhostAddsToIt(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = openWithPopup(factory);
		waitForGhostText(viewer, "log()");
		// Enter applies the selected proposal
		type(viewer, SWT.CR);
		waitForAndAssertCondition(TIMEOUT, () -> assertEquals("console.log\n", viewer.getDocument().get()));
		waitForGhostText(viewer, "()");
		waitForAndAssertCondition(TIMEOUT, () -> controller(viewer).isGhostPainted());
		type(viewer, SWT.TAB);
		assertEquals("console.log()\n", viewer.getDocument().get());
	}

	@Test
	public void closingThePopupDropsTheSelectionBoundGhost(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = openWithPopup(factory);
		waitForGhostText(viewer, "log()");
		type(viewer, SWT.ESC);
		// a plain request follows, without the selected proposal
		waitForGhostText(viewer, "// plain");
		final var requests = List.copyOf(factory.getServer().getTextDocumentService().inlineCompletionRequests);
		assertNull(requests.get(requests.size() - 1).getContext().getSelectedCompletionInfo());
	}

	@Test
	public void closingThePopupDuringARequestDropsItsReply(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		final var replies = new CompletableFuture<Void>();
		factory.withConfiguration((index, server) -> {
			server.setCompletionList(new CompletionList(false, List.of(completion(".log", ".log", 7, 8))));
			server.getTextDocumentService().setInlineCompletionResponder(params -> Either.forLeft(respond(params)));
			server.getTextDocumentService().holdInlineCompletionsUntil(replies);
		});
		final ISourceViewer viewer = openEditor(TestUtils.createUniqueTestFile(project, "console.\n"));
		setCaret(viewer, 8);
		((ITextOperationTarget) viewer).doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS);
		final var requests = factory.getServer().getTextDocumentService().inlineCompletionRequests;
		waitForAndAssertCondition(TIMEOUT, () -> requests.stream()
				.anyMatch(params -> params.getContext().getSelectedCompletionInfo() != null));

		type(viewer, SWT.ESC);
		waitForAndAssertCondition(TIMEOUT,
				() -> factory.cancelledRequests(LSPMethods.TEXT_DOCUMENT_INLINE_COMPLETION) == 1);
		replies.complete(null);
		// a plain request follows, without the selected proposal
		waitForGhostText(viewer, "// plain");
	}

	@Test
	public void selectingAnotherProposalAsksForItsInlineCompletion(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		factory.withConfiguration((index, server) -> {
			server.setCompletionList(new CompletionList(false,
					List.of(completion(".log", ".log", 7, 8), completion(".warn", ".warn", 7, 8))));
			server.getTextDocumentService().setInlineCompletionResponder(params -> Either.forLeft(respond(params)));
		});
		final ISourceViewer viewer = openEditor(TestUtils.createUniqueTestFile(project, "console.\n"));
		setCaret(viewer, 8);
		((ITextOperationTarget) viewer).doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS);
		waitForGhostText(viewer, "log()");

		final StyledText widget = viewer.getTextWidget();
		final var down = new Event();
		down.widget = widget;
		down.keyCode = SWT.ARROW_DOWN;
		widget.notifyListeners(SWT.KeyDown, down);
		waitForGhostText(viewer, "warn()");
	}

	@Test
	public void applyingTheProposalKeepsTheInlineCompletionsOfAllServers(MockLanguageServerFactory factory)
			throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		factory.withConfiguration((index, server) -> {
			server.setCompletionList(new CompletionList(false, List.of(completion(".log", ".log", 7, 8))));
			server.getTextDocumentService().setInlineCompletionResponder(params -> {
				final SelectedCompletionInfo selected = params.getContext().getSelectedCompletionInfo();
				return Either.forLeft(selected == null ? List.of()
						: List.of(item(selected.getText() + "(" + index + ")", 0, 7, 8)));
			});
		});
		final ISourceViewer viewer = openEditor(TestUtils.createUniqueTestFile(project, "lsptmultils", "console.\n"));
		waitForAndAssertCondition(TIMEOUT, () -> factory.getServerCount() == 2);
		setCaret(viewer, 8);
		((ITextOperationTarget) viewer).doOperation(ISourceViewer.CONTENTASSIST_PROPOSALS);
		waitForAndAssertCondition(TIMEOUT, () -> {
			final var session = controller(viewer).getSession();
			assertNotNull(session);
			assertEquals(2, session.size());
		});
		type(viewer, SWT.CR);
		waitForAndAssertCondition(TIMEOUT, () -> assertEquals("console.log\n", viewer.getDocument().get()));
		waitForAndAssertCondition(TIMEOUT, () -> {
			final var session = controller(viewer).getSession();
			assertNotNull(session);
			assertEquals(2, session.size());
		});
	}
}
