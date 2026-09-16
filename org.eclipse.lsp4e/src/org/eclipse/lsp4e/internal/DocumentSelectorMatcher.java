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
package org.eclipse.lsp4e.internal;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.internal.files.PathPatternMatcher;
import org.eclipse.lsp4j.DocumentFilter;

/**
 * A compiled LSP {@code DocumentSelector}: decides whether a document URI is matched by the
 * {@code documentSelector} of a dynamic capability registration.
 * <p>
 * Per the LSP specification a selector is a list of {@link DocumentFilter}s combined with OR, and
 * within one filter the properties that are set ({@code language}, {@code scheme}, {@code pattern})
 * are combined with AND. A filter's glob pattern matches against the document's absolute path,
 * except for a {@code RelativePattern}, which matches against the path relative to its own base URI.
 * <p>
 * Compile a selector once (when the registration arrives) via {@link #compile(List)} and reuse the
 * instance for all subsequent queries; the glob matchers are precompiled.
 */
public final class DocumentSelectorMatcher {

	/**
	 * Resolves the LSP language id of the document at a given URI, i.e. the id that is (or would be)
	 * sent to this filter's server in {@code textDocument/didOpen} for that document.
	 */
	@FunctionalInterface
	public interface LanguageIdResolver {
		@Nullable
		String getLanguageId(URI uri);
	}

	private record CompiledFilter(@Nullable String language, @Nullable String scheme,
			@Nullable PathPatternMatcher pattern) {

		boolean matches(final URI uri, final LanguageIdResolver languageIdResolver) {
			if (language != null && !language.equals(languageIdResolver.getLanguageId(uri))) {
				return false;
			}
			if (scheme != null && !scheme.equalsIgnoreCase(uri.getScheme())) {
				return false;
			}
			if (pattern != null && !matchesPattern(uri)) {
				return false;
			}
			return true;
		}

		private boolean matchesPattern(final URI uri) {
			final PathPatternMatcher pattern = this.pattern;
			if (pattern == null) {
				return false;
			}
			final Path path;
			try {
				path = Paths.get(uri);
			} catch (final Exception ex) {
				// no file-system path can be derived (e.g. non-file scheme): the pattern cannot match
				return false;
			}
			final Path basePath = pattern.getBasePath();
			if (basePath != null) { // RelativePattern: match relative to its base URI
				if (!path.startsWith(basePath)) {
					return false;
				}
				return pattern.matches(basePath.relativize(path));
			}
			return pattern.matches(path);
		}
	}

	private final List<CompiledFilter> filters;

	private DocumentSelectorMatcher(final List<CompiledFilter> filters) {
		this.filters = filters;
	}

	/**
	 * Compiles the {@code documentSelector} of a registration.
	 *
	 * @return the compiled selector, or {@code null} for a {@code null} selector, which per the LSP
	 *         specification means the registration applies to every document the server is used for
	 */
	public static @Nullable DocumentSelectorMatcher compile(final @Nullable List<DocumentFilter> documentSelector) {
		if (documentSelector == null) {
			return null;
		}
		final var filters = new ArrayList<CompiledFilter>(documentSelector.size());
		for (final DocumentFilter filter : documentSelector) {
			final String language = isNullOrBlank(filter.getLanguage()) ? null : filter.getLanguage();
			final String scheme = isNullOrBlank(filter.getScheme()) ? null : filter.getScheme();
			final PathPatternMatcher pattern = filter.getPattern() == null ? null
					: PathPatternMatcher.fromGlobPattern(filter.getPattern(), null);
			if (language == null && scheme == null && pattern == null) {
				continue; // the spec requires at least one property to be set; ignore invalid filters
			}
			filters.add(new CompiledFilter(language, scheme, pattern));
		}
		// a non-null selector without any usable filter matches no documents
		return new DocumentSelectorMatcher(List.copyOf(filters));
	}

	/** @return whether any filter of this selector matches the document at the given URI */
	public boolean matches(final URI uri, final LanguageIdResolver languageIdResolver) {
		return filters.stream().anyMatch(filter -> filter.matches(uri, languageIdResolver));
	}

	private static boolean isNullOrBlank(final @Nullable String value) {
		return value == null || value.isBlank();
	}
}
