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
package org.eclipse.lsp4e.internal;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.runtime.CoreException;

public class ResourceUtil {

	public static void setWritable(final IFile file) throws CoreException {
		ResourceAttributes attrs = file.getResourceAttributes();
		if (attrs != null) {
			attrs.setReadOnly(false);
			file.setResourceAttributes(attrs);
		}
	}

	private ResourceUtil() {

	}
}
