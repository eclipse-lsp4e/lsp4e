/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * - Angelo ZERR (Red Hat Inc.) - initial API and implementation
 * - Sebastian Thomschke (Vegard IT GmbH) - adapted the code from LSP4IJ to LSP4E
 *******************************************************************************/
package org.eclipse.lsp4e.internal.files;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

// Based on https://github.com/redhat-developer/lsp4ij/blob/6f41f6d22a7146f31e0218cb459513abd5dc16d3/src/main/java/com/redhat/devtools/lsp4ij/features/files/PathPatternMatcher.java
public final class PathPatternMatcher {

	private record Parts(List<String> parts, List<Integer> cols) {
	}

	private @Nullable List<PathMatcher> pathMatchers;
	private final String pattern;
	private final @Nullable Path basePath;

	public PathPatternMatcher(final String pattern, final @Nullable Path basePath) {
		this.pattern = pattern;
		this.basePath = basePath;
	}

	public String getPattern() {
		return pattern;
	}

	public @Nullable Path getBasePath() {
		return basePath;
	}

	public boolean matches(final URI uri) {
		return internalMatches(Paths.get(uri));
	}

	public boolean matches(final Path path) {
		return internalMatches(path);
	}

	private boolean internalMatches(final Path pathToMatch) {
		if (pattern.isEmpty())
			return false;

		var pathMatchers = this.pathMatchers;
		if (pathMatchers == null) {
			pathMatchers = this.pathMatchers = createPathMatchers();
		}
		try {
			for (final PathMatcher pathMatcher : pathMatchers) {
				try {
					if (pathMatcher.matches(pathToMatch)) {
						return true;
					}
				} catch (final Exception ex) {
					// ignore matcher errors, treat as non-match
				}
			}
		} catch (final Exception e) {
			// ignore URI/Path conversion errors, treat as non-match
		}
		return false;
	}

	private synchronized List<PathMatcher> createPathMatchers() {
		final String glob = pattern.replace("\\", "/"); //$NON-NLS-1$ //$NON-NLS-2$
		// As Java NIO glob does not support **/ or /** as optional
		// we need to expand the pattern, ex: **/foo -> foo, **/foo.
		final List<String> expandedPatterns = expandPatterns(glob);
		final var compiledMatchers = new ArrayList<PathMatcher>();
		for (final var expandedPattern : expandedPatterns) {
			try {
				final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + expandedPattern); //$NON-NLS-1$
				compiledMatchers.add(pathMatcher);
			} catch (final Exception ex) {
				// ignore invalid glob expressions
			}
		}
		return compiledMatchers;
	}

	@Override
	public boolean equals(final @Nullable Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final var other = (PathPatternMatcher) obj;
		return Objects.equals(basePath, other.basePath) //
				&& Objects.equals(pathMatchers, other.pathMatchers) //
				&& Objects.equals(pattern, other.pattern);
	}

	@Override
	public int hashCode() {
		return Objects.hash(basePath, pathMatchers, pattern);
	}

	/**
	 * Expand the given pattern. ex: <code>*&ast;/foo -> foo, *&ast;/foo<code>.
	 */
	public static List<String> expandPatterns(final String pattern) {
		final Parts parts = getParts(pattern);
		if (parts != null) {
			// tokenize pattern ex : **/foo/** --> [**/, foo, /**]
			final var expanded = new ArrayList<String>();
			// generate combinations array with 0,1 according to the number of **/, /**
			// ex: **/foo/** (number=2) --> [[0, 0], [0, 1], [1, 0], [1, 1]]
			final List<int[]> combinations = generateCombinations(parts.cols().size());
			for (final int[] combination : combinations) {
				// Clone tokenized pattern (ex : [**/, foo, /**])
				final var expand = new ArrayList<String>(parts.parts());
				for (int i = 0; i < combination.length; i++) {
					// Loop for current combination (ex : [0, 1])
					if (combination[i] == 0) {
						// When 0, replace **/, /** with ""
						// ex : [**/, foo, /**] --> ["", foo, "/**"]
						final int col = parts.cols().get(i);
						expand.set(col, ""); //$NON-NLS-1$
					}
				}
				// ["", foo, "/**"] --> foo/**
				expanded.add(String.join("", expand)); //$NON-NLS-1$
			}
			return expanded;
		}
		return Collections.singletonList(pattern);
	}

	private static @Nullable Parts getParts(final String pattern) {
		int from = 0;
		int index = getNextIndex(pattern, from);
		if (index != -1) {
			final List<Integer> cols = new ArrayList<>();
			final List<String> parts = new ArrayList<>();
			while (index != -1) {
				final String s = pattern.substring(from, index);
				if (!s.isEmpty()) {
					parts.add(s);
				}
				cols.add(Integer.valueOf(parts.size()));
				from = index + 3;
				parts.add(pattern.substring(index, from));
				index += 3;
				index = getNextIndex(pattern, index);
			}
			parts.add(pattern.substring(from));
			return new Parts(parts, cols);
		}
		return null;
	}

	private static int getNextIndex(final String pattern, final int fromIndex) {
		final int startSlashIndex = pattern.indexOf("**/", fromIndex); //$NON-NLS-1$
		final int endSlashIndex = pattern.indexOf("/**", fromIndex); //$NON-NLS-1$
		if (startSlashIndex != -1 || endSlashIndex != -1) {
			if (startSlashIndex == -1)
				return endSlashIndex;
			if (endSlashIndex == -1)
				return startSlashIndex;
			return Math.min(startSlashIndex, endSlashIndex);
		}
		return -1;
	}

	private static List<int[]> generateCombinations(final int count) {
		final var combinations = new ArrayList<int[]>();
		generateCombinationsHelper(count, new int[count], 0, combinations);
		return combinations;
	}

	private static void generateCombinationsHelper(final int count, final int[] combination, final int index,
			final List<int[]> combinations) {
		if (index == count) {
			combinations.add(combination.clone());
		} else {
			combination[index] = 0;
			generateCombinationsHelper(count, combination, index + 1, combinations);
			combination[index] = 1;
			generateCombinationsHelper(count, combination, index + 1, combinations);
		}
	}
}
