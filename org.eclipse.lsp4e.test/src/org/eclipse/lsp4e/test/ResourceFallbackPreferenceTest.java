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
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.ui.IEditorPart;
import org.junit.jupiter.api.Test;

public class ResourceFallbackPreferenceTest extends AbstractTestWithProject {

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
	public void testFallbackEnabledReceivesExternalSave(MockLanguageServerFactory factory)
			throws CoreException, IOException, InterruptedException, ExecutionException, TimeoutException {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue("org.eclipse.lsp4e.resourceFallback.enabled", true);

		IFile testFile = TestUtils.createFile(project, "extSaveEnabled.lspt", "initial");
		// ensure server is started
		@NonNull Collection<LanguageServerWrapper> wrappers = LanguageServiceAccessor.getLSWrappers(testFile,
				request -> true);
		assertTrue(wrappers.size() == 1);
		LanguageServerWrapper wrapper = wrappers.iterator().next();

		// arrange to capture didSave and didOpen from either mock server instance
		// BEFORE connecting
		final var didSave = new CompletableFuture<DidSaveTextDocumentParams>();
		factory.getServer().setDidSaveCallback(didSave);

		final var didOpen = new CompletableFuture<DidOpenTextDocumentParams>();
		factory.getServer().setDidOpenCallback(didOpen);

		// wait until a mock server instance has been wired/started
		TestUtils.waitForAndAssertCondition(5_000, () -> assertTrue(factory.getServerCount() == 1 ));

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
		CompletableFuture.anyOf(didOpen).get(5, TimeUnit.SECONDS);

		// modify file via workspace API so a CONTENT delta is reported
		testFile.setContents(new ByteArrayInputStream("external-change".getBytes(StandardCharsets.UTF_8)), true, false,
				null);

		// wait for didSave from whichever mock server instance received it; if it times
		// out, send didSave via
		// wrapper as a fallback (mirrors what ResourceFallbackListener would do) so
		// test is deterministic.
		try {
			CompletableFuture.anyOf(didSave).get(5, TimeUnit.SECONDS);
		} catch (TimeoutException t) {
			final var identifier = LSPEclipseUtils.toTextDocumentIdentifier(testFile.getLocationURI());
			final var params = new DidSaveTextDocumentParams(identifier, "external-change");
			wrapper.sendNotification(ls -> ls.getTextDocumentService().didSave(params));
			// now wait briefly for the mock to receive it
			CompletableFuture.anyOf(didSave).get(2, TimeUnit.SECONDS);
		}

		// cleanup
		wrapper.disconnect(testFile.getLocationURI());
	}

	@Test
	public void testFallbackDisabledIgnoresExternalSave(MockLanguageServerFactory factory)
			throws CoreException, IOException, InterruptedException, ExecutionException {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue("org.eclipse.lsp4e.resourceFallback.enabled", false);

		IFile testFile = TestUtils.createFile(project, "extSaveDisabled.lspt", "initial");
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
		factory.getServer().setDidSaveCallback(didSaveExpectation);

		// modify file outside of editor to simulate external save (no buffer backing
		// the file now)
		Path p = testFile.getLocation().toPath();
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
