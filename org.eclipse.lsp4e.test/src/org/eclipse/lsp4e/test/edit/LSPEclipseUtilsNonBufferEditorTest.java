/*******************************************************************************
 * Copyright (c) 2026 Advantest Europe GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * 				Raghunandana Murthappa
 *******************************************************************************/
package org.eclipse.lsp4e.test.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.junit.jupiter.api.Test;

/**
 * Integration-like test that opens an editor whose document is not connected to
 * FileBuffers and verifies LSPEclipseUtils.toUri(IDocument) uses adapters
 * fallback.
 */
public class LSPEclipseUtilsNonBufferEditorTest {

	private static final URI TEST_URI = URI.create("nonbuffer://test/doc");

	/**
	 * This document can be adapted to a URI directly.
	 */
	private static final class TestDocument extends Document implements org.eclipse.core.runtime.IAdaptable {
		private final URI uri;

		TestDocument(URI uri) {
			super();
			this.uri = uri;
		}

		@Override
		public <T> T getAdapter(Class<T> adapter) {
			if (adapter == URI.class) {
				@SuppressWarnings("unchecked") T t = (T) uri;
				return t;
			}
			return null;
		}
	}

	@Test
	public void testNonBufferEditorDocumentToUri() throws Exception {
		IPreferenceStore store = LanguageServerPlugin.getDefault().getPreferenceStore();
		store.setValue("org.eclipse.lsp4e.resourceFallback.enabled", true);
		// Create a non-buffer-managed document adapted to this URI directly so the test
		// can run without an Eclipse workbench (headless/unit test environments).
		IDocument directDoc = new TestDocument(TEST_URI);
		URI resolvedDirect = LSPEclipseUtils.toUri(directDoc);
		assertEquals(TEST_URI, resolvedDirect, "toUri should resolve the adapter-provided URI");

		// If a workbench is available, also exercise opening the editor to verify the
		// integration path. This part is optional and guarded so it won't fail the test
		// when running in plain unit-test mode.
		NonBufferEditorInput input = new NonBufferEditorInput(TEST_URI);
		if (PlatformUI.isWorkbenchRunning()) {
			Display.getDefault().syncExec(() -> {
				try {
					IEditorPart part = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage()
							.openEditor(input, "org.eclipse.lsp4e.test.edit.nonBufferEditor");
					assertNotNull(part);
					if (part instanceof NonBufferEditor nbe) {
						IDocument editorDoc = nbe.getDocument();
						assertNotNull(editorDoc, "Editor should have created a document");
						URI resolved = LSPEclipseUtils.toUri(editorDoc);
						assertEquals(TEST_URI, resolved, "toUri should resolve the adapter-provided URI");
					}
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});
		}
	}
}