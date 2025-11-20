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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.codemining.ICodeMining;
import org.eclipse.lsp4e.internal.AbstractLSPCodeMiningProvider;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4j.TextDocumentIdentifier;
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
}
