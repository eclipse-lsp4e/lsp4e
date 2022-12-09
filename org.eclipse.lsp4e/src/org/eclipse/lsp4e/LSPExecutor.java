package org.eclipse.lsp4e;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;

public abstract class LSPExecutor {


	// Pluggable strategy for getting the set of LSWrappers to dispatch operations on
	protected abstract List<CompletableFuture<LanguageServerWrapper>> getServers();

	private @NonNull Predicate<ServerCapabilities> filter = s -> true;

	/**
	 * Runs an operation on all applicable language servers, returning an async result that will consist
	 * of all non-empty individual results
	 *
	 * @param <T> Type of result being computed on the language server(s)
	 * @param fn An individual operation to be performed on the language server, which following the LSP4j API
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. Note that the supplied fn will receive two arguments, the wrapper for the language server,
	 * as well as the language server itself.
	 *
	 * @return Async result
	 */
	@NonNull
	public <T> CompletableFuture<@NonNull List<@NonNull T>> collectAll(BiFunction<LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		final CompletableFuture<List<T>> init = CompletableFuture.completedFuture(new ArrayList<T>());
		return getServers().stream()
			.map(wrapperFuture -> wrapperFuture
					.thenCompose(w -> w == null ? CompletableFuture.completedFuture((T) null) : w.execute(fn)))
			.reduce(init, LanguageServiceAccessor::combine, LanguageServiceAccessor::concatResults)

			// Ensure any subsequent computation added by caller does not block further incoming messages from language servers
			.thenApplyAsync(Function.identity());
	}

	/**
	 * Runs an operation on all applicable language servers, returning a list of asynchronous responses that can
	 * be used to instigate further processing as they complete individually
	 *
	 * @param <T> Type of result being computed on the language server(s)
	 * @param fn An individual operation to be performed on the language server, which following the LSP4j API
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. Note that the supplied fn will receive two arguments, the wrapper for the language server,
	 * as well as the language server itself.
	 *
	 * @return
	 */
	@NonNull
	public <T> List<@NonNull CompletableFuture<@Nullable T>> computeAll(BiFunction<LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		return getServers().stream()
				.map(wrapperFuture -> wrapperFuture
						.thenCompose(w -> w == null ? null : w.execute(fn).thenApplyAsync(Function.identity())))
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}

	/**
	 * Runs an operation on all applicable language servers, returning an async result that will receive the first
	 * non-null response
	 * @param <T> Type of result being computed on the language server(s)
	 * @param fn An individual operation to be performed on the language server, which following the LSP4j API
	 * will return a <code>CompletableFuture&lt;T&gt;</code>. Note that the supplied fn will receive two arguments, the wrapper for the language server,
	 * as well as the language server itself.
	 *
	 * @return An asynchronous result that will complete with a populated <code>Optional&lt;T&gt;</code> from the first
	 * non-empty response, and with an empty <code>Optional</code> if none of the servers returned a non-empty result.
	 */
	public <T> CompletableFuture<Optional<T>> computeFirst(BiFunction<LanguageServerWrapper, LanguageServer, ? extends CompletionStage<T>> fn) {
		final List<CompletableFuture<LanguageServerWrapper>> servers = getServers();
		if (servers.isEmpty()) {
			return CompletableFuture.completedFuture(Optional.empty());
		}
		final CompletableFuture<Optional<T>> result = new CompletableFuture<>();

		// Dispatch the request to the servers, appending a step to each such that
		// the first to return a non-null result will be the overall result.
		// CompletableFuture.anyOf() almost does what we need, but we don't want
		// a quickly-returned null to trump a slowly-returned result
		final List<CompletableFuture<T>> intermediate = servers.stream()
				.map(wrapperFuture -> wrapperFuture
						.thenCompose(w -> w == null ? null : w.execute(fn)))
				.filter(Objects::nonNull)
				.map(cf -> cf.thenApply(t -> {
					if (!isEmpty(t)) { // TODO: Does this need to be a supplied function to handle all cases?
						result.complete(Optional.of(t));
					}
					return t;
				})).collect(Collectors.toList());

		// Make sure that if the servers all return null then we give up and supply an empty result
		// rather than potentially waiting forever...
		final CompletableFuture<?> fallback = CompletableFuture.allOf(intermediate.toArray(new CompletableFuture[intermediate.size()]));
		fallback.thenRun(() -> result.complete(Optional.empty()));

		return result.thenApplyAsync(Function.identity());
	}

	/**
	 *
	 * @param project
	 * @return Executor that will run requests on servers appropriate to the supplied project
	 */
	public static LSPProjectExecutor forProject(final IProject project) {
		return new LSPProjectExecutor(project);
	}

	/**
	 *
	 * @param document
	 * @return Executor that will run requests on servers appropriate to the supplied document
	 */
	public static LSPDocumentExecutor forDocument(final @NonNull IDocument document) {
		return new LSPDocumentExecutor(document);
	}


	public LSPExecutor withFilter(final @NonNull Predicate<ServerCapabilities> filter) {
		this.filter = filter;
		return this;
	}


	/**
	 * Executor that will run requests on the set of language servers appropriate for the supplied document
	 *
	 */
	public static class LSPDocumentExecutor extends LSPExecutor {

		private final @NonNull IDocument document;

		private LSPDocumentExecutor(final @NonNull IDocument document) {
			this.document = document;
		}

		public @NonNull IDocument getDocument() {
			return this.document;
		}

		@Override
		protected List<CompletableFuture<LanguageServerWrapper>> getServers() {
			// Compute list of servers from document & filter
			return LanguageServiceAccessor.getLSWrappers(this.document).stream()
				.map(wrapper -> wrapper.connectIf(this.document, getFilter()))
				.collect(Collectors.toList());
		}
	}

	/**
	 * Executor that will run requests on the set of language servers appropriate for the supplied project
	 *
	 */
	public static class LSPProjectExecutor extends LSPExecutor {

		private final IProject project;

		private boolean restartStopped = true;

		LSPProjectExecutor(final IProject project) {
			this.project = project;
		}

		/**
		 * If called, this executor will not attempt to any servers that previously started in this session
		 * but have since shut down
		 * @return
		 */
		public LSPProjectExecutor excludeInactive() {
			this.restartStopped = false;
			return this;
		}

		@Override
		protected List<CompletableFuture<LanguageServerWrapper>> getServers() {
			// Compute list of servers from project & filter

			return LanguageServiceAccessor.getWrappers(this.project, getFilter(), !this.restartStopped);
		}

	}


	public Predicate<ServerCapabilities> getFilter() {
		return this.filter;
	}

	private static <T> boolean isEmpty(final T t) {
		return t == null || ((t instanceof List) && ((List<?>)t).isEmpty());
	}

}