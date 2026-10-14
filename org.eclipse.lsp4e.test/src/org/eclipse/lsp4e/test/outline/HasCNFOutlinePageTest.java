/*******************************************************************************
 * Copyright (c) 2026 Hélios GILLES and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Hélios GILLES - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.outline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.outline.HasCNFOutlinePage;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.MultiPageEditorPart;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.junit.jupiter.api.Test;

public class HasCNFOutlinePageTest extends AbstractTestWithProject {

	private final HasCNFOutlinePage tester = new HasCNFOutlinePage();

	private final List<Class<?>> adapterRequests = new ArrayList<>();

	/**
	 * The test runs in the UI thread for every editor that becomes active: an editor that no language server
	 * supports must not be asked for its outline page, which some editors compute slowly.
	 */
	@Test
	public void testEditorWithoutLanguageServerIsNotAskedForItsOutlinePage() throws Exception {
		IFile file = TestUtils.createUniqueTestFileOfUnknownType(project, "Hello World !!");

		assertFalse(test(editor(file)));
		assertEquals(List.of(), adapterRequests);
	}

	@Test
	public void testEditorWithLanguageServerIsAskedForItsOutlinePage() throws Exception {
		IFile file = TestUtils.createUniqueTestFile(project, "Hello World !!");

		assertFalse(test(editor(file)));
		assertEquals(List.of(IContentOutlinePage.class), adapterRequests);
	}

	/**
	 * A multi-page editor asks its active page for the outline page, and the input of the page may not be the one
	 * of the editor.
	 */
	@Test
	public void testMultiPageEditorIsAskedForItsOutlinePage() throws Exception {
		IFile file = TestUtils.createUniqueTestFileOfUnknownType(project, "Hello World !!");

		assertFalse(test(new MultiPageEditor(new FileEditorInput(file))));
		assertEquals(List.of(IContentOutlinePage.class), adapterRequests);
	}

	private boolean test(IEditorPart editor) {
		return tester.test(editor, "hasCNFOutlinePage", new Object[0], null);
	}

	/**
	 * Returns an editor of the given file that records the adapters asked for, and has none.
	 */
	private IEditorPart editor(IFile file) {
		final var input = new FileEditorInput(file);
		return (IEditorPart) Proxy.newProxyInstance(HasCNFOutlinePageTest.class.getClassLoader(),
				new Class<?>[] { IEditorPart.class }, (proxy, method, args) -> {
					switch (method.getName()) {
					case "getEditorInput":
						return input;
					case "getAdapter":
						adapterRequests.add((Class<?>) args[0]);
						return null;
					case "equals":
						return proxy == args[0];
					case "hashCode":
						return System.identityHashCode(proxy);
					case "toString":
						return "editor of " + file.getName();
					default:
						Class<?> type = method.getReturnType();
						return type.isPrimitive() && type != void.class
								? Array.get(Array.newInstance(type, 1), 0)
								: null;
					}
				});
	}

	/**
	 * A multi-page editor that records the adapters asked for, and has none.
	 */
	private class MultiPageEditor extends MultiPageEditorPart {

		private final IEditorInput input;

		MultiPageEditor(IEditorInput input) {
			this.input = input;
		}

		@Override
		public IEditorInput getEditorInput() {
			return input;
		}

		@Override
		public <T> @Nullable T getAdapter(@Nullable Class<T> adapter) {
			adapterRequests.add(adapter);
			return null;
		}

		@Override
		protected void createPages() {
		}

		@Override
		public void doSave(@Nullable IProgressMonitor monitor) {
		}

		@Override
		public void doSaveAs() {
		}

		@Override
		public boolean isSaveAsAllowed() {
			return false;
		}
	}
}
