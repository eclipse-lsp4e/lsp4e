/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.AbstractLSPCodeMiningProvider;
import org.eclipse.lsp4e.internal.GhostTextRegistry;
import org.eclipse.lsp4e.operations.codelens.CodeLensProvider;
import org.eclipse.lsp4e.operations.color.DocumentColorProvider;
import org.eclipse.lsp4e.operations.inlayhint.InlayHintProvider;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link AbstractLSPCodeMiningProvider} cancels the previous
 * in-flight request for the same document when a new one starts.
 */
class AbstractLSPCodeMiningProviderTest extends AbstractTestWithProject {

	private static final class StubProvider extends AbstractLSPCodeMiningProvider {
		@Override
		protected @Nullable CompletableFuture<List<? extends ICodeMining>> doProvideCodeMinings(IDocument doc,
				TextDocumentIdentifier docId) {
			return new CompletableFuture<>();
		}
	}

	@Test
	void cancelsPreviousRequestForSameDocument() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "txt", "content");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		var provider = new StubProvider();

		var first = provider.provideCodeMinings(viewer, new NullProgressMonitor());
		assertNotNull(first);
		assertFalse(first.isCancelled(), "first request should start uncancelled");

		var second = provider.provideCodeMinings(viewer, new NullProgressMonitor());
		assertNotNull(second);

		assertTrue(first.isCancelled(), "previous request should be cancelled when a new one starts");
		assertFalse(second.isCancelled(), "new request should remain active");
	}

	@Test
	void keepsRequestsIndependentAcrossDocuments() throws Exception {
		IFile file1 = TestUtils.createUniqueTestFile(project, "txt", "one");
		IFile file2 = TestUtils.createUniqueTestFile(project, "txt", "two");
		ITextViewer viewer1 = TestUtils.openTextViewer(file1);
		ITextViewer viewer2 = TestUtils.openTextViewer(file2);

		var provider = new StubProvider();

		var first = provider.provideCodeMinings(viewer1, new NullProgressMonitor());
		var second = provider.provideCodeMinings(viewer2, new NullProgressMonitor());

		assertNotNull(first);
		assertNotNull(second);
		assertFalse(first.isCancelled(), "request for doc1 must remain active when doc2 starts");
		assertFalse(second.isCancelled(), "request for doc2 must start active");
	}

	private static final int TIMEOUT = 5_000;

	private ITextViewer open(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(() -> {
			final ServerCapabilities capabilities = MockLanguageServer.defaultServerCapabilities();
			capabilities.setInlayHintProvider(true);
			capabilities.setColorProvider(true);
			return capabilities;
		});
		final ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "lspt", "hello\nworld\n"));
		TestUtils.waitForAndAssertCondition(TIMEOUT, () -> LanguageServers.forDocument(viewer.getDocument()).anyMatching());
		return viewer;
	}

	private static List<? extends ICodeMining> minings(ICodeMiningProvider provider, ITextViewer viewer) throws Exception {
		final var minings = provider.provideCodeMinings(viewer, new NullProgressMonitor());
		assertNotNull(minings);
		return minings.get(TIMEOUT, TimeUnit.MILLISECONDS);
	}

	private static List<? extends ICodeMining> miningsForGhostText(ICodeMiningProvider provider, ITextViewer viewer)
			throws Exception {
		final var minings = new AtomicReference<CompletableFuture<List<? extends ICodeMining>>>();
		GhostTextRegistry.refreshForGhostText(viewer.getDocument(),
				() -> minings.set(provider.provideCodeMinings(viewer, new NullProgressMonitor())));
		assertNotNull(minings.get());
		return minings.get().get(TIMEOUT, TimeUnit.MILLISECONDS);
	}

	/**
	 * Checks that, with one mining computed and the server now answering with two, a refresh only for
	 * ghost text shows copies of the one, and any other refresh asks the server.
	 */
	private static void assertShownAgainOnlyForGhostText(ICodeMiningProvider provider, ITextViewer viewer,
			Runnable answerWithTwo) throws Exception {
		final List<? extends ICodeMining> computed = minings(provider, viewer);
		assertEquals(1, computed.size());
		answerWithTwo.run();

		final List<? extends ICodeMining> shownAgain = miningsForGhostText(provider, viewer);
		assertEquals(1, shownAgain.size());
		assertNotSame(computed.get(0), shownAgain.get(0), "JFace disposes the minings it replaces");
		assertEquals(computed.get(0).getLabel(), shownAgain.get(0).getLabel());

		assertEquals(2, minings(provider, viewer).size());
	}

	@Test
	void inlayHintsAreShownAgainOnlyForGhostText(MockLanguageServerFactory factory) throws Exception {
		final ITextViewer viewer = open(factory);
		final InlayHint first = new InlayHint(new Position(0, 1), Either.forLeft("a"));
		final InlayHint second = new InlayHint(new Position(1, 1), Either.forLeft("b"));
		final var documents = factory.getServer().getTextDocumentService();
		documents.setInlayHints(List.of(first));
		assertShownAgainOnlyForGhostText(new InlayHintProvider(), viewer, () -> documents.setInlayHints(List.of(first, second)));
	}

	@Test
	void codeLensesAreShownAgainOnlyForGhostText(MockLanguageServerFactory factory) throws Exception {
		final ITextViewer viewer = open(factory);
		final var first = new CodeLens(new Range(new Position(0, 0), new Position(0, 1)), new Command("a", "dummy"), null);
		final var second = new CodeLens(new Range(new Position(1, 0), new Position(1, 1)), new Command("b", "dummy"), null);
		final MockLanguageServer server = factory.getServer();
		server.setCodeLens(List.of(first));
		assertShownAgainOnlyForGhostText(new CodeLensProvider(), viewer, () -> server.setCodeLens(List.of(first, second)));
	}

	@Test
	void colorsAreShownAgainOnlyForGhostText(MockLanguageServerFactory factory) throws Exception {
		final ITextViewer viewer = open(factory);
		final var red = new Color(1, 0, 0, 1);
		final var first = new ColorInformation(new Range(new Position(0, 0), new Position(0, 1)), red);
		final var second = new ColorInformation(new Range(new Position(1, 0), new Position(1, 1)), red);
		final var documents = factory.getServer().getTextDocumentService();
		documents.setDocumentColors(List.of(first));
		assertShownAgainOnlyForGhostText(new DocumentColorProvider(), viewer, () -> documents.setDocumentColors(List.of(first, second)));
	}

	@Test
	void ghostTextDoesNotShowMiningsOfAnEarlierVersionOrFromBeforeARefreshRequest(MockLanguageServerFactory factory)
			throws Exception {
		final ITextViewer viewer = open(factory);
		final InlayHint first = new InlayHint(new Position(0, 1), Either.forLeft("a"));
		final InlayHint second = new InlayHint(new Position(1, 1), Either.forLeft("b"));
		final var documents = factory.getServer().getTextDocumentService();
		documents.setInlayHints(List.of(first));
		final var provider = new InlayHintProvider();
		assertEquals(1, minings(provider, viewer).size());

		documents.setInlayHints(List.of(first, second));
		AbstractLSPCodeMiningProvider.invalidateCaches();
		assertEquals(2, miningsForGhostText(provider, viewer).size(), "the server asked for a refresh");

		documents.setInlayHints(List.of(first));
		viewer.getDocument().replace(0, 0, "x");
		assertEquals(1, miningsForGhostText(provider, viewer).size(), "the document changed");
	}
}
