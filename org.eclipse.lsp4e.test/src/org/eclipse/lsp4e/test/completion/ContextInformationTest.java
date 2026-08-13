/*******************************************************************************
 * Copyright (c) 2017 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.lsp4e.operations.completion.LSContextInformation;
import org.eclipse.lsp4e.operations.completion.LSContextInformationValidator;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4e.tests.mock.MockTextDocumentService;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SignatureInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ContextInformationTest extends AbstractCompletionTest {

	@Override
	@BeforeEach
	public void setUp() {
		contentAssistProcessor = new LSContentAssistProcessor();
	}

	@Test
	public void testNoContextInformation(MockLanguageServerFactory factory) throws CoreException {
		factory.withConfiguration((idx, server)-> {
			server.setSignatureHelp(new SignatureHelp());
		});

		IFile testFile = TestUtils.createUniqueTestFile(project, "");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		IContextInformation[] infos = contentAssistProcessor.computeContextInformation(viewer, 0);
		assertEquals(0, infos.length);
	}

	@Test
	public void testContextInformationNoParameters(MockLanguageServerFactory factory) throws CoreException {
		final var signatureHelp = new SignatureHelp();
		final var information = new SignatureInformation("label", "documentation", Collections.emptyList());
		signatureHelp.setSignatures(List.of(information));
		factory.withConfiguration((idx, server) -> {
			server.setSignatureHelp(signatureHelp);
		});

		IFile testFile = TestUtils.createUniqueTestFile(project, "method()");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		IContextInformation[] infos = contentAssistProcessor.computeContextInformation(viewer, 0);
		assertEquals(1, infos.length);

		String expected = new StringBuilder(information.getLabel()).append('\n')
				.append(LSPEclipseUtils.getDocString(information.getDocumentation()))
				.toString();
		assertEquals(expected, infos[0].getInformationDisplayString());
	}
	
	@Test
	public void testContextInformationWithActiveParameter(MockLanguageServerFactory factory) throws CoreException {
		factory.withConfiguration((idx, server) -> {
			server.setTextDocumentService(createDocumentServiceWithSignatureHelp(server));
		});

		IFile testFile = TestUtils.createUniqueTestFile(project, "func(arg1, arg2)");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		IContextInformation[] infos = contentAssistProcessor.computeContextInformation(viewer, 10);
		assertEquals(1, infos.length);
		LSContextInformation lsInfo = (LSContextInformation) infos[0];

		// 'par2' is the active parameter
		assertEquals(7, lsInfo.getActiveParameter().start());
		assertEquals(4, lsInfo.getActiveParameter().length());
	}

	@Test
	public void testContextInformationValidationUpdatesActiveParameter(MockLanguageServerFactory factory) throws CoreException {
		factory.withConfiguration((idx, server) -> {
			server.setTextDocumentService(createDocumentServiceWithSignatureHelp(server));
		});

		IFile testFile = TestUtils.createUniqueTestFile(project, "func(arg1, arg2)");
		ITextViewer viewer = TestUtils.openTextViewer(testFile);

		IContextInformation[] infos = contentAssistProcessor.computeContextInformation(viewer, 5);
		assertEquals(1, infos.length);
		LSContextInformation lsInfo = (LSContextInformation) infos[0];

		LSContextInformationValidator validator = new LSContextInformationValidator(contentAssistProcessor);
		validator.install(lsInfo, viewer, 5);
		assertTrue(validator.isContextInformationValid(5));
		assertEquals(1, lsInfo.getActiveParameter().start());

		assertTrue(validator.isContextInformationValid(10));
		assertEquals(7, lsInfo.getActiveParameter().start());

		assertFalse(validator.isContextInformationValid(15));
	}

	private MockTextDocumentService createDocumentServiceWithSignatureHelp(MockLanguageServer server) {
		return new MockTextDocumentService(server::buildMaybeDelayedFuture) {
			@Override
			public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams position) {
				final var signatureHelp = new SignatureHelp();
				final var information = new SignatureInformation("(par1, par2)", "documentation", List.of(
						new ParameterInformation("par1", "par1 doc"), new ParameterInformation("par2", "par2 doc")));
				int pos = position.getPosition().getCharacter();
				if (pos >= 5 && pos < 9) {
					// in 'arg1'
					information.setActiveParameter(0);
				} else if (pos >= 9 && pos < 15) {
					// in ', arg2'
					information.setActiveParameter(1);
				} else {
					// Not in signature
					return CompletableFuture.completedFuture(signatureHelp);
				}
				signatureHelp.setSignatures(List.of(information));
				return CompletableFuture.completedFuture(signatureHelp);
			}
		};
	}

	@Test
	public void testTriggerChars(MockLanguageServerFactory factory) throws CoreException {
		final Set<String> triggers = Set.of("a", "b");
		factory.withConfiguration((idx, server) -> {
			server.setContextInformationTriggerChars(triggers);
		});

		final var content = "First";
		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, content));

		assertArrayEquals(new char[] { 'a', 'b' },
				contentAssistProcessor.getContextInformationAutoActivationCharacters());
	}

	@Test
	public void testTriggerCharsNullList(MockLanguageServerFactory factory) throws CoreException {
		factory.withConfiguration((idx, server)-> {
			server.setContextInformationTriggerChars(null);
		});

		TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "First"));

		assertArrayEquals(new char[0], contentAssistProcessor.getContextInformationAutoActivationCharacters());
	}
}
