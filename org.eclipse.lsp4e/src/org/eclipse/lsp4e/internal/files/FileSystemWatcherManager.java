/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * - Angelo ZERR (Red Hat Inc.) - initial API and implementation
 * - Sebastian Thomschke (Vegard IT GmbH) - adapted the code from LSP4IJ to LSP4E; improved thread safty
 *******************************************************************************/
package org.eclipse.lsp4e.internal.files;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.WatchKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

// Based on https://github.com/redhat-developer/lsp4ij/blob/6f41f6d22a7146f31e0218cb459513abd5dc16d3/src/main/java/com/redhat/devtools/lsp4ij/features/files/watcher/FileSystemWatcherManager.java

/**
 * LSP file system manager which matches a given URI by using LSP
 * {@link FileSystemWatcher}.
 */
public final class FileSystemWatcherManager {

	private static final int WATCH_KIND_ANY = 7;

	private final Map<String, List<FileSystemWatcher>> registry = new HashMap<>();
	private final @Nullable Path basePath;

	private volatile @Nullable Set<FileSystemWatcher> fileSystemWatchers;
	private volatile @Nullable Map<Integer, List<PathPatternMatcher>> pathPatternMatchers;

	public FileSystemWatcherManager(final @Nullable IProject project) {
		Path watchedFilesBasePath = null;
		try {
			if (project != null) {
				final var loc = project.getLocationURI();
				if (loc != null)
					watchedFilesBasePath = Paths.get(loc);
			}
		} catch (IllegalArgumentException ex) {
			LanguageServerPlugin.logError(ex);
		}
		this.basePath = watchedFilesBasePath;
	}

	public FileSystemWatcherManager(final @Nullable Path basePath) {
		this.basePath = basePath;
	}

	/**
	 * Register the file system watcher list with the given id.
	 */
	public void registerFileSystemWatchers(final String id, final @Nullable List<FileSystemWatcher> watchers) {
		if (watchers == null)
			return;

		synchronized (registry) {
			registry.put(id, new ArrayList<>(watchers));
			reset();
		}
	}

	/**
	 * Unregister the file system watcher list with the given id.
	 */
	public void unregisterFileSystemWatchers(final String id) {
		synchronized (registry) {
			registry.remove(id);
			reset();
		}
	}

	/**
	 * Removes all registered watchers.
	 */
	public void clear() {
		synchronized (registry) {
			registry.clear();
			reset();
		}
	}

	private void reset() {
		fileSystemWatchers = registry.values().stream() //
				.flatMap(List::stream) //
				.collect(Collectors.toCollection(HashSet::new));
		pathPatternMatchers = null;
	}

	/**
	 * Returns an unmodifiable snapshot of the currently registered LSP file system
	 * watchers, or {@code null} if none are registered.
	 *
	 * @return an unmodifiable set of LSP file system watchers, or {@code null} if
	 *         there are no watchers
	 */
	public @Nullable Set<FileSystemWatcher> getFileSystemWatchers() {
		final Set<FileSystemWatcher> watchers = this.fileSystemWatchers;
		return watchers == null ? null : Set.copyOf(watchers);
	}

	/**
	 * Returns true if there are some file system watchers and false otherwise.
	 *
	 * @return true if there are some file system watchers and false otherwise.
	 */
	public boolean hasFilePatterns() {
		return fileSystemWatchers != null && !fileSystemWatchers.isEmpty();
	}

	public boolean hasFilePatternsFor(final int kind) {
		if (!hasFilePatterns())
			return false;

		// Ensure pattern matchers are initialized before use
		computePatternMatchersIfNeeded();

		final var pathPatternMatchers = this.pathPatternMatchers;
		if (pathPatternMatchers == null)
			return false;

		final List<PathPatternMatcher> matchersForKind = pathPatternMatchers.get(kind);
		return matchersForKind != null && !matchersForKind.isEmpty();
	}

