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
package org.eclipse.lsp4e.test.completion;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.gson.Gson;

/**
 * Verifies that dynamic registration of completion updates LSP4E server
 * capabilities and enables content assist proposals.
 */
public class DynamicCompletionRegistrationTest extends AbstractTestWithProject {

	private LSContentAssistProcessor contentAssistProcessor;

	@BeforeEach
	public void setup() {
		contentAssistProcessor = new LSContentAssistProcessor(true, false);
	}

    @Test
    public void testDynamicCompletionRegistrationProvidesProposalsAndTriggers() throws Exception {
        // Prepare a file and open a viewer
        IFile file = TestUtils.createUniqueTestFile(project, "");
        ITextViewer viewer = TestUtils.openTextViewer(file);

        // Ensure the mock LS is up
        waitForAndAssertCondition(5_000, () -> !MockLanguageServer.INSTANCE.getRemoteProxies().isEmpty());
        LanguageClient client = getMockClient();
        assertNotNull(client);

        // Provide a simple completion item in the mock LS
        var items = new ArrayList<CompletionItem>();
        var item = new CompletionItem();
        item.setLabel("Alpha");
        item.setKind(CompletionItemKind.Text);
        item.setTextEdit(Either.forLeft(new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), "Alpha")));
        items.add(item);
        MockLanguageServer.INSTANCE.setCompletionList(new CompletionList(false, items));

        // Dynamically register completion with trigger characters (server-like behavior)
        var registration = new Registration();
        registration.setId("test-completion-reg");
        registration.setMethod("textDocument/completion");
        var opts = new CompletionOptions();
        opts.setTriggerCharacters(List.of(".", "/", "#"));
        registration.setRegisterOptions(new Gson().toJsonTree(opts));
        client.registerCapability(new RegistrationParams(List.of(registration))).get(2, TimeUnit.SECONDS);

        // Compute proposals (manual invocation). Should return the mock item.
        ICompletionProposal[] proposals = contentAssistProcessor.computeCompletionProposals(viewer, 0);
        assertEquals(1, proposals.length);
        assertEquals("Alpha", proposals[0].getDisplayString());

        // Ask processor for auto-activation triggers and assert they include the registered ones
        char[] triggers = contentAssistProcessor.getCompletionProposalAutoActivationCharacters();
        String trig = new String(triggers != null ? triggers : new char[0]);
        assertTrue(trig.indexOf('.') >= 0);
        assertTrue(trig.indexOf('/') >= 0);
        assertTrue(trig.indexOf('#') >= 0);
    }

	private LanguageClient getMockClient() {
		var proxies = MockLanguageServer.INSTANCE.getRemoteProxies();
		assertEquals(1, proxies.size());
		return proxies.get(0);
	}
}
