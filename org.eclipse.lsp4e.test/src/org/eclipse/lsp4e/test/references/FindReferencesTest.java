/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Sebastian Thomschke (Vegard IT GmbH) - refactor and fix erratic test failures
 *******************************************************************************/
package org.eclipse.lsp4e.test.references;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.lsp4e.internal.Pair;
import org.eclipse.lsp4e.operations.references.LSFindReferences;
import org.eclipse.lsp4e.operations.references.LSSearchResult;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.search.internal.ui.text.FileMatch;
import org.eclipse.search.ui.IQueryListener;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.NewSearchUI;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.monitoring.EventLoopMonitorThread;
import org.eclipse.ui.monitoring.IUiFreezeEventLogger;
import org.eclipse.ui.monitoring.UiFreezeEvent;
import org.eclipse.ui.services.IEvaluationService;
import org.eclipse.ui.tests.harness.util.DisplayHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("restriction")
public class FindReferencesTest extends AbstractTestWithProject {

	public static final class UiFreezeEventLogger implements IUiFreezeEventLogger {

		static volatile UiFreezeEventLogger INSTANCE;

		final List<UiFreezeEvent> events = new Vector<>();

		public UiFreezeEventLogger() {
			INSTANCE = this;
		}

		@Override
		public void log(UiFreezeEvent event) {
			System.err.println(event);
			events.add(event);
		}
	}

	@BeforeEach
	public void setUp() throws Exception {
		ensureSearchResultViewIsClosed();

		final var testFile = TestUtils.createUniqueTestFile(project, "word1 word2\nword3 word2");
		var textViewer = TestUtils.openTextViewer(testFile);
		DisplayHelper.sleep(textViewer.getTextWidget().getDisplay(), 2_000); // Give some time to the editor to update
		MockLanguageServer.INSTANCE.getTextDocumentService().setMockReferences(
				new Location(testFile.getLocationURI().toString(), new Range(new Position(0, 6), new Position(0, 11))),
				new Location(testFile.getLocationURI().toString(), new Range(new Position(1, 6), new Position(1, 11))));
	}

	@AfterEach
	public void tearDown() {
		ensureSearchResultViewIsClosed();
	}

	@Test
	public void testFindReferences() throws Exception {
		final var handler = new LSFindReferences();
		final var evaluationService = PlatformUI.getWorkbench().getService(IEvaluationService.class);
		final var searchResultListener = registerSearchResultListener();
		handler.execute(new ExecutionEvent(null, new HashMap<>(), null, evaluationService.getCurrentState()));

		waitForAndAssertSearchResult(searchResultListener, 0, 2_000);
	}

	@Test
	public void testFindReferencesIsNonBlocking() throws Exception {
		final int uiFreezeThreshold = 300;
		final int findReferencesFakeDuration = uiFreezeThreshold * 5;

		assertNull(UiFreezeEventLogger.INSTANCE);
		final var uiFreezeMonitor = initFreezeMonitor(uiFreezeThreshold);
		waitForAndAssertCondition("UiFreezeEventLogger.INSTANCE is null", 2_000,
				() -> UiFreezeEventLogger.INSTANCE != null);

		MockLanguageServer.INSTANCE.setTimeToProceedQueries(findReferencesFakeDuration);
		try {
			final var handler = new LSFindReferences();
			final var evaluationService = PlatformUI.getWorkbench().getService(IEvaluationService.class);
			final var searchResultListener = registerSearchResultListener();

			long startTime = System.currentTimeMillis();
			handler.execute(new ExecutionEvent(null, new HashMap<>(), null, evaluationService.getCurrentState()));
			long executionTime = System.currentTimeMillis() - startTime;

			assertTrue(executionTime < uiFreezeThreshold,
					handler.getClass().getSimpleName() + ".execute(...) blocks UI for " + executionTime
							+ "ms. Acceptable is <" + uiFreezeThreshold + "ms");

			waitForAndAssertSearchResult(searchResultListener, findReferencesFakeDuration,
					findReferencesFakeDuration + 1_000);
		} finally {
			MockLanguageServer.INSTANCE.setTimeToProceedQueries(0);
			uiFreezeMonitor.shutdown();
		}

		final var uiFreezeCount = UiFreezeEventLogger.INSTANCE.events.size();
		assertEquals(0, uiFreezeCount, "UI Thread was frozen " + uiFreezeCount + " times for more than " + uiFreezeThreshold + "ms");
	}

	private EventLoopMonitorThread initFreezeMonitor(int uiFreezeThreshold) {
		final var args = new EventLoopMonitorThread.Parameters();
		args.longEventWarningThreshold = uiFreezeThreshold;
		args.longEventErrorThreshold = uiFreezeThreshold;
		args.deadlockThreshold = 30_000;
		args.uiThreadFilter = "";
		args.noninterestingThreadFilter = "sun.*,java.*,jdk.internal.*";
		final var monitor = new EventLoopMonitorThread(args);
		monitor.start();
		return monitor;
	}

	private void ensureSearchResultViewIsClosed() {
		final var searchPart = NewSearchUI.getSearchResultView();
		if (searchPart != null) {
			searchPart.getViewSite().getPage().hideView(searchPart);
		}
	}

	private CompletableFuture<Pair<ISearchResult, Long>> registerSearchResultListener() {
		final var future = new CompletableFuture<Pair<ISearchResult, Long>>();
		final var startTimes = new HashMap<ISearchQuery, Long>();

		NewSearchUI.addQueryListener(new IQueryListener() {
			@Override
			public void queryAdded(ISearchQuery q) {
			}

			@Override
			public void queryRemoved(ISearchQuery q) {
			}

			@Override
			public void queryStarting(ISearchQuery q) {
				startTimes.put(q, System.currentTimeMillis());
			}

			@Override
			public void queryFinished(ISearchQuery q) {
				future.complete(Pair.of(q.getSearchResult(), System.currentTimeMillis() - startTimes.get(q)));
			}
		});
		return future;
	}

	private void waitForAndAssertSearchResult(CompletableFuture<Pair<ISearchResult, Long>> searchResultListener,
			int min_time_ms, int max_time_ms) {
		final var startAt = System.currentTimeMillis();
		final var searchDuration = new AtomicLong(-1);

		waitForAndAssertCondition(max_time_ms, () -> {
			final var searchResult = searchResultListener.getNow(null);
			assertNotNull(searchResult, "No search query was executed");

			if (searchResult.first() instanceof LSSearchResult lsSearchResult) {
				final long now = System.currentTimeMillis();
				assertEquals(2, lsSearchResult.getMatchCount());
				final var file = lsSearchResult.getElements()[0];
				final var match1 = lsSearchResult.getMatches(file)[0];
				assertEquals(6, match1.getOffset());
				assertEquals(5, match1.getLength());
				if(match1 instanceof FileMatch fileMatch) {
					assertEquals(1, fileMatch.getLineElement().getLine());
				}

				final var match2 = lsSearchResult.getMatches(file)[1];
				assertEquals(18, match2.getOffset());
				assertEquals(5, match2.getLength());
				if(match2 instanceof FileMatch fileMatch) {
					assertEquals(2, fileMatch.getLineElement().getLine());
				}

				if (searchDuration.get() < 0) {
					searchDuration.set(now - startAt);
				}
				// this is to ensure that the simulation of a slow LS actually works
				assertTrue(searchDuration.get() >= min_time_ms, "Search result returned too early!");

				assertNotNull(NewSearchUI.getSearchResultView(), "Search result view is not shown");
			} else {
				fail("Search result " + searchResult + " is not of expected type LSSearchResult");
			}
			return true;
		});
	}
}