	/**
	 * Returns true if the given uri matches a pattern for the given watch kind and
	 * false otherwise.
	 *
	 * @param uri
	 *            the uri to match.
	 * @param kind
	 *            the watch kind ({@link WatchKind#Create},
	 *            {@link WatchKind#Change}, {@link WatchKind#Delete} or 7 (for any))
	 * @return true if the given uri matches a pattern for the given watch kind and
	 *         false otherwise.
	 */
	public boolean isMatchFilePattern(final @Nullable URI uri, final int kind) {
		// If no URI or no patterns are registered, there can be no match
		if (uri == null || !hasFilePatterns())
			return false;

		// Ensure pattern matchers are initialized before use
		computePatternMatchersIfNeeded();

		// Cache: basePath -> relative path if included, false otherwise
		final Map<Path, Either<Path, Boolean>> basePathToRelativePath = new HashMap<>();

		try {
			// Convert the URI to a Path for matching
			final Path path = Paths.get(uri);

			// Match against the given kind or the "any" kind
			return match(path, kind, basePathToRelativePath) || match(path, WATCH_KIND_ANY, basePathToRelativePath);

		} catch (final Exception ex) {
			// Any failure in URI-to-Path conversion or matching is treated as "no match"
			LanguageServerPlugin.logWarning(ex.getMessage(), ex);
		}
		return false;
	}

	private void computePatternMatchersIfNeeded() {
		if (pathPatternMatchers == null) {
			computePatternMatchers();
		}
	}

	private synchronized void computePatternMatchers() {
		if (pathPatternMatchers != null) {
			return;
		}
		final Set<FileSystemWatcher> watchers = this.fileSystemWatchers;
		if (watchers == null) {
			pathPatternMatchers = Map.of();
			return;
		}

		final var matchers = new HashMap<Integer, List<PathPatternMatcher>>();
		for (final FileSystemWatcher watcher : watchers) {
			final PathPatternMatcher matcher = getPathPatternMatcher(watcher, basePath);
			if (matcher != null) {
				final Integer kind = watcher.getKind();
				tryAddingMatcher(matcher, matchers, kind, WatchKind.Create);
				tryAddingMatcher(matcher, matchers, kind, WatchKind.Change);
				tryAddingMatcher(matcher, matchers, kind, WatchKind.Delete);
			}
		}
		pathPatternMatchers = matchers;
	}

	/**
	 * Checks whether the given {@link Path} matches any registered
	 * {@link PathPatternMatcher} for the specified watch kind.
	 *
	 * <p>
	 * This method iterates through all pattern matchers for the given {@code kind},
	 * checks if the provided path is under each matcher’s base path, and if so,
	 * applies the matcher to the relative path.
	 * </p>
	 *
	 * <p>
	 * To optimize performance, a cache map is used to store intermediate results
	 * for base path checks:
	 * <ul>
	 * <li><b>Key:</b> the base path of a matcher</li>
	 * <li><b>Value:</b> Either:
	 * <ul>
	 * <li>Left - the relative path if the path is under the base path</li>
	 * <li>Right(false) - indicates the path is not under this base path</li>
	 * </ul>
	 * </li>
	 * </ul>
	 * </p>
	 *
	 * @param path
	 *            the file path to test, must not be {@code null}
	 * @param kind
	 *            the watch kind to check against (e.g., {@link WatchKind#Create},
	 *            {@link WatchKind#Change}, {@link WatchKind#Delete}, or
	 *            {@code WATCH_KIND_ANY} for a wildcard match)
	 * @param basePathToRelativePath
	 *            a cache for storing relative paths or negative results
	 * @return {@code true} if the path matches any pattern for the given kind,
	 *         {@code false} otherwise
	 */
	private boolean match(final Path path, final int kind,
			final Map<Path, Either<Path, Boolean>> basePathToRelativePath) {

		final var pathPatternMatchers = this.pathPatternMatchers;
		if (pathPatternMatchers == null)
			return false;

		// Retrieve all matchers registered for the given kind
		final List<PathPatternMatcher> matchers = pathPatternMatchers.get(Integer.valueOf(kind));
		if (matchers == null)
			return false; // No matchers for this kind

		// Iterate over each matcher
		for (final var matcher : matchers) {
			// Check if the path is under the matcher’s base path
			final Path matcherBasePath = matcher.getBasePath();
			if (matcherBasePath == null)
				continue;
			final Path relativePath = matchBasePath(path, matcherBasePath, basePathToRelativePath);
			if (relativePath == null)
				continue;
			// Apply the matcher to the relative path
			if (matcher.matches(matcherBasePath.relativize(path)))
				return true;
		}

		// No matcher matched
		return false;
	}

