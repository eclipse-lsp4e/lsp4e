/*******************************************************************************
 * Copyright (c) 2016, 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Lucas Bullen (Red Hat Inc.) - [Bug 528333] Performance problem with diagnostics
 *******************************************************************************/
package org.eclipse.lsp4e.operations.diagnostics;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.lsp4e.IMarkerAttributeComputer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.ArrayUtil;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.texteditor.MarkerUtilities;

public class LSPDiagnosticsToMarkers implements Consumer<PublishDiagnosticsParams> {

	public static final String LSP_DIAGNOSTIC = "lspDiagnostic"; //$NON-NLS-1$
	public static final String LANGUAGE_SERVER_ID = "languageServerId"; //$NON-NLS-1$
	public static final String LS_DIAGNOSTIC_MARKER_TYPE = "org.eclipse.lsp4e.diagnostic"; //$NON-NLS-1$

	// LSP range attributes stored on markers to allow matching without computing
	// document offsets
	public static final String LSP_START_LINE = "lspStartLine"; //$NON-NLS-1$
	public static final String LSP_START_CHAR = "lspStartChar"; //$NON-NLS-1$
	public static final String LSP_END_LINE = "lspEndLine"; //$NON-NLS-1$
	public static final String LSP_END_CHAR = "lspEndChar"; //$NON-NLS-1$

	private static final IMarkerAttributeComputer DEFAULT_MARKER_ATTRIBUTE_COMPUTER = new IMarkerAttributeComputer() {

		@Override
		public void addMarkerAttributesForDiagnostic(Diagnostic diagnostic, @Nullable IDocument document,
				IResource resource, Map<String, Object> attributes) {
			// nothing to do
		}
	};

	private final String languageServerId;
	private final String markerType;
	private final IMarkerAttributeComputer markerAttributeComputer;

	public LSPDiagnosticsToMarkers(String serverId, @Nullable String markerType, @Nullable IMarkerAttributeComputer markerAttributeComputer) {
		this.languageServerId = serverId;
		this.markerType = markerType != null ? markerType : LS_DIAGNOSTIC_MARKER_TYPE;
		this.markerAttributeComputer = markerAttributeComputer == null ? DEFAULT_MARKER_ATTRIBUTE_COMPUTER
				: markerAttributeComputer;
	}

	public LSPDiagnosticsToMarkers(String serverId) {
		this(serverId, null, null);
	}

	@Override
	public void accept(PublishDiagnosticsParams diagnostics) {
		try {
			String uri = diagnostics.getUri();
			IResource resource = LSPEclipseUtils.findResourceFor(uri);
			if (resource != null && resource.isAccessible()) {
				updateMarkers(diagnostics, resource);
			} else {
				for (final IEditorReference editorRef : LSPEclipseUtils.findOpenEditorsFor(LSPEclipseUtils.toUri(uri))) {
					final ITextViewer textViewer = LSPEclipseUtils.getTextViewer(editorRef.getEditor(true));
					if (textViewer instanceof ISourceViewer sourceViewer) {
						updateEditorAnnotations(sourceViewer, diagnostics);
					}
				}
			}
		} catch (Exception ex) {
			LanguageServerPlugin.logError(ex);
		}
	}

	private void updateEditorAnnotations(ISourceViewer sourceViewer, PublishDiagnosticsParams diagnostics) {
		IAnnotationModel annotationModel = sourceViewer.getAnnotationModel();
		if (annotationModel == null) {
			return;
		}
		if (annotationModel instanceof IAnnotationModelExtension annotationModelExtension) {
			final var toRemove = new HashSet<Annotation>();
			annotationModel.getAnnotationIterator().forEachRemaining(annotation -> {
				if (annotation instanceof DiagnosticAnnotation) {
					toRemove.add(annotation);
				}
			});
			final var toAdd = new HashMap<Annotation, Position>(diagnostics.getDiagnostics().size(), 1.f);
			diagnostics.getDiagnostics().forEach(diagnostic -> {
				try {
					final var doc = sourceViewer.getDocument();
					if (doc != null) {
						int startOffset = LSPEclipseUtils.toOffset(diagnostic.getRange().getStart(), doc);
						int endOffset = LSPEclipseUtils.toOffset(diagnostic.getRange().getEnd(), doc);
						toAdd.put(new DiagnosticAnnotation(diagnostic, markerAttributeComputer::computeMarkerMessage),
								new Position(startOffset, endOffset - startOffset));
					}
				} catch (BadLocationException ex) {
					LanguageServerPlugin.logError(ex);
				}
			});
			annotationModelExtension.replaceAnnotations(toRemove.toArray(Annotation[]::new), toAdd);
		}
	}

