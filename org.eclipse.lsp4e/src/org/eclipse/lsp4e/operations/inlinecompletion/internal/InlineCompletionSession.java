/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.InlineCompletionTriggerKind;

/**
 * The candidates of one inline completion request and which of them is shown. Candidate ranges are
 * tracked across the edits made while the session lives (typing through the suggestion, Backspace).
 */
public final class InlineCompletionSession {

	private final InlineCompletionTriggerKind triggerKind;
	private final List<InlineCompletionCandidate> candidates = new ArrayList<>();
	private final List<@Nullable TrackedRange> ranges = new ArrayList<>();
	private int index;

	/** @param triggerKind how the request was triggered */
	public InlineCompletionSession(InlineCompletionTriggerKind triggerKind) {
		this.triggerKind = triggerKind;
	}

	public InlineCompletionTriggerKind getTriggerKind() {
		return triggerKind;
	}

	/** Adds candidates, skipping duplicates of candidates already present. */
	public void addCandidates(List<InlineCompletionCandidate> toAdd) {
		final var distinct = new ArrayList<>(candidates);
		CandidateNormalizer.addDistinct(distinct, toAdd);
		for (final InlineCompletionCandidate candidate : distinct.subList(candidates.size(), distinct.size())) {
			candidates.add(candidate);
			ranges.add(new TrackedRange(candidate.start(), candidate.end()));
		}
	}

	public int size() {
		return candidates.size();
	}

	/**
	 * Updates the candidate ranges for a change made at the caret. A candidate whose range start the
	 * change touches or precedes can no longer be shown.
	 */
	public void documentChanged(int offset, int deletedLength, int insertedLength) {
		for (int i = 0; i < ranges.size(); i++) {
			final TrackedRange range = ranges.get(i);
			if (range != null) {
				ranges.set(i, offset < range.start() ? null : range.apply(offset, deletedLength, insertedLength));
			}
		}
	}

	/**
	 * Returns what to show: the current candidate if it can still be shown, otherwise the next one that
	 * can (which becomes current).
	 *
	 * @return the ghost text, or {@code null} if no candidate can be shown
	 */
	public @Nullable GhostText current(IDocument document, int caret) {
		for (int step = 0; step < candidates.size(); step++) {
			final int i = (index + step) % candidates.size();
			final GhostText ghost = compute(document, caret, i);
			if (ghost != null) {
				index = i;
				return ghost;
			}
		}
		return null;
	}

	/** Makes the next (or, with {@code forward == false}, the previous) shown candidate current. */
	public @Nullable GhostText cycle(IDocument document, int caret, boolean forward) {
		final int size = candidates.size();
		for (int step = 1; step <= size; step++) {
			final int i = Math.floorMod(index + (forward ? step : -step), size);
			final GhostText ghost = compute(document, caret, i);
			if (ghost != null) {
				index = i;
				return ghost;
			}
		}
		return null;
	}

	/**
	 * Returns the 1-based position of the current candidate among the candidates that can be shown,
	 * and their number, as {@code [position, count]}.
	 */
	public int[] position(IDocument document, int caret) {
		int position = 0;
		int count = 0;
		for (int i = 0; i < candidates.size(); i++) {
			if (compute(document, caret, i) != null) {
				count++;
				if (i == index) {
					position = count;
				}
			}
		}
		return new int[] { position, count };
	}

	private @Nullable GhostText compute(IDocument document, int caret, int i) {
		final TrackedRange range = ranges.get(i);
		return range == null ? null : GhostTextComputer.compute(document, caret, range, candidates.get(i));
	}
}
