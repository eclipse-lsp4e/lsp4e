/*******************************************************************************
 * Copyright (c) 2022 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.semanticTokens;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServiceAccessor;
import org.eclipse.lsp4e.operations.semanticTokens.SemanticTokensClient;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.junit.jupiter.api.Test;

public class SemanticTokensLegendProviderTest extends AbstractTestWithProject {

	@Test
	public void testSemanticTokensLegendProvider() throws CoreException {
		// Setup Server Capabilities
		List<String> tokenTypes = List.of("keyword","other");
		List<String> tokenModifiers = List.of("obsolete");
		SemanticTokensTestUtil.setSemanticTokensLegend(tokenTypes, tokenModifiers);

		// Setup test data
		IFile file = TestUtils.createUniqueTestFile(project, "lspt", "test content");
		// start the LS
		LanguageServerWrapper wrapper = LanguageServiceAccessor.getLSWrappers(file, c -> Boolean.TRUE).iterator()
		.next();

		final var semanticTokensLegend = SemanticTokensClient.DEFAULT.getSemanticTokensLegend(wrapper);
		assertNotNull(semanticTokensLegend);
		assertEquals(tokenTypes, semanticTokensLegend.getTokenTypes());
		assertEquals(tokenModifiers, semanticTokensLegend.getTokenModifiers());
	}
}
