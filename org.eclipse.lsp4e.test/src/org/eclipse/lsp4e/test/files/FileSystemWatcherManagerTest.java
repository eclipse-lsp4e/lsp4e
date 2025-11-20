/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc. and others.
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
package org.eclipse.lsp4e.test.files;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4e.internal.files.FileSystemWatcherManager;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.WatchKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Based on https://github.com/redhat-developer/lsp4ij/blob/6f41f6d22a7146f31e0218cb459513abd5dc16d3/src/test/java/com/redhat/devtools/lsp4ij/features/files/FileSystemWatcherManagerTest.java

/**
 * Basic glob pattern tests for {@link FileSystemWatcherManager}.
 * <p>
 * These are adapted from the LSP4IJ test suite to validate that the
 * Eclipse-side implementation behaves consistently for typical patterns and
 * watch kinds.
 */
class FileSystemWatcherManagerTest {

	private static final String DEFAULT_WATCHER_ID = "default";

	private final Path projectDir = Paths.get("current-project").toAbsolutePath();
	private final Path externalDir = Paths.get("external-project").toAbsolutePath();

	private final FileSystemWatcherManager manager = new FileSystemWatcherManager(projectDir);

	@BeforeEach
	void setUp() {
		manager.clear();
	}

	@AfterEach
	void tearDown() {
		manager.clear();
	}

	@Test
	void sapCdsPatterns() {
		// Patterns adapted from LSP4IJ sap_cds_ls test
		registerWatchers(DEFAULT_WATCHER_ID, List.of( //
				new FileSystemWatcher(Either.forLeft("package.json"),
						Integer.valueOf(WatchKind.Create | WatchKind.Change | WatchKind.Delete)), new FileSystemWatcher(Either.forLeft("{.git,.cds}ignore"),
						Integer.valueOf(WatchKind.Create | WatchKind.Change | WatchKind.Delete)), new FileSystemWatcher(Either.forLeft(".cdsrc.json"),
						Integer.valueOf(WatchKind.Create | WatchKind.Change | WatchKind.Delete)), new FileSystemWatcher(Either.forLeft("**/{_i18n,i18n}/i18n{*.properties,*.json,*.csv}"),
						Integer.valueOf(WatchKind.Create | WatchKind.Change | WatchKind.Delete))));

		// Match package.json at project root
		assertMatchFile(projectDir.resolve("package.json").toUri(), WatchKind.Create);
		assertMatchFile(projectDir.resolve("package.json").toUri(), WatchKind.Change);
		assertMatchFile(projectDir.resolve("package.json").toUri(), WatchKind.Delete);

		// Non-matching names/locations
		assertNoMatchFile(projectDir.resolve("package.jso").toUri(), WatchKind.Create);
		assertNoMatchFile(projectDir.resolve("foo").resolve("package.json").toUri(), WatchKind.Create);
		assertNoMatchFile(externalDir.resolve("package.json").toUri(), WatchKind.Create);

		// Match {.git,.cds}ignore at project root
		assertMatchFile(projectDir.resolve(".gitignore").toUri(), WatchKind.Create);
		assertNoMatchFile(projectDir.resolve("gitignore").toUri(), WatchKind.Create);

		// Match .cdsrc.json at project root
		assertMatchFile(projectDir.resolve(".cdsrc.json").toUri(), WatchKind.Create);
		assertNoMatchFile(projectDir.resolve("cdsrc.json").toUri(), WatchKind.Create);

		// Match **/{_i18n,i18n}/i18n{*.properties,*.json,*.csv}
		assertMatchFile(projectDir.resolve("_i18n").resolve("i18n.properties").toUri(), WatchKind.Create);
		assertMatchFile(projectDir.resolve("i18n").resolve("i18n.json").toUri(), WatchKind.Create);
		assertNoMatchFile(projectDir.resolve("other").resolve("i18n.properties").toUri(), WatchKind.Create);
	}

