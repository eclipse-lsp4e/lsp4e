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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposalComparator;
import org.eclipse.lsp4j.CompletionItem;
import org.junit.jupiter.api.Test;

public class LSCompletionProposalComparatorTest {

	/**
	 * DocumentFilter length currently has top priority in LSCompletionProposalComparator.
	 * This test encodes the expectation that, once proposals are considered matching,
	 * sortText should decide the order, not the document filter length.
	 */
	@Test
	public void testDocumentFilterLengthDoesNotOverrideSortText() {
		IDocument document = new Document("");

		final var item1 = new CompletionItem("p1");
		item1.setSortText("B");
		final var item2 = new CompletionItem("p2");
		item2.setSortText("A");

		final var proposalWithLongFilter = new StubProposal(document, item1, "longFilter");
		final var proposalWithShortFilter = new StubProposal(document, item2, "x");

		final var comparator = new LSCompletionProposalComparator();
		final var proposals = new ArrayList<LSCompletionProposal>();
		proposals.add(proposalWithLongFilter);
		proposals.add(proposalWithShortFilter);

		proposals.sort(comparator);

		// Desired order: sortText ascending -> "A", then "B"
		assertEquals("A", proposals.get(0).getSortText());
		assertEquals("B", proposals.get(1).getSortText());
	}

	/**
	 * CompletionProposalPopup.computeFilteredProposals filters an already sorted
	 * list of proposals. To avoid surprising reordering if re-sorting is added
	 * later, LSCompletionProposalComparator must not change the relative order
	 * of proposals solely because their documentFilter length changes.
	 *
	 * This test verifies that changing the documentFilter does not change the
	 * comparator outcome for otherwise identical proposals.
	 */
	@Test
	public void testFilteredProposalsShouldBeResortedWhenFilterChanges() {
		IDocument document = new Document("");

		final var item1 = new CompletionItem("p1");
		final var item2 = new CompletionItem("p2");

		final int initialOffset = 1;
		final int updatedOffset = 2;

		final var proposalA = new VarFilterProposal(document, item1);
		final var proposalB = new VarFilterProposal(document, item2);

		// At the initial offset, proposalA has a longer filter than proposalB
		// so A should be ordered before B.
		proposalA.setFilterForOffset(initialOffset, "xx");
		proposalB.setFilterForOffset(initialOffset, "x");

		// After typing more, the filters swap lengths so the desired order
		// (if resorted) would become B before A.
		proposalA.setFilterForOffset(updatedOffset, "x");
		proposalB.setFilterForOffset(updatedOffset, "xx");

		final var comparator = new LSCompletionProposalComparator();

		// Initial sort at invocation offset
		final var initiallySorted = new ArrayList<LSCompletionProposal>();
		initiallySorted.add(proposalA);
		initiallySorted.add(proposalB);
		proposalA.setOffsetForSorting(initialOffset);
		proposalB.setOffsetForSorting(initialOffset);
		initiallySorted.sort(comparator);

		// Simulate JFace behaviour: keep the original order and just filter
		final var filteredWithoutResort = new ArrayList<>(initiallySorted);

		// Expected behaviour: update filters for the new offset and resort
		final var expectedResorted = new ArrayList<LSCompletionProposal>();
		expectedResorted.add(proposalA);
		expectedResorted.add(proposalB);
		proposalA.setOffsetForSorting(updatedOffset);
		proposalB.setOffsetForSorting(updatedOffset);
		expectedResorted.sort(comparator);

		// We would like the filtered list to reflect the re-sorted order.
		assertEquals(expectedResorted, filteredWithoutResort);
	}

	private static class StubProposal extends LSCompletionProposal {

		private final String filter;

		StubProposal(IDocument document, CompletionItem item, String filter) {
			super(document, 0, item, null, null, false);
			this.filter = filter;
		}

		@Override
		public String getDocumentFilter() {
			return filter;
		}

		@Override
		public String getDocumentFilter(int offset) {
			return filter;
		}

		@Override
		public int getRankCategory() {
			return 5;
		}

		@Override
		public int getRankScore() {
			return 0;
		}
	}

	private static class VarFilterProposal extends LSCompletionProposal {

		private final Map<Integer, String> filtersByOffset = new HashMap<>();
		private int sortOffset;

		VarFilterProposal(IDocument document, CompletionItem item) {
			super(document, 0, item, null, null, false);
		}

		void setFilterForOffset(int offset, String filter) {
			filtersByOffset.put(Integer.valueOf(offset), filter);
		}

		void setOffsetForSorting(int offset) {
			this.sortOffset = offset;
		}

		@Override
		public String getDocumentFilter() {
			String filter = filtersByOffset.get(Integer.valueOf(sortOffset));
			return filter != null ? filter : "";
		}

		@Override
		public String getDocumentFilter(int offset) {
			String filter = filtersByOffset.get(Integer.valueOf(offset));
			return filter != null ? filter : "";
		}

		@Override
		public int getRankCategory() {
			return 5;
		}

		@Override
		public int getRankScore() {
			return 0;
		}
	}
}
