/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.test.completion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.eclipse.lsp4e.operations.completion.CompletionProposalTools;
import org.eclipse.lsp4e.operations.completion.LocationInString;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureInformation;
import org.eclipse.lsp4j.jsonrpc.messages.Tuple;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class CompletionProposalToolsTest {

	public static final Stream<Arguments> getActiveParameter() {
		SignatureInformation noActiveParameter = new SignatureInformation("(par1, par2)", "documentation",
				List.of(new ParameterInformation("par1", "par1 doc"), new ParameterInformation("par2", "par2 doc")));

		SignatureInformation invalidIndex = new SignatureInformation("(par1, par2)", "documentation",
				List.of(new ParameterInformation("par1", "par1 doc"), new ParameterInformation("par2", "par2 doc")));
		invalidIndex.setActiveParameter(2);

		SignatureInformation validIndex = new SignatureInformation("(par1, par2)", "documentation",
				List.of(new ParameterInformation("par1", "par1 doc"), new ParameterInformation("par2", "par2 doc")));
		validIndex.setActiveParameter(1);

		SignatureInformation validIndexButNotPresentInSignature = new SignatureInformation("(par1, foobar2)",
				"documentation",
				List.of(new ParameterInformation("par1", "par1 doc"), new ParameterInformation("par2", "par2 doc")));
		validIndexButNotPresentInSignature.setActiveParameter(1);

		ParameterInformation par2 = new ParameterInformation();
		par2.setLabel(Tuple.two(7, 11));
		SignatureInformation activeLocationGivenByTuple = new SignatureInformation("(par1, par2)", "documentation",
				List.of(new ParameterInformation("par1", "par1 doc"), par2));
		activeLocationGivenByTuple.setActiveParameter(1);

		return Stream.of(Arguments.of(noActiveParameter, null), Arguments.of(invalidIndex, null),
				Arguments.of(validIndex, new LocationInString(7, 4)),
				Arguments.of(validIndexButNotPresentInSignature, null),
				Arguments.of(activeLocationGivenByTuple, new LocationInString(7, 4)));
	}

	@ParameterizedTest
	@MethodSource
	void getActiveParameter(SignatureInformation info, LocationInString activeParameter) throws Exception {
		assertEquals(activeParameter, CompletionProposalTools.extractActiveParameter(info));
	}

}
