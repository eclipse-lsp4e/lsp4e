/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.inlinecompletion;

import static org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionStatus.State.*;
import static org.eclipse.lsp4e.test.inlinecompletion.InlineCompletionTest.*;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionStatus;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.InlineCompletionItem;
import org.eclipse.lsp4j.InlineCompletionList;
import org.eclipse.lsp4j.InlineCompletionParams;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.swt.SWT;
import org.junit.jupiter.api.Test;

/**
 * Tests of how the inline completion engine drives the {@link InlineCompletionStatus}.
 */
public class InlineCompletionStatusTest extends AbstractTestWithProject {

	private static final int TIMEOUT = 5_000;

	private final InlineCompletionStatus status = InlineCompletionStatus.getInstance();

	private ISourceViewer open(MockLanguageServerFactory factory,
			Function<InlineCompletionParams, Either<List<InlineCompletionItem>, InlineCompletionList>> responder)
			throws Exception {
		factory.withCapabilities(InlineCompletionTest::capabilities);
		factory.withConfiguration((index, server) -> server.getTextDocumentService().setInlineCompletionResponder(responder));
		final ISourceViewer viewer = openEditor(TestUtils.createUniqueTestFile(project, "hello\n"));
		setCaret(viewer, 5);
		return viewer;
	}

	private void waitForStatus(InlineCompletionStatus.State state) {
		waitForAndAssertCondition("status " + state, TIMEOUT, () -> assertEquals(state, status.getState()));
	}

	private static Either<List<InlineCompletionItem>, InlineCompletionList> failure(ResponseErrorCode code) {
		throw new ResponseErrorException(new ResponseError(code, "mock inline completion failure", null));
	}

	@Test
	public void readyOnceAServerHasTheCapabilityAndFetchingDuringARequest(MockLanguageServerFactory factory)
			throws Exception {
		final ISourceViewer viewer = open(factory, items(item(" world")));
		waitForStatus(READY);
		final CompletableFuture<Void> replies = holdReplies(factory);
		controller(viewer).trigger();
		waitForStatus(FETCHING);
		replies.complete(null);
		waitForStatus(READY);
		waitForGhostText(viewer, " world");
	}

	@Test
	public void aServerWithoutTheCapabilityStaysInitializing(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(MockLanguageServer::defaultServerCapabilities);
		final ISourceViewer viewer = openEditor(TestUtils.createUniqueTestFile(project, "hello\n"));
		controller(viewer).trigger();
		processEvents(300);
		assertEquals(INITIALIZING, status.getState());
	}

	@Test
	public void aFailedRequestIsAnErrorUntilOneSucceeds(MockLanguageServerFactory factory) throws Exception {
		final var fail = new AtomicBoolean(true);
		final ISourceViewer viewer = open(factory,
				params -> fail.get() ? failure(ResponseErrorCode.InternalError) : Either.forLeft(List.of(item(" world"))));
		waitForStatus(READY);
		controller(viewer).trigger();
		waitForStatus(ERROR);

		fail.set(false);
		final CompletableFuture<Void> replies = holdReplies(factory);
		controller(viewer).trigger();
		waitForStatus(FETCHING);
		replies.complete(null);
		waitForStatus(READY);
	}

	@Test
	public void anOutdatedRequestIsNoError(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, params -> failure(ResponseErrorCode.ContentModified));
		waitForStatus(READY);
		final var requests = server(factory).getTextDocumentService().inlineCompletionRequests;
		controller(viewer).trigger();
		waitForAndAssertCondition(TIMEOUT, () -> !requests.isEmpty());
		processEvents(300);
		assertEquals(READY, status.getState());
	}

	@Test
	public void cancellingEndsFetching(MockLanguageServerFactory factory) throws Exception {
		final ISourceViewer viewer = open(factory, items(item(" world")));
		waitForStatus(READY);
		final CompletableFuture<Void> replies = holdReplies(factory);
		controller(viewer).trigger();
		waitForStatus(FETCHING);
		type(viewer, SWT.ESC);
		waitForStatus(READY);
		replies.complete(null);
		processEvents(200);
		assertEquals(READY, status.getState());
	}

	@Test
	public void closingTheEditorLeavesNothingToBeReadyFor(MockLanguageServerFactory factory) throws Exception {
		open(factory, items());
		waitForStatus(READY);
		UI.getActivePage().closeEditor(UI.getActivePage().getActiveEditor(), false);
		waitForStatus(INITIALIZING);
	}

	@Test
	public void listenersAreNotifiedOfChangesOnly() {
		final var first = new AtomicInteger();
		final var second = new AtomicInteger();
		final Runnable firstListener = first::incrementAndGet;
		final Runnable secondListener = second::incrementAndGet;
		final InlineCompletionStatus.State before = status.getState();
		status.setState(INITIALIZING);
		status.addListener(firstListener);
		status.addListener(secondListener);
		try {
			status.setState(INITIALIZING);
			assertEquals(0, first.get());
			status.setState(READY);
			assertEquals(1, first.get());
			assertEquals(1, second.get());
			status.removeListener(firstListener);
			status.setState(ERROR);
			assertEquals(1, first.get());
			assertEquals(2, second.get());
		} finally {
			status.removeListener(firstListener);
			status.removeListener(secondListener);
			status.setState(before);
		}
	}
}
