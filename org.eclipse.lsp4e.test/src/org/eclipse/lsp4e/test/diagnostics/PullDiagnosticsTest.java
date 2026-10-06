/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.diagnostics;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.eclipse.lsp4e.test.utils.TestUtils.waitForCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Consumer;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.DiagnosticRegistrationOptions;
import org.eclipse.lsp4j.DiagnosticServerCapabilities;
import org.eclipse.lsp4j.DocumentDiagnosticParams;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentServerCapabilities;
import org.junit.jupiter.api.Test;

public class PullDiagnosticsTest extends AbstractTestWithProject {

	@Test
	public void testDiagnosticProviderEnablesPullDiagnostics(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(() -> withDiagnosticProvider(provider -> provider.setIdentifier("test-identifier")));

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		TestUtils.openTextViewer(testFile);

		waitForAndAssertCondition(5_000, () -> !getDiagnosticRequests(factory).isEmpty());
		DocumentDiagnosticParams request = getDiagnosticRequests(factory).get(0);
		assertEquals("test-identifier", request.getIdentifier());
		assertTrue(request.getTextDocument().getUri().endsWith(testFile.getName()));
	}

	@Test
	public void testTextDocumentDiagnosticCapabilityAloneDoesNotEnablePullDiagnostics(MockLanguageServerFactory factory)
			throws Exception {
		factory.withCapabilities(() -> {
			ServerCapabilities capabilities = MockLanguageServer.defaultServerCapabilities();
			final var textDocument = new TextDocumentServerCapabilities();
			final var diagnostic = new DiagnosticServerCapabilities();
			diagnostic.setMarkupMessageSupport(true);
			textDocument.setDiagnostic(diagnostic);
			capabilities.setTextDocument(textDocument);
			return capabilities;
		});

		assertNoDiagnosticRequestAfterEdit(factory);
	}

	@Test
	public void testMatchingDocumentSelector(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(() -> withDiagnosticProvider(provider -> {
			final var filter = new DocumentFilter();
			filter.setLanguage("lspt");
			filter.setScheme("file");
			filter.setPattern("**/*.lspt");
			provider.setDocumentSelector(List.of(filter));
		}));

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		TestUtils.openTextViewer(testFile);

		waitForAndAssertCondition(5_000, () -> !getDiagnosticRequests(factory).isEmpty());
	}

	@Test
	public void testNonMatchingDocumentSelector(MockLanguageServerFactory factory) throws Exception {
		factory.withCapabilities(() -> withDiagnosticProvider(provider -> {
			final var filter = new DocumentFilter();
			filter.setLanguage("otherLanguage");
			provider.setDocumentSelector(List.of(filter));
		}));

		assertNoDiagnosticRequestAfterEdit(factory);
	}

	private void assertNoDiagnosticRequestAfterEdit(MockLanguageServerFactory factory) throws Exception {
		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);
		viewer.getDocument().replace(0, 0, "Hello");
		waitForAndAssertCondition(5_000, () -> !factory.getServer().getDidChangeEvents().isEmpty());

		assertFalse(waitForCondition(2_000, () -> !getDiagnosticRequests(factory).isEmpty()));
	}

	private static ServerCapabilities withDiagnosticProvider(Consumer<DiagnosticRegistrationOptions> customizer) {
		ServerCapabilities capabilities = MockLanguageServer.defaultServerCapabilities();
		final var provider = new DiagnosticRegistrationOptions(false, false);
		customizer.accept(provider);
		capabilities.setDiagnosticProvider(provider);
		return capabilities;
	}

	private static List<DocumentDiagnosticParams> getDiagnosticRequests(MockLanguageServerFactory factory) {
		return factory.getServer().getTextDocumentService().getDiagnosticRequests();
	}
}
