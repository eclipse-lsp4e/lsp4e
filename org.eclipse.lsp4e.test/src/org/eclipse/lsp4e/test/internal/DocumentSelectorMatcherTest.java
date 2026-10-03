/*******************************************************************************
 * Copyright (c) 2026 Cocotec Ltd and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Ahmed Hussain (Cocotec Ltd) - initial implementation
 *
 *******************************************************************************/
package org.eclipse.lsp4e.test.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.internal.DocumentSelectorMatcher;
import org.eclipse.lsp4e.internal.DocumentSelectorMatcher.LanguageIdResolver;
import org.eclipse.lsp4j.DocumentFilter;
import org.eclipse.lsp4j.RelativePattern;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class DocumentSelectorMatcherTest {

	private static final URI RUST_FILE = URI.create("file:///proj/src/foo.rs");
	private static final URI MANIFEST_FILE = URI.create("file:///proj/Cargo.toml");
	private static final URI UNTITLED_FILE = URI.create("untitled:Untitled-1");

	private final LanguageIdResolver languageIds = Map.of( //
			RUST_FILE, "rs", //
			MANIFEST_FILE, "toml")::get;

	private static DocumentSelectorMatcher compile(DocumentFilter... filters) {
		final DocumentSelectorMatcher matcher = DocumentSelectorMatcher.compile(List.of(filters));
		assertNotNull(matcher);
		return matcher;
	}

	private static DocumentFilter filter(@Nullable String language, @Nullable String scheme,
			@Nullable String pattern) {
		final var filter = new DocumentFilter();
		filter.setLanguage(language);
		filter.setScheme(scheme);
		if (pattern != null) {
			filter.setPattern(pattern);
		}
		return filter;
	}

	@Test
	void nullSelectorCompilesToNull() {
		assertNull(DocumentSelectorMatcher.compile(null));
	}

	@Test
	void emptySelectorMatchesNothing() {
		final DocumentSelectorMatcher matcher = compile();
		assertFalse(matcher.matches(RUST_FILE, languageIds));
	}

	@Test
	void filterWithNoUsablePropertyIsIgnored() {
		// the spec requires at least one property to be set
		final DocumentSelectorMatcher matcher = compile(filter(null, null, null), filter("", "", null));
		assertFalse(matcher.matches(RUST_FILE, languageIds));
	}

	@Test
	void languageMatching() {
		final DocumentSelectorMatcher matcher = compile(filter("rs", null, null));
		assertTrue(matcher.matches(RUST_FILE, languageIds));
		assertFalse(matcher.matches(MANIFEST_FILE, languageIds));
		// unresolvable language id: no match
		assertFalse(matcher.matches(UNTITLED_FILE, languageIds));
	}

	@Test
	void schemeMatchingIsCaseInsensitive() {
		final DocumentSelectorMatcher matcher = compile(filter(null, "FILE", null));
		assertTrue(matcher.matches(RUST_FILE, languageIds));
		assertFalse(matcher.matches(UNTITLED_FILE, languageIds));
	}

	@Test
	void patternMatchesAbsolutePath() {
		assertTrue(compile(filter(null, null, "**/*.rs")).matches(RUST_FILE, languageIds));
		assertTrue(compile(filter(null, null, "**/Cargo.toml")).matches(MANIFEST_FILE, languageIds));
		assertFalse(compile(filter(null, null, "**/*.rs")).matches(MANIFEST_FILE, languageIds));
	}

	@Test
	void patternNeverMatchesNonFileUris() {
		final DocumentSelectorMatcher matcher = compile(filter(null, null, "**/*"));
		assertFalse(matcher.matches(UNTITLED_FILE, languageIds));
	}

	@Test
	void relativePatternMatchesRelativeToItsBase() {
		final var relativePattern = new RelativePattern();
		relativePattern.setBaseUri(Either.forRight("file:///proj/src"));
		relativePattern.setPattern("*.rs");
		final var filter = new DocumentFilter();
		filter.setPattern(relativePattern);
		final DocumentSelectorMatcher matcher = compile(filter);

		assertTrue(matcher.matches(RUST_FILE, languageIds));
		// same file name outside the base: no match
		assertFalse(matcher.matches(URI.create("file:///elsewhere/foo.rs"), languageIds));
	}

	@Test
	void propertiesOfOneFilterAreConjunctive() {
		// language matches but scheme does not
		assertFalse(compile(filter("rs", "untitled", null)).matches(RUST_FILE, languageIds));
		// language matches but pattern does not
		assertFalse(compile(filter("rs", null, "**/*.toml")).matches(RUST_FILE, languageIds));
		// all set properties match
		assertTrue(compile(filter("rs", "file", "**/*.rs")).matches(RUST_FILE, languageIds));
	}

	@Test
	void filtersAreDisjunctive() {
		final DocumentSelectorMatcher matcher = compile(filter("rs", null, null),
				filter(null, null, "**/Cargo.toml"));
		assertTrue(matcher.matches(RUST_FILE, languageIds));
		assertTrue(matcher.matches(MANIFEST_FILE, languageIds));
		assertFalse(matcher.matches(URI.create("file:///proj/readme.md"), languageIds));
	}
}