	private WorkspaceJob updateMarkers(PublishDiagnosticsParams diagnostics, IResource resource) {
		final var job = new WorkspaceJob("Update markers from diagnostics") { //$NON-NLS-1$
			@Override
			public boolean belongsTo(@Nullable Object family) {
				return LanguageServerPlugin.FAMILY_UPDATE_MARKERS == family;
			}

			@Override
			public IStatus runInWorkspace(@Nullable IProgressMonitor monitor) throws CoreException {
				if (!resource.isAccessible()) {
					return Status.OK_STATUS;
				}

				// Ensure that markers updates are not done in parallel
				synchronized(resource) {
					doRun();
				}

				return Status.OK_STATUS;
			}

			private void doRun() throws CoreException {
				final var toDeleteMarkers = ArrayUtil
						.asHashSet(resource.findMarkers(markerType, true, IResource.DEPTH_ZERO));
				toDeleteMarkers
						.removeIf(marker -> !Objects.equals(marker.getAttribute(LANGUAGE_SERVER_ID, ""), languageServerId)); //$NON-NLS-1$
				final var newDiagnostics = new ArrayList<Diagnostic>();
				final var toUpdate = new HashMap<IMarker, Diagnostic>();

				// A language server can scan the whole project and generate diagnostics for files that are not currently open in the IDE
				// (the markers will show up in the problem view). If so, need to open the document temporarily but be sure to release it
				// when we're done
				IDocument existingDocument = LSPEclipseUtils.getExistingDocument(resource);
				final boolean hasDiagnostics = !diagnostics.getDiagnostics().isEmpty();
				boolean temporaryLoadDocument = false;
				IDocument document = existingDocument;
				if (hasDiagnostics && document == null) {
					final @Nullable URI resourceUri = LSPEclipseUtils.toUri(resource);
					if (resourceUri != null && "file".equals(resourceUri.getScheme())) { //$NON-NLS-1$
						temporaryLoadDocument = true;
						document = LSPEclipseUtils.getDocument(resource);
					}
				}
				for (Diagnostic diagnostic : diagnostics.getDiagnostics()) {
					IMarker associatedMarker = getExistingMarkerFor(document, diagnostic, toDeleteMarkers);
					if (associatedMarker == null) {
						newDiagnostics.add(diagnostic);
					} else {
						toDeleteMarkers.remove(associatedMarker);
						toUpdate.put(associatedMarker, diagnostic);
					}
				}

				try {
					for (Diagnostic diagnostic : newDiagnostics) {
						if (resource.exists()) {
							Map<String, Object> markerAttributes = computeMarkerAttributes(document, diagnostic, resource);
							resource.createMarker(markerType, markerAttributes);
						}
					}
					for (Entry<IMarker, Diagnostic> entry : toUpdate.entrySet()) {
						IMarker marker = entry.getKey();
						if (marker.exists()) {
							Map<String, Object> markerAttributes = computeMarkerAttributes(document, entry.getValue(), resource);
							updateMarker(markerAttributes, marker);
						}
					}
					toDeleteMarkers.forEach(t -> {
						try {
							t.delete();
						} catch (CoreException e) {
							LanguageServerPlugin.logError(e);
						}
					});
				} finally {
					if (document != null && temporaryLoadDocument) {
						FileBuffers.getTextFileBufferManager().disconnect(resource.getFullPath(), LocationKind.IFILE, new NullProgressMonitor());
					}
				}
			}
		};
		job.setSystem(true);
		job.setRule(resource.getWorkspace().getRuleFactory().markerRule(resource));
		job.schedule();
		return job;
	}

	protected void updateMarker(Map<String, Object> targetAttributes, IMarker marker) {
		try {
			if (!targetAttributes.equals(marker.getAttributes())) {
				marker.setAttributes(targetAttributes);
			}
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
		}
	}

