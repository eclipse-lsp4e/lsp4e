/*******************************************************************************
 * Copyright (c) 2026 Avaloq Group AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Avaloq Group AG - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.inlinecompletion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.Document;
import org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEvent;
import org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEventKind;
import org.eclipse.lsp4e.operations.inlinecompletion.internal.InlineCompletionExtensions;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.junit.jupiter.api.Test;

public class InlineCompletionExtensionsTest extends AbstractTestWithProject {

	private final InlineCompletionExtensions extensions = InlineCompletionExtensions.getInstance();

	@Test
	public void testEventReachesListenersDespiteFailingListener() throws Exception {
		RecordingEventListener.EVENTS.clear();
		final var event = new InlineCompletionEvent(UUID.randomUUID(), InlineCompletionEventKind.ACCEPT_WORD,
				URI.create("file:///test.lspt"), "word", 42L);

		extensions.fireEvent(event).get(5, TimeUnit.SECONDS);

		assertEquals(List.of(event), RecordingEventListener.EVENTS);
	}

	@Test
	public void testEnablement() {
		assertTrue(extensions.isEnabled());
		SwitchableEnablement.enabled = false;
		try {
			assertFalse(extensions.isEnabled());
		} finally {
			SwitchableEnablement.enabled = true;
		}
	}

	@Test
	public void testWordDetectorOfContentType() throws Exception {
		final IFile file = TestUtils.createUniqueTestFile(project, "my#var");

		final var document = TestUtils.openTextViewer(file).getDocument();

		assertInstanceOf(HashWordDetector.class, extensions.getWordDetector(document));
	}

	@Test
	public void testDefaultWordDetector() {
		assertSame(InlineCompletionExtensions.DEFAULT_WORD_DETECTOR, extensions.getWordDetector(new Document("x")));
	}
}
