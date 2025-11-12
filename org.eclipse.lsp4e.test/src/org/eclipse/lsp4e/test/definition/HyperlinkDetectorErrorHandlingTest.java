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
package org.eclipse.lsp4e.test.definition;

import static org.junit.Assert.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.lsp4e.operations.declaration.OpenDeclarationHyperlinkDetector;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockTextDocumentService;
import org.eclipse.lsp4j.DeclarationParams;
import org.eclipse.lsp4j.ImplementationParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.Test;

public class HyperlinkDetectorErrorHandlingTest extends AbstractTestWithProject {

	private final OpenDeclarationHyperlinkDetector detector = new OpenDeclarationHyperlinkDetector();

	@Override
	protected ServerCapabilities getServerCapabilities() {
		// Ensure providers are enabled to exercise all branches
		var caps = MockLanguageServer.defaultServerCapabilities();
		caps.setDefinitionProvider(true);
		caps.setTypeDefinitionProvider(true);
		caps.setDeclarationProvider(true);
		caps.setImplementationProvider(true);
		return caps;
	}

	@Test
	public void testDefinitionRemainsWhenTypeDefinitionErrors() throws Exception {
		MockLanguageServer.INSTANCE.setTextDocumentService(
				// Simulate server error for typeDefinition (mirrors issue
				// https://github.com/eclipse-lsp4e/lsp4e/issues/1169)
				new MockTextDocumentService(MockLanguageServer.INSTANCE::buildMaybeDelayedFuture) {
					@Override
					public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(
							TypeDefinitionParams params) {
						var f = new CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>();
						f.completeExceptionally(
								new RuntimeException("unexpected error during typeDefinition retrieval"));
						return f;
					}

					@Override
					public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> implementation(
							ImplementationParams params) {
						throw new RuntimeException("unexpected error during implementation retrieval");
					}

					@Override
					public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> declaration(
							DeclarationParams params) {
						throw new RuntimeException("unexpected error during declaration retrieval");
					}
				});

		// ensure TextDocumentService is faulty
		assertThrows(RuntimeException.class,
				() -> MockLanguageServer.INSTANCE.getTextDocumentService().declaration(null));
		assertThrows(RuntimeException.class,
				() -> MockLanguageServer.INSTANCE.getTextDocumentService().implementation(null));
		assertTrue(
				MockLanguageServer.INSTANCE.getTextDocumentService().typeDefinition(null).isCompletedExceptionally());

		// Configure 1 good definition result
		MockLanguageServer.INSTANCE.setDefinition(List.of( //
				new Location("file://def", new Range(new Position(0, 0), new Position(0, 10))), //
				new Location("file://def", new Range(new Position(1, 10), new Position(1, 20)))));

		IFile file = TestUtils.createUniqueTestFile(project, "Example Text");
		ITextViewer viewer = TestUtils.openTextViewer(file);

		IHyperlink[] links = detector.detectHyperlinks(viewer, new Region(0, 0), true);

		// Expected: 1 link (from definition) even if typeDefinition fails
		assertNotNull("Hyperlinks should not be null when definition succeeds despite typeDefinition error", links);
		assertEquals(2, links.length);
	}
}
