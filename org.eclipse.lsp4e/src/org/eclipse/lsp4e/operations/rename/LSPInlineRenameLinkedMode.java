/*******************************************************************************
 * Copyright (c) 2025 Vegard IT GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Sebastian Thomschke (Vegard IT GmbH) - initial implementation.
 *******************************************************************************/
package org.eclipse.lsp4e.operations.rename;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.link.ILinkedModeListener;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedModeUI.ExitFlags;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.LinkedPositionGroup;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.internal.IdentifierUtil;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;

/**
 * Inline rename implementation using JFace linked mode.
 *
 * @noreference test only
 */
public final class LSPInlineRenameLinkedMode {

	private static final String INLINE_RENAME_PREFERENCE = "org.eclipse.lsp4e.inlineRename"; //$NON-NLS-1$
	private static final boolean INLINE_RENAME_ACTIVE_DEFAULT = true;

	public static final class PreferenceInitializer extends AbstractPreferenceInitializer {
		@Override
		public void initializeDefaultPreferences() {
			final var store = LanguageServerPlugin.getDefault().getPreferenceStore();
			store.setDefault(INLINE_RENAME_PREFERENCE, INLINE_RENAME_ACTIVE_DEFAULT);
		}
	}

	static boolean start(final IDocument document, final ITextViewer viewer, final int offset, final Shell shell) {
		if (!isInlineRenameEnabled()) {
			return false;
		}

		final var job = new Job(Messages.rename_title) {
			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				final var processor = new LSPRenameProcessor(document, offset);
				final var status = runPrepareRename(processor);
				if (status == null || status.hasFatalError()) {
					LanguageServerPlugin.logWarning(
							"Inline rename: prepareRename returned fatal status or null, falling back to dialog"); //$NON-NLS-1$
					scheduleDialogFallback(document, offset, shell);
					return Status.OK_STATUS;
				}

				final var prepareResult = processor.getPrepareRenameResult();
				final IRegion renameRegion;
				final String originalName;
				final List<IRegion> occurrences;
				try {
					if (prepareResult != null) {
						renameRegion = toRegion(document, offset, prepareResult);
					} else {
						// PrepareRename timed out or returned no result:
						// fall back to identifier at caret
						renameRegion = IdentifierUtil.computeIdentifierRegion(document, offset);
					}
					originalName = document.get(renameRegion.getOffset(), renameRegion.getLength());
					occurrences = collectSameFileOccurrences(document, offset, renameRegion);
				} catch (final BadLocationException ex) {
					LanguageServerPlugin.logError(ex);
					scheduleDialogFallback(document, offset, shell);
					return Status.OK_STATUS;
				}

				final var mode = new LSPInlineRenameLinkedMode(document, viewer, offset, renameRegion, originalName,
						occurrences, processor.getRefactoringServer());
				mode.start();
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.schedule();
		return true;
	}

	private static boolean isInlineRenameEnabled() {
		return LanguageServerPlugin.getDefault().getPreferenceStore().getBoolean(INLINE_RENAME_PREFERENCE);
	}

	private static @Nullable RefactoringStatus runPrepareRename(final LSPRenameProcessor processor) {
		try {
			return processor.checkInitialConditions(new NullProgressMonitor());
		} catch (final CoreException ex) {
			LanguageServerPlugin.logError(ex);
		} catch (final OperationCanceledException ex) {
			// Expected when the operation is cancelled
		}
		return null;
	}

	private static void scheduleDialogFallback(final IDocument document, final int offset, final Shell shell) {
		if (shell.isDisposed())
			return;

		shell.getDisplay().asyncExec(() -> {
			if (shell.isDisposed())
				return;
			final var processor = new LSPRenameProcessor(document, offset);
			final var refactoring = new ProcessorBasedRefactoring(processor);
			final var wizard = new LSPRenameRefactoringWizard(refactoring);
			final var operation = new RefactoringWizardOpenOperation(wizard);
			try {
				operation.run(shell, Messages.rename_title);
			} catch (final InterruptedException ex) {
				LanguageServerPlugin.logError(ex);
				Thread.currentThread().interrupt();
			}
		});
	}

	private static IRegion toRegion(final IDocument document, final int offset,
			final Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> prepare)
			throws BadLocationException {

		final Range range;
		if (prepare.isFirst()) {
			range = prepare.getFirst();
		} else if (prepare.isSecond()) {
			range = castNonNull(prepare.getSecond()).getRange();
		} else {
			// PrepareRenameDefaultBehavior: use word under caret
			range = IdentifierUtil.computeIdentifierRange(document, offset);
		}

		final int startOffset = LSPEclipseUtils.toOffset(range.getStart(), document);
		final int endOffset = LSPEclipseUtils.toOffset(range.getEnd(), document);
		return new Region(startOffset, endOffset - startOffset);
	}

	/**
	 * Collects all occurrences of the identifier in the same file using the LSP
	 * textDocument/documentHightlight request (when available).
	 *
	 * The returned list always contains {@code primaryRegion} as its first entry.
	 */
	private static List<IRegion> collectSameFileOccurrences(final IDocument document, final int offset,
			final IRegion primaryRegion) {
		final var regions = new ArrayList<IRegion>();
		regions.add(primaryRegion);

		try {
			final var textDocument = castNonNull(LSPEclipseUtils.toTextDocumentIdentifier(document));
			final var highlightParams = new DocumentHighlightParams(textDocument,
					LSPEclipseUtils.toPosition(offset, document));

			final var highlightLists = LanguageServers.forDocument(document) //
					.withCapability(ServerCapabilities::getDocumentHighlightProvider)
					.collectAll((w, ls) -> ls.getTextDocumentService().documentHighlight(highlightParams)) //
					.get(1, TimeUnit.SECONDS);

			for (final var highlights : highlightLists) {
				if (highlights == null) {
					continue;
				}
				highlights.stream().filter(Objects::nonNull).forEach(highlight -> {
					try {
						final int start = LSPEclipseUtils.toOffset(highlight.getRange().getStart(), document);
						final int end = LSPEclipseUtils.toOffset(highlight.getRange().getEnd(), document);
						final var region = new Region(start, end - start);
						if (regions.stream().noneMatch(
								r -> r.getOffset() == region.getOffset() && r.getLength() == region.getLength())) {
							regions.add(region);
						}
					} catch (final BadLocationException ex) {
						LanguageServerPlugin.logError(ex);
					}
				});
			}
		} catch (final BadLocationException | ExecutionException | TimeoutException | RuntimeException ex) {
			LanguageServerPlugin.logError(ex);
		} catch (final InterruptedException ex) {
			LanguageServerPlugin.logError(ex);
			Thread.currentThread().interrupt();
		}
		return regions;
	}

	private final IDocument document;
	private final ITextViewer viewer;
	private final int offset;
	private final IRegion renameRegion;
	private final String originalName;
	private final List<IRegion> occurrences;
	private final @Nullable LanguageServerWrapper refactoringServer;
	private @Nullable LinkedPosition linkedPosition;
	private final List<LinkedPosition> linkedPositions = new ArrayList<>();
	private final List<String> originalContents = new ArrayList<>();
	private volatile boolean cancelled = false;

	private LSPInlineRenameLinkedMode(final IDocument document, final ITextViewer viewer, final int offset,
			final IRegion renameRegion, final String originalName, final List<IRegion> occurrences,
			final @Nullable LanguageServerWrapper refactoringServer) {
		this.document = document;
		this.viewer = viewer;
		this.offset = offset;
		this.renameRegion = renameRegion;
		this.originalName = originalName;
		this.occurrences = occurrences;
		this.refactoringServer = refactoringServer;

		// Capture the original text for each occurrence so we can restore it on cancel.
		for (final var region : occurrences) {
			try {
				originalContents.add(document.get(region.getOffset(), region.getLength()));
			} catch (BadLocationException e) {
				// Fallback: use the primary name if reading fails
				originalContents.add(originalName);
				LanguageServerPlugin.logError(e);
			}
		}
	}

	private boolean doRename(final String newName) {
		try {
			final var params = new RenameParams();
			params.setPosition(LSPEclipseUtils.toPosition(offset, document));
			params.setTextDocument(castNonNull(LSPEclipseUtils.toTextDocumentIdentifier(document)));
			params.setNewName(newName);

			final WorkspaceEdit edit;
			final LanguageServerWrapper server = this.refactoringServer;
			if (server != null) {
				edit = server.execute(ls -> ls.getTextDocumentService().rename(params)).get(1, TimeUnit.SECONDS);
			} else {
				edit = LanguageServers.forDocument(document).withCapability(ServerCapabilities::getRenameProvider)
						.computeFirst(ls -> ls.getTextDocumentService().rename(params)).get(1, TimeUnit.SECONDS)
						.orElse(null);
			}
			if (edit == null) {
				return false;
			}
			LSPEclipseUtils.applyWorkspaceEdit(edit, Messages.rename_title);
			return true;
		} catch (final BadLocationException | ExecutionException | TimeoutException | RuntimeException ex) {
			LanguageServerPlugin.logError(ex);
		} catch (final InterruptedException ex) {
			LanguageServerPlugin.logError(ex);
			Thread.currentThread().interrupt();
		}

		return false;
	}

	private @Nullable ExitFlags exitPolicy(final @Nullable LinkedModeModel environment, final VerifyEvent event,
			final int offset, final int length) {
		if (event.character == SWT.ESC) {
			cancelled = true;
			return new ExitFlags(ILinkedModeListener.EXIT_ALL, false);
		}
		if (event.character == SWT.CR || event.character == SWT.LF) {
			cancelled = false;
			return new ExitFlags(ILinkedModeListener.EXIT_ALL, false);
		}
		return null;
	}

	private void installLinkedMode() {
		try {
			final var model = new LinkedModeModel();
			final var group = new LinkedPositionGroup();
			LinkedPosition primary = null;
			for (final IRegion region : occurrences) {
				final var position = new LinkedPosition(document, region.getOffset(), region.getLength());
				this.linkedPositions.add(position);
				group.addPosition(position);
				if (primary == null || (region.getOffset() == renameRegion.getOffset()
						&& region.getLength() == renameRegion.getLength())) {
					primary = position;
				}
			}
			this.linkedPosition = primary;
			model.addGroup(group);
			model.addLinkingListener(new ILinkedModeListener() {
				@Override
				public void left(final LinkedModeModel environment, final int flags) {
					if (cancelled) {
						// User cancelled (ESC) - restore original text for all occurrences and do not
						// call textDocument/rename. We restore from back to front so that earlier
						// replacements do not shift offsets of later regions.
						UI.runOnUIThread(() -> {
							final StyledText widget = viewer.getTextWidget();
							if (widget != null && !widget.isDisposed()) {
								widget.setRedraw(false);
							}
							try {
								for (int i = linkedPositions.size() - 1; i >= 0; i--) {
									final var pos = linkedPositions.get(i);
									final String original = i < originalContents.size() ? originalContents.get(i)
											: originalName;
									document.replace(pos.getOffset(), pos.getLength(), original);
								}
							} catch (final BadLocationException ex) {
								LanguageServerPlugin.logError(ex);
							} finally {
								if (widget != null && !widget.isDisposed()) {
									widget.setRedraw(true);
								}
							}
						});
					} else {
						scheduleRenameJob();
					}
				}

				@Override
				public void resume(LinkedModeModel environment, int flags) {
					// no-op
				}

				@Override
				public void suspend(LinkedModeModel environment) {
					// no-op
				}
			});
			model.forceInstall();

			final var ui = new EditorLinkedModeUI(model, viewer);
			ui.setExitPolicy(this::exitPolicy);
			ui.enter();
		} catch (final BadLocationException ex) {
			LanguageServerPlugin.logError(ex);
		}
	}

	private void scheduleRenameJob() {
		final var job = new Job(Messages.rename_title) {
			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				try {
					// LinkedPosition tracks the live identifier region while the user is typing.
					// We must use it (not the original Region) because the length/offset can
					// change.
					final var position = linkedPosition;
					if (position == null) {
						LanguageServerPlugin.logWarning("Inline rename: linkedPosition is null, skipping"); //$NON-NLS-1$
						return Status.OK_STATUS;
					}

					final String newName = document.get(position.getOffset(), position.getLength());
					if (newName.equals(originalName)) {
						return Status.OK_STATUS;
					}

					/*
					 * Perform revert + LSP rename as a single visual step on the UI thread.
					 *
					 * Language servers expect the "old" identifier to still be present at the
					 * rename position when textDocument/rename is invoked. If we call rename while
					 * the buffer already contains the new name, language servers will either treat
					 * it as a no-op or fail to resolve the symbol, and references will not be
					 * updated.
					 *
					 * To avoid that, we:
					 *
					 * 1) Restore the original identifier in the document so the LS sees the
					 * pre-rename state.
					 *
					 * 2) Immediately call textDocument/rename(newName) and apply the resulting
					 * WorkspaceEdit, which updates all occurrences (including this one).
					 *
					 * To prevent a visible flicker in the active editor, we wrap the revert + apply
					 * sequence in a redraw block for this editor's StyledText: setRedraw(false) ...
					 * setRedraw(true).
					 */
					UI.runOnUIThread(() -> {
						final StyledText widget = viewer.getTextWidget();
						final int offset = position.getOffset();
						final int length = position.getLength();

						// Even if the editor widget is null (viewer without StyledText, or editor
						// closed between confirm and execution), we still finish the confirmed
						// rename on the document/workspace. The widget is only used for flicker
						// suppression (setRedraw), not as a precondition for applying the rename.
						if (widget == null || widget.isDisposed()) {
							try {
								document.replace(offset, length, originalName);
							} catch (BadLocationException e) {
								LanguageServerPlugin.logError(e);
								return;
							}

							final boolean success = doRename(newName);
							if (!success) {
								try {
									document.replace(offset, length, newName);
								} catch (BadLocationException e) {
									LanguageServerPlugin.logError(e);
								}
							}
							return;
						}

						widget.setRedraw(false);
						try {
							document.replace(offset, length, originalName);
							final boolean success = doRename(newName);
							if (!success) {
								try {
									document.replace(offset, length, newName);
								} catch (BadLocationException e) {
									LanguageServerPlugin.logError(e);
								}
							}
						} catch (BadLocationException e) {
							LanguageServerPlugin.logError(e);
						} finally {
							widget.setRedraw(true);
						}
					});
					return Status.OK_STATUS;
				} catch (final BadLocationException ex) {
					return new Status(IStatus.ERROR, LanguageServerPlugin.PLUGIN_ID, ex.getMessage(), ex);
				}
			}
		};
		job.setSystem(true);
		job.schedule();
	}

	private boolean start() {
		final var styledText = viewer.getTextWidget();
		if (styledText == null || styledText.isDisposed())
			return false;

		styledText.getDisplay().asyncExec(() -> {
			if (!styledText.isDisposed()) {
				installLinkedMode();
			}
		});
		return true;
	}
}
