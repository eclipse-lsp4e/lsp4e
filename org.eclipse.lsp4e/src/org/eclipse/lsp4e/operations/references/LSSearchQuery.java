/*******************************************************************************
 * Copyright (c) 2016-23 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Mickael Istria (Red Hat Inc.) - initial implementation
 *   Michał Niewrzał (Rogue Wave Software Inc.)
 *   Angelo Zerr <angelo.zerr@gmail.com> - fix Bug 526255
 *   Lucas Bullen (Red Hat Inc.) - [Bug 517428] Requests sent before initialization
 *******************************************************************************/
package org.eclipse.lsp4e.operations.references;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.search.internal.ui.text.FileMatch;
import org.eclipse.search.internal.ui.text.FileSearchQuery;
import org.eclipse.search.internal.ui.text.LineElement;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.text.Match;

/**
 * {@link ISearchQuery} implementation for LSP.
 */
public class LSSearchQuery extends FileSearchQuery {

	private final IDocument document;
	private final int offset;

	private @Nullable LSSearchResult result;

	/**
	 * LSP search query to "Find references" from the given offset of the given
	 * {@link LanguageServerDocumentExecutor}.
	 *
	 * @param offset
	 * @param document
	 */
	public LSSearchQuery(int offset, IDocument document) {
		super("", false, false, true, true, null); //$NON-NLS-1$
		this.document = document;
		this.offset = offset;
	}

	@Override
	public IStatus run(@Nullable IProgressMonitor monitor) throws OperationCanceledException {
		getSearchResult().removeAll();

		try {
			// Execute LSP "references" service
			final var params = new ReferenceParams();
			params.setContext(new ReferenceContext(false));
			params.setTextDocument(castNonNull(LSPEclipseUtils.toTextDocumentIdentifier(document)));
			params.setPosition(LSPEclipseUtils.toPosition(offset, document));

			List<CompletableFuture<@Nullable List<? extends Location>>> requests = LanguageServers.forDocument(document).withCapability(ServerCapabilities::getReferencesProvider)
				.computeAll(languageServer -> languageServer.getTextDocumentService().references(params));
			CompletableFuture<?>[] populateUIFutures = requests.stream().map(request ->
				request.thenAcceptAsync(locations -> {
						final var result = this.result;
						if (locations != null && result != null) {
							// Convert each LSP Location to a Match search.
							locations.stream() //
								.filter(Objects::nonNull) //
								.map(LSSearchQuery::toMatch) //
								.filter(Objects::nonNull) //
								.forEach(result::addMatch);
						}
				})).toArray(CompletableFuture[]::new);
			CompletableFuture.allOf(populateUIFutures).join();
			return Status.OK_STATUS;
		} catch (Exception ex) {
			return new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID,
					ex.getMessage(), ex);
		}
	}

	/**
	 * Convert the given LSP {@link Location} to Eclipse search {@link Match}.
	 *
	 * @param location
	 *            the LSP location to convert.
	 * @return the converted Eclipse search {@link Match}.
	 */
	private static @Nullable Match toMatch(Location location) {
		IResource resource = LSPEclipseUtils.findResourceFor(location.getUri());
		if (resource != null) {
			IDocument document = LSPEclipseUtils.getExistingDocument(resource);
			boolean temporaryLoadDocument = document == null;
			if (temporaryLoadDocument) {
				document = LSPEclipseUtils.getDocument(resource);
			}
			if (document != null) {
				try {
					int startOffset = LSPEclipseUtils.toOffset(location.getRange().getStart(), document);
					int endOffset = LSPEclipseUtils.toOffset(location.getRange().getEnd(), document);

					IRegion lineInformation = document.getLineInformationOfOffset(startOffset);
					final var lineEntry = new LineElement(resource, document.getLineOfOffset(startOffset) + 1,
							lineInformation.getOffset(),
							document.get(lineInformation.getOffset(), lineInformation.getLength()));
					return new FileMatch((IFile) resource, startOffset, endOffset - startOffset, lineEntry);
				} catch (BadLocationException ex) {
					LanguageServerPlugin.logError(ex);
				} finally {
					if (temporaryLoadDocument) {
						try {
							FileBuffers.getTextFileBufferManager().disconnect(resource.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
						} catch (CoreException e) {
							LanguageServerPlugin.logError(e);
						}
					}
				}
			}

			Position startPosition = location.getRange().getStart();
			final var lineEntry = new LineElement(resource, startPosition.getLine() + 1, 0,
					String.format("%s:%s", startPosition.getLine(), startPosition.getCharacter())); //$NON-NLS-1$
			return new FileMatch((IFile) resource, 0, 0, lineEntry);
		}
		try {
			return URIMatch.create(location);
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
			return null;
		}
	}

	@Override
	public LSSearchResult getSearchResult() {
		var result = this.result;
		if (result == null) {
			result = this.result = new LSSearchResult(this);
		}
		return result;
	}

	@Override
	public String getLabel() {
		return Messages.LSSearchQuery_label;
	}

	@Override
	public boolean canRerun() {
		return true;
	}

	@Override
	public boolean canRunInBackground() {
		return true;
	}

	@Override
	public boolean isFileNameSearch() {
		return false;
	}
}
