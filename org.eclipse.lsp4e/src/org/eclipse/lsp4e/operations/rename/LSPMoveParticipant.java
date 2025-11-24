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

import static org.eclipse.lsp4e.internal.NullSafetyHelper.lateNonNull;

import java.net.URI;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.lsp4j.FileRename;
import org.eclipse.lsp4j.RenameFilesParams;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.MoveParticipant;

public class LSPMoveParticipant extends MoveParticipant {

	private URI oldURI = lateNonNull();
	private URI newURI = lateNonNull();
	private IResource resource = lateNonNull();

	@Override
	public String getName() {
		return "LSP4E Move"; //$NON-NLS-1$
	}

	@Override
	protected boolean initialize(final Object element) {
		if (element instanceof final IResource res && (res instanceof IFile || res instanceof IFolder)) {
			resource = res;
			final URI uri = LSPEclipseUtils.toUri(res);
			if (uri == null)
				return false;
			oldURI = uri;

			// Compute destination from MoveArguments destination (container path)
			final Object dest = getArguments().getDestination();
			IPath destLoc = null;
			if (dest instanceof IResource destRes) {
				destLoc = destRes.getRawLocation();
			} else if (dest instanceof IPath destPath) {
				destLoc = destPath;
			}
			if (destLoc == null)
				return false;

			final String targetName = res.getName();
			newURI = LSPEclipseUtils.toUri(destLoc.append(targetName));

			return LSPFileOperationParticipantSupport
					.createFileOperationExecutor(res, FileOperationsServerCapabilities::getWillRename).anyMatching();
		}

		return false;
	}

	@Override
	public RefactoringStatus checkConditions(final IProgressMonitor monitor, final CheckConditionsContext context)
			throws OperationCanceledException {
		return new RefactoringStatus();
	}

	@Override
	public @Nullable Change createChange(final IProgressMonitor monitor)
			throws CoreException, OperationCanceledException {
		return null;
	}

	@Override
	public @Nullable Change createPreChange(final IProgressMonitor monitor)
			throws CoreException, OperationCanceledException {
		final var params = new RenameFilesParams();
		params.getFiles().add(new FileRename(oldURI.toString(), newURI.toString()));
		return LSPFileOperationParticipantSupport.computePreChange(getName(), params, resource,
				FileOperationsServerCapabilities::getWillRename, (ws, p) -> ws.willRenameFiles(p));
	}
}