	/**
	 * Checks whether the given {@link Path} is located under a specified base path.
	 *
	 * <p>
	 * If the path is under the base path, the relative path is returned and cached.
	 * If the path is not under the base path, the result is cached as a negative
	 * match.
	 * </p>
	 *
	 * @param path
	 *            the path to check, must not be {@code null}
	 * @param basePath
	 *            the base path to test against, may be {@code null}
	 * @param basePathToRelativePath
	 *            cache map to store computed results
	 * @return the relative path between {@code basePath} and {@code path} if
	 *         included, or {@code null} if the path is not under the base path
	 */
	private static @Nullable Path matchBasePath(final Path path, final @Nullable Path basePath,
			final Map<Path, Either<Path, Boolean>> basePathToRelativePath) {
		if (basePath == null)
			return null; // No base path to check

		// Check cache first
		final Either<Path, Boolean> matches = basePathToRelativePath.get(basePath);
		if (matches != null) {
			return matches.isLeft() //
					? matches.getLeft() // Cached positive result
					: null; // Cached negative result
		}

		// Compute for the first time
		if (path.startsWith(basePath)) {
			final Path relativePath = basePath.relativize(path);
			basePathToRelativePath.put(basePath, Either.forLeft(relativePath)); // Cache positive result
			return relativePath;
		}

		// Path is not under base path, cache negative result
		basePathToRelativePath.put(basePath, Either.forRight(Boolean.FALSE));
		return null;
	}

	private static @Nullable PathPatternMatcher getPathPatternMatcher(final FileSystemWatcher fileSystemMatcher,
			final @Nullable Path basePath) {
		final Either<String, RelativePattern> globPattern = fileSystemMatcher.getGlobPattern();
		if (globPattern.isLeft()) {
			final String pattern = globPattern.getLeft();
			return pattern.isBlank() //
					? null // Invalid pattern, ignore the watcher
					: new PathPatternMatcher(pattern, basePath);
		}
		final RelativePattern relativePattern = globPattern.getRight();
		// Implement relative pattern like glob string pattern
		// by waiting for finding a concrete use case.
		final String pattern = relativePattern.getPattern();
		if (pattern.isBlank())
			return null; // Invalid pattern, ignore the watcher

		final Path relativeBasePath = getRelativeBasePath(relativePattern.getBaseUri());
		if (relativeBasePath == null) {
			// Invalid baseUri, ignore the watcher
			return null;
		}
		return new PathPatternMatcher(pattern, relativeBasePath);
	}

	private static @Nullable Path getRelativeBasePath(final @Nullable Either<WorkspaceFolder, String> baseUri) {
		if (baseUri == null)
			return null;

		String baseDir = null;
		if (baseUri.isRight()) {
			baseDir = baseUri.getRight();
		} else if (baseUri.isLeft()) {
			final var workspaceFolder = baseUri.getLeft();
			baseDir = workspaceFolder.getUri();
		}
		if (baseDir == null || baseDir.isBlank())
			return null;

		try {
			return Paths.get(URI.create(baseDir));
		} catch (final Exception ex) {
			// Invalid baseUri, ignore the watcher
			LanguageServerPlugin.logWarning(ex.getMessage(), ex);
		}
		return null;
	}

	private static void tryAddingMatcher(final PathPatternMatcher matcher,
			final Map<Integer, List<PathPatternMatcher>> matchers, final @Nullable Integer watcherKind,
			final int kind) {
		if (!isWatchKind(watcherKind, kind))
			return;

		final List<PathPatternMatcher> matchersForKind = matchers.computeIfAbsent(kind, k -> new ArrayList<>());
		matchersForKind.add(matcher);
	}

	/**
	 * Checks if the combined value contains a specific kind.
	 */
	private static boolean isWatchKind(final @Nullable Integer watcherKind, final int kind) {
		return watcherKind == null || (watcherKind & kind) != 0;
	}
}