	@Test
	void watcherKindFiltering() {
		// Register patterns with different explicit kinds
		registerWatchers("watcher-kind", List.of(
				new FileSystemWatcher(Either.forLeft("**/*.kind_null"), null),
				new FileSystemWatcher(Either.forLeft("**/*.kind_7"), Integer.valueOf(7)),
				new FileSystemWatcher(Either.forLeft("**/*.kind_Create"), Integer.valueOf(WatchKind.Create)),
				new FileSystemWatcher(Either.forLeft("**/*.kind_Change"), Integer.valueOf(WatchKind.Change)),
				new FileSystemWatcher(Either.forLeft("**/*.kind_Delete"), Integer.valueOf(WatchKind.Delete))));

		URI createUri = projectDir.resolve("foo.kind_Create").toUri();
		URI changeUri = projectDir.resolve("foo.kind_Change").toUri();
		URI deleteUri = projectDir.resolve("foo.kind_Delete").toUri();
		URI nullUri = projectDir.resolve("foo.kind_null").toUri();
		URI anyUri = projectDir.resolve("foo.kind_7").toUri();

		// kind null -> all kinds
		assertMatchFile(nullUri, WatchKind.Create);
		assertMatchFile(nullUri, WatchKind.Change);
		assertMatchFile(nullUri, WatchKind.Delete);

		// kind 7 -> all kinds
		assertMatchFile(anyUri, WatchKind.Create);
		assertMatchFile(anyUri, WatchKind.Change);
		assertMatchFile(anyUri, WatchKind.Delete);

		// specific kinds
		assertMatchFile(createUri, WatchKind.Create);
		assertNoMatchFile(createUri, WatchKind.Change);
		assertNoMatchFile(createUri, WatchKind.Delete);

		assertNoMatchFile(changeUri, WatchKind.Create);
		assertMatchFile(changeUri, WatchKind.Change);
		assertNoMatchFile(changeUri, WatchKind.Delete);

		assertNoMatchFile(deleteUri, WatchKind.Create);
		assertNoMatchFile(deleteUri, WatchKind.Change);
		assertMatchFile(deleteUri, WatchKind.Delete);
	}

	@Test
	void globMatchingSimple() {
		// Simple glob patterns, adapted from VS Code tests
		registerGlobWatcher("node_modules");
		assertGlobMatch("node_modules");
		assertNoGlobMatch("node_module");
		assertNoGlobMatch("test/node_modules");

		registerGlobWatcher("test.txt");
		assertGlobMatch("test.txt");

		// Windows file systems do not allow '?' in file names. Keep the VS Code
		// style assertion only on non-Windows platforms.
		if (!isWindows()) {
			assertNoGlobMatch("test?txt");
		}
		assertNoGlobMatch("/text.txt");
		assertNoGlobMatch("test/test.txt");
	}

	private void registerWatchers(String id, List<FileSystemWatcher> watchers) {
		manager.registerFileSystemWatchers(id, watchers);
	}

	private void assertMatchFile(URI uri, int kind) {
		boolean matched = manager.isMatchFilePattern(uri, kind);
		assertTrue(matched, () -> uri + " should match for kind " + kind);
	}

	private void assertNoMatchFile(URI uri, int kind) {
		boolean matched = manager.isMatchFilePattern(uri, kind);
		assertFalse(matched, () -> uri + " should not match for kind " + kind);
	}

	private void registerGlobWatcher(String pattern) {
		manager.clear();
		manager.registerFileSystemWatchers(DEFAULT_WATCHER_ID,
				List.of(new FileSystemWatcher(Either.forLeft(pattern), Integer.valueOf(WatchKind.Create))));
	}

	private void assertGlobMatch(String relativePath) {
		assertGlobMatch(relativePath, true);
	}

	private void assertNoGlobMatch(String relativePath) {
		assertGlobMatch(relativePath, false);
	}

	private void assertGlobMatch(String relativePath, boolean expected) {
		URI uri;
		if (relativePath.startsWith("/")) {
			uri = projectDir.resolve(relativePath.substring(1)).toUri();
		} else {
			uri = projectDir.resolve(relativePath).toUri();
		}
		boolean matched = manager.isMatchFilePattern(uri, WatchKind.Create);
		if (expected) {
			assertTrue(matched, () -> "Pattern should match " + uri);
		} else {
			assertFalse(matched, () -> "Pattern should not match " + uri);
		}
	}

	private static boolean isWindows() {
		String os = System.getProperty("os.name");
		return os != null && os.toLowerCase().contains("win");
	}
}
