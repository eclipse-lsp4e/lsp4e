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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.DeleteFilesParams;
import org.eclipse.lsp4j.FileDelete;
import org.eclipse.lsp4j.FileOperationsServerCapabilities;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.DeleteParticipant;

public class LSPDeleteParticipant extends DeleteParticipant {

	private URI oldURI = lateNonNull();
	private IResource resource = lateNonNull();

	@Override
	public String getName() {
		return "LSP4E Delete"; //$NON-NLS-1$
	}

	@Override
	protected boolean initialize(final Object element) {
		if (element instanceof final IResource res && (res instanceof IFile || res instanceof IFolder)) {
			resource = res;
			final URI uri = LSPEclipseUtils.toUri(res);
			if (uri == null)
				return false;
			oldURI = uri;
			return LSPFileOperationParticipantSupport
					.createFileOperationExecutor(res, FileOperationsServerCapabilities::getWillDelete).anyMatching();
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
		final var params = new DeleteFilesParams();
		params.getFiles().add(new FileDelete(oldURI.toString()));
		return LSPFileOperationParticipantSupport.computePreChange(getName(), params, resource,
				FileOperationsServerCapabilities::getWillDelete, (ws, p) -> ws.willDeleteFiles(p));
	}
}
