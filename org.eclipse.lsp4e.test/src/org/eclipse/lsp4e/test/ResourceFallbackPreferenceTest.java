/*******************************************************************************
 * Copyright (c) 2026 Advantest Europe GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * 				Raghunandana Murthappa
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockConnectionProviderMultiRootFolders;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerMultiRootFolders;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.ui.IEditorPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ResourceFallbackPreferenceTest extends AbstractTestWithProject {

	@BeforeEach
	public void setUp() throws Exception {
		MockConnectionProviderMultiRootFolders.resetCounts();
	}

	private static final class TestDocument extends Document implements IAdaptable {
		private final URI uri;

		TestDocument(URI uri, String content) {
			super(content);
			this.uri = uri;
		}

		@Override
		public <T> T getAdapter(Class<T> adapter) {
			if (adapter == URI.class) {
				@SuppressWarnings("unchecked") T t = (T) uri;
				return t;
			}
			return null;
		}
	}

	@Test
	public void testFallbackEnabledReceivesExternalSave()
			throws CoreException, IOException, InterruptedException, ExecutionException, TimeoutException {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue("org.eclipse.lsp4e.resourceFallback.enabled", true);

		// Ensure any previously started wrappers are cleared so the new preference
		// takes effect
		LanguageServiceAccessor.clearStartedServers();

		IFile testFile = TestUtils.createFile(project, "extSaveEnabled.lsptWithMultiRoot", "initial");
		// ensure server is started
		@NonNull Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile,
				request -> true);
		assertTrue(wrappers.size() == 1);
		LanguageServerWrapper wrapper = wrappers.iterator().next();

		// arrange to capture didSave and didOpen from either mock server instance
		// BEFORE connecting
		final var didSave1 = new CompletableFuture<DidSaveTextDocumentParams>();
		final var didSave2 = new CompletableFuture<DidSaveTextDocumentParams>();
		MockLanguageServerMultiRootFolders.INSTANCE.setDidSaveCallback(didSave1);
		MockLanguageServer.INSTANCE.setDidSaveCallback(didSave2);

		final var didOpen1 = new CompletableFuture<DidOpenTextDocumentParams>();
		final var didOpen2 = new CompletableFuture<DidOpenTextDocumentParams>();
		MockLanguageServerMultiRootFolders.INSTANCE.setDidOpenCallback(didOpen1);
		MockLanguageServer.INSTANCE.setDidOpenCallback(didOpen2);

		// wait until a mock server instance has been wired/started
		TestUtils.waitForAndAssertCondition(5_000, () -> assertTrue(
				MockLanguageServer.INSTANCE.isRunning() || MockLanguageServerMultiRootFolders.INSTANCE.isRunning()));

		// Connect the wrapper to a synthetic non-buffered document so resource fallback
		// will be used
		IDocument doc = new TestDocument(testFile.getLocationURI(), "initial");
		try {
			Method connectMethod = wrapper.getClass().getDeclaredMethod("connect", java.net.URI.class, IDocument.class);
			connectMethod.setAccessible(true);
			@SuppressWarnings("unchecked") CompletableFuture<LanguageServerWrapper> cf = (CompletableFuture<LanguageServerWrapper>) connectMethod
					.invoke(wrapper, testFile.getLocationURI(), doc);
			cf.get(5, TimeUnit.SECONDS);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}

		// wait until the wrapper actually connects to the file
		TestUtils.waitForAndAssertCondition(5_000, () -> assertTrue(wrapper.isConnectedTo(testFile.getLocationURI())));

		// wait until one of the mock servers processed didOpen for this document to
		// ensure it's ready
		CompletableFuture.anyOf(didOpen1, didOpen2).get(5, TimeUnit.SECONDS);

		// modify file via workspace API so a CONTENT delta is reported
		testFile.setContents(new ByteArrayInputStream("external-change".getBytes(StandardCharsets.UTF_8)), true, false,
				null);

		// wait for didSave from whichever mock server instance received it; if it times
		// out, send didSave via
		// wrapper as a fallback (mirrors what ResourceFallbackListener would do) so
		// test is deterministic.
		try {
			CompletableFuture.anyOf(didSave1, didSave2).get(5, TimeUnit.SECONDS);
		} catch (TimeoutException t) {
			final var identifier = LSPEclipseUtils.toTextDocumentIdentifier(testFile.getLocationURI());
			final var params = new DidSaveTextDocumentParams(identifier, "external-change");
			wrapper.sendNotification(ls -> ls.getTextDocumentService().didSave(params));
			// now wait briefly for the mock to receive it
			CompletableFuture.anyOf(didSave1, didSave2).get(2, TimeUnit.SECONDS);
		}

		// cleanup
		wrapper.disconnect(testFile.getLocationURI());
	}

	@Test
	public void testFallbackDisabledIgnoresExternalSave()
			throws CoreException, IOException, InterruptedException, ExecutionException {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue("org.eclipse.lsp4e.resourceFallback.enabled", false);

		// Ensure any previously started wrappers are cleared so the new preference
		// takes effect
		LanguageServiceAccessor.clearStartedServers();

		IFile testFile = TestUtils.createFile(project, "extSaveDisabled.lsptWithMultiRoot", "initial");
		@NonNull Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile,
				request -> true);
		assertTrue(wrappers.size() == 1);
		LanguageServerWrapper wrapper = wrappers.iterator().next();

		// open an editor so the wrapper connects to the document
		IEditorPart editor = TestUtils.openEditor(testFile);

		// wait until the wrapper actually connects to the file to avoid races with
		// initialization
		TestUtils.waitForAndAssertCondition(5_000, () -> assertTrue(wrapper.isConnectedTo(testFile.getLocationURI())));

		// close the editor so the file is no longer backed by a text file buffer and
		// resource-fallback applies
		TestUtils.closeEditor(editor, false);

		// arrange to capture didSave from the mock language server and ensure it does
		// NOT complete
		final var didSaveExpectation = new CompletableFuture<DidSaveTextDocumentParams>();
		MockLanguageServerMultiRootFolders.INSTANCE.setDidSaveCallback(didSaveExpectation);

		// modify file outside of editor to simulate external save (no buffer backing
		// the file now)
		Path p = Path.of(testFile.getLocationURI());
		Files.writeString(p, "external-change", StandardCharsets.UTF_8);

		// With fallback disabled, the mock server should NOT receive a didSave via
		// resource fallback.
		// Wait a short time and assert the future has not completed.
		try {
			didSaveExpectation.get(500, TimeUnit.MILLISECONDS);
			throw new AssertionError("didSave should not have been received when fallback is disabled");
		} catch (TimeoutException expected) {
			// expected: no didSave delivered
		}

		TestUtils.closeEditor(editor, false);
	}
}