	private @Nullable IMarker getExistingMarkerFor(@Nullable IDocument document, Diagnostic diagnostic, Set<IMarker> remainingMarkers) {
		final var markerMessage = markerAttributeComputer.computeMarkerMessage(diagnostic);
		final var rangeStart = diagnostic.getRange().getStart();
		final var rangeEnd = diagnostic.getRange().getEnd();
		for (IMarker marker : remainingMarkers) {
			if (!marker.exists()) {
				continue;
			}
			try {
				// Always require same server and same user-visible message
				if (!languageServerId.equals(marker.getAttribute(LANGUAGE_SERVER_ID))
						|| !markerMessage.equals(marker.getAttribute(IMarker.MESSAGE))) {
					continue;
				}
				if (document != null) {
					// Document available: match by precise character offsets
					final int startOff = LSPEclipseUtils.toOffset(rangeStart, document);
					final int endOff = LSPEclipseUtils.toOffset(rangeEnd, document);
					if (startOff == MarkerUtilities.getCharStart(marker)
							&& (endOff == MarkerUtilities.getCharEnd(marker) || rangeStart.equals(rangeEnd))) {
						return marker;
					}
				} else {
					// No document: match by raw LSP range attributes stored on the marker
					final int markerStartLine = marker.getAttribute(LSP_START_LINE, Integer.MIN_VALUE);
					final int markerStartChar = marker.getAttribute(LSP_START_CHAR, Integer.MIN_VALUE);
					final int markerEndLine = marker.getAttribute(LSP_END_LINE, Integer.MIN_VALUE);
					final int markerEndChar = marker.getAttribute(LSP_END_CHAR, Integer.MIN_VALUE);
					if (markerStartLine == Integer.MIN_VALUE || markerEndLine == Integer.MIN_VALUE) {
						continue;
					}
					final boolean sameStart = markerStartLine == rangeStart.getLine()
							&& markerStartChar == rangeStart.getCharacter();
					final boolean sameEnd = markerEndLine == rangeEnd.getLine()
							&& markerEndChar == rangeEnd.getCharacter();
					if (sameStart && (sameEnd || rangeStart.equals(rangeEnd))) {
						return marker;
					}
				}
			} catch (CoreException | BadLocationException e) {
				LanguageServerPlugin.logError(e);
			}
		}
		return null;
	}

	private Map<String, Object> computeMarkerAttributes(@Nullable IDocument document,
			Diagnostic diagnostic, IResource resource) {
		Either<String, Integer> code = diagnostic.getCode();
		if (code != null && code.isLeft()) {
			diagnostic.setCode(Either.forLeft(code.getLeft().intern()));
		}
		String source = diagnostic.getSource();
		if (source != null) {
			diagnostic.setSource(source.intern());
		}
		final var attributes = new HashMap<String, Object>(12);
		attributes.put(LSP_DIAGNOSTIC, diagnostic);
		attributes.put(LANGUAGE_SERVER_ID, languageServerId);
		attributes.put(IMarker.MESSAGE, markerAttributeComputer.computeMarkerMessage(diagnostic));
		attributes.put(IMarker.SEVERITY, LSPEclipseUtils.toEclipseMarkerSeverity(diagnostic.getSeverity()));

		final var rangeStart = diagnostic.getRange().getStart();
		final var rangeEnd = diagnostic.getRange().getEnd();
		attributes.put(IMarker.LINE_NUMBER, rangeStart.getLine() + 1);
		attributes.put(LSP_START_LINE, rangeStart.getLine());
		attributes.put(LSP_START_CHAR, rangeStart.getCharacter());
		attributes.put(LSP_END_LINE, rangeEnd.getLine());
		attributes.put(LSP_END_CHAR, rangeEnd.getCharacter());

		if (document != null) {
			int documentLength = document.getLength();
			int start;
			try {
				start = Math.min(LSPEclipseUtils.toOffset(rangeStart, document), documentLength);
			} catch (BadLocationException ex) {
				start = documentLength;
			}
			int end;
			try {
				end = Math.min(LSPEclipseUtils.toOffset(rangeEnd, document), documentLength);
			} catch (BadLocationException ex) {
				end = documentLength;
			}
			try {
				int lineOfStartOffset = document.getLineOfOffset(start);
				attributes.put(IMarker.LINE_NUMBER, lineOfStartOffset + 1);
				// Empty range arbitrary implementation: extend one char forward or backward if at EOL
				if (start == end && documentLength > end) {
					end++;
					if (document.getLineOfOffset(end) != lineOfStartOffset) {
						start--;
						end--;
					}
				}
			} catch (BadLocationException ex) {
				LanguageServerPlugin.logError(ex);
			}
			attributes.put(IMarker.CHAR_START, start);
			attributes.put(IMarker.CHAR_END, end);
		}

		markerAttributeComputer.addMarkerAttributesForDiagnostic(diagnostic, document, resource, attributes);

		return attributes;
	}
}
