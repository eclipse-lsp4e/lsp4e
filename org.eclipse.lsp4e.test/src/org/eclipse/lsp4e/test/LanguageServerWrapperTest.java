/*******************************************************************************
 * Copyright (c) 2019 SAP SE and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Markus Ofterdinger (SAP SE) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockConnectionProviderMultiRootFolders;
import org.eclipse.ui.IEditorPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LanguageServerWrapperTest extends AbstractTestWithProject {

	private IProject project2;

	@BeforeEach
	public void setUp() throws Exception {
		project2 = TestUtils.createProject("LanguageServerWrapperTestProject2" + System.currentTimeMillis());
	}

	@Test
	public void testConnect() throws Exception {
		IFile testFile1 = TestUtils.createFile(project, "shouldUseExtension.lsptWithMultiRoot", "");
		IFile testFile2 = TestUtils.createFile(project2, "shouldUseExtension.lsptWithMultiRoot", "");

		IEditorPart editor1 = TestUtils.openEditor(testFile1);
		IEditorPart editor2 = TestUtils.openEditor(testFile2);

		@NonNull Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, request -> true);

		assertEquals(1, wrappers.size());

		LanguageServerWrapper wrapper = wrappers.iterator().next();
		waitForAndAssertCondition(2_000, wrapper::isActive);

		// e.g. LanguageServerWrapper@69fe8c75 [serverId=org.eclipse.lsp4e.test.server-with-multi-root-support, initialPath=null, initialProject=P/LanguageServerWrapperTest_testConnect_11691664858710, isActive=true]
		assertThat(wrapper.toString(), matchesPattern("LanguageServerWrapper@[0-9a-f]+ \\[serverId=org.eclipse.lsp4e.test.server-with-multi-root-support, initialPath=null, initialProject=P\\/LanguageServerWrapperTest_testConnect_[0-9]+, isActive=true, pid=(null|[0-9])+\\]"));

		assertTrue(wrapper.isConnectedTo(testFile1.getLocationURI()));
		assertTrue(wrapper.isConnectedTo(testFile2.getLocationURI()));

		TestUtils.closeEditor(editor1, false);
		TestUtils.closeEditor(editor2, false);
	}

	/**
	 * Check if {@code isActive()} is correctly synchronized with  {@code stop()}
	 * @see <a href="https://github.com/eclipse-lsp4e/lsp4e/pull/688">GitHub Pull Request #688</a>
	 */
	@Test
	public void testStartStopAndActive() throws CoreException, AssertionError {
		final int testCount= 100;

		MockConnectionProviderMultiRootFolders.resetCounts();

		IFile testFile1 = TestUtils.createFile(project, "shouldUseExtension.lsptWithMultiRoot", "");
		IEditorPart editor1 = TestUtils.openEditor(testFile1);
		@NonNull Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile1, request -> true);
		assertEquals(1, wrappers.size());
		LanguageServerWrapper wrapper = wrappers.iterator().next();

		final int startingActiveThreads= ForkJoinPool.commonPool().getActiveThreadCount();

		CompletableFuture<Void> startStop= CompletableFuture.runAsync(() -> {
			for (int i= 0; i < testCount - 1; i++) {
				wrapper.stop();
				wrapper.start();
			}
			wrapper.stop();
		});

		CompletableFuture<Void> testActive= CompletableFuture.runAsync(() -> {
			while (!startStop.isDone()) {
				wrapper.isActive();
			}
		});

		try {
			startStop.get(30, TimeUnit.SECONDS);

			try {
				testActive.get(1, TimeUnit.SECONDS);
			} catch (Exception e) {
				throw new AssertionError("testActive terminated with exception");
			}

		} catch (Exception e) {
			throw new AssertionError("test job terminated with exception");
			//TODO improve diagnostics: check for timeout

		} finally {
			TestUtils.closeEditor(editor1, false);
		}

		// Give the various futures created time to execute. ForkJoinPool.commonPool.awaitQuiescence does not
		// work here - other tests may not have cleaned up correctly.
		long timeOut= System.currentTimeMillis() + 60_000;
		do {
			try {
				Thread.sleep(1_000);
			} catch (InterruptedException e) {
				//ignore
			}
		} while (ForkJoinPool.commonPool().getActiveThreadCount() > startingActiveThreads && System.currentTimeMillis() < timeOut);

		if (ForkJoinPool.commonPool().getActiveThreadCount() > startingActiveThreads)
			throw new AssertionError("timeout waiting for ForkJoinPool.commonPool to go quiet");

		Integer cpStartCount= MockConnectionProviderMultiRootFolders.getStartCount();
		Integer cpStopCount= MockConnectionProviderMultiRootFolders.getStopCount();

		assertEquals(cpStartCount, cpStopCount, "startCount == stopCount");
	}

}
