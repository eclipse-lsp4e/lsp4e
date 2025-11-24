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
package org.eclipse.lsp4e.operations.rename;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerProjectExecutor;
import org.eclipse.lsp4e.internal.files.PathPatternMatcher;
import org.eclipse.lsp4j.FileOperationFilter;
import org.eclipse.lsp4j.FileOperationOptions;
import org.eclipse.lsp4j.FileOperationPatternKind;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;

/**
 * Internal class, only public to be accessible by test cases.
 *
 * @noreference
 */
public final class LSPFileOperationParticipantSupport {

	/**
	 * Hard timeout for willCreate/willRename/willDelete requests to avoid UI hangs
	 * when a server advertises support but never responds.
	 */
	private static final long FILE_OP_TIMEOUT_SECONDS = 10;

	public static <P> @Nullable Change computePreChange(final String changeName, final P params,
			final IResource resource,
			final Function<FileOperationsServerCapabilities, @Nullable FileOperationOptions> optionsProvider,
			final BiFunction<WorkspaceService, P, CompletableFuture<@Nullable WorkspaceEdit>> request) {
		return computePreChange(changeName, params, createFileOperationExecutor(resource, optionsProvider), request);
	}

	public static <P> @Nullable Change computePreChange(final String changeName, final P params,
			final LanguageServerProjectExecutor executor,
			final BiFunction<WorkspaceService, P, CompletableFuture<@Nullable WorkspaceEdit>> request) {
		final CompositeChange[] changes = executor.collectAll((wrapper, ls) -> request //
				.apply(ls.getWorkspaceService(), params) //
				.orTimeout(FILE_OP_TIMEOUT_SECONDS, TimeUnit.SECONDS).exceptionally(ex -> {
					LanguageServerPlugin.logWarning(
							"File operation pre-change '" + changeName + "' failed or timed out for server: " //$NON-NLS-1$ //$NON-NLS-2$
									+ wrapper.serverDefinition.label,
							ex);
					return null;
				}).thenApply(edits -> edits == null || isEmptyEdit(edits) //
						? (@Nullable CompositeChange) null
						: LSPEclipseUtils.toCompositeChange(edits, wrapper.serverDefinition.label))) //
				.join() //
				.stream() //
				.filter(Objects::nonNull) //
				.toArray(CompositeChange[]::new);

		return switch (changes.length) {
		case 0 -> null;
		case 1 -> changes[0];
		default -> new CompositeChange(changeName, changes);
		};
	}

	public static LanguageServerProjectExecutor createFileOperationExecutor(final IResource res,
			final Function<FileOperationsServerCapabilities, @Nullable FileOperationOptions> optionsProvider) {
		final var uri = LSPEclipseUtils.toUri(res);
		final var project = res.getProject();
		if (uri == null) {
			// No URI means we cannot match any file operation filters; return an executor
			// that will not match any servers.
			return LanguageServers.forProject(project).withFilter(capabilities -> false);
		}

		final var path = Path.of(uri);
		return LanguageServers.forProject(project).withFilter(capabilities -> {
			final var workspace = capabilities.getWorkspace();
			if (workspace == null)
				return false;
			final var fileOps = workspace.getFileOperations();
			if (fileOps == null)
				return false;

			final var options = optionsProvider.apply(fileOps);
			return matches(options, path, res.getType() == IResource.FOLDER);
		});
	}

	private static boolean isEmptyEdit(final WorkspaceEdit edits) {
		return (edits.getChanges() == null || edits.getChanges().isEmpty())
				&& (edits.getDocumentChanges() == null || edits.getDocumentChanges().isEmpty());
	}

	private static boolean matches(final @Nullable FileOperationOptions options, final Path path,
			final boolean isFolder) {
		if (options == null)
			return false;

		final var filters = options.getFilters();
		return filters.isEmpty() || filters.stream().anyMatch(filter -> matchesFilter(filter, path, isFolder));
	}

	private static boolean matchesFilter(final FileOperationFilter filter, final Path path, final boolean isFolder) {
		final var scheme = filter.getScheme();
		if (scheme != null && !"file".equalsIgnoreCase(scheme)) //$NON-NLS-1$
			return false;

		final var pattern = filter.getPattern();
		if (pattern.getGlob().isBlank())
			return false;

		final var matches = pattern.getMatches();
		if (FileOperationPatternKind.File.equals(matches) && isFolder)
			return false;

		if (FileOperationPatternKind.Folder.equals(matches) && !isFolder)
			return false;

		final String glob = pattern.getGlob();
		final var matcher = new PathPatternMatcher(glob, null);
		return matcher.matches(path);
	}

	private LSPFileOperationParticipantSupport() {
	}
}
