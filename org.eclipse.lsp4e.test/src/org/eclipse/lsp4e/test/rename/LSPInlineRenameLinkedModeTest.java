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
package org.eclipse.lsp4e.test.rename;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServerWrapper;
import org.eclipse.lsp4e.operations.rename.LSPInlineRenameLinkedMode;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServerFactory;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

/**
 * Tests the behavior of {@link LSPInlineRenameLinkedMode} in isolation from the
 * UI layer to keep the tests deterministic and avoid brittle dependencies on
 * SWT widgets or key-event timing.
 * <p>
 * The scenarios focus on:
 * </p>
 * <ul>
 * <li>Collecting same-file occurrences from
 * {@code textDocument/documentHighlight} and mapping them to {@link IRegion}
 * instances.</li>
 * <li>Driving the non-UI inline rename pipeline, including
 * {@code scheduleRenameJob()}, revert of the in-buffer edit, and application of
 * the {@link WorkspaceEdit} returned by the language server.</li>
 * </ul>
 */
class LSPInlineRenameLinkedModeTest extends AbstractTestWithProject {

	/**
	 * Unit-style test for
	 * {@code LSPInlineRenameLinkedMode.collectSameFileOccurrences(...)}.
	 * <p>
	 * Verifies that document highlights for the caret position are converted into a
	 * de-duplicated list of same-file regions and that the primary region is always
	 * present as the first element.
	 * </p>
	 */
	@Test
	void testInlineLinkedEditingSameFile(MockLanguageServerFactory factory) throws Exception {
		// Ensure inline rename is enabled for this test
		InstanceScope.INSTANCE.getNode(LanguageServerPlugin.PLUGIN_ID).putBoolean("org.eclipse.lsp4e.inlineRename", //$NON-NLS-1$
				true);

		// Prepare a simple document with two occurrences of the same identifier
		var content = "compute();\ncompute();";
		IFile file = TestUtils.createUniqueTestFile(project, content);
		IDocument document = LSPEclipseUtils.getDocument(file);
		assertNotNull(document);

		int idLength = "compute".length();
		int firstOffset = content.indexOf("compute");
		int secondOffset = content.indexOf("compute", firstOffset + 1);
		var primaryRegion = new Region(firstOffset, idLength);

		// Configure mock document highlights:
		// - two valid occurrences
		// - a duplicate of the primary occurrence (should be de-duplicated)
		var caretPosition = LSPEclipseUtils.toPosition(firstOffset, document);
		var highlights = new HashMap<Position, List<? extends DocumentHighlight>>();
		highlights.put(caretPosition, List.of( //
				new DocumentHighlight(new Range(new Position(0, 0), new Position(0, idLength)),
						DocumentHighlightKind.Read),
				new DocumentHighlight(new Range(new Position(1, 0), new Position(1, idLength)),
						DocumentHighlightKind.Read),
				new DocumentHighlight(new Range(new Position(0, 0), new Position(0, idLength)),
						DocumentHighlightKind.Text)));
		factory.withConfiguration((idx, server)-> {
			server.setDocumentHighlights(highlights);
		});

		// Call the internal helper directly via reflection to avoid depending on
		// UI/command wiring
		Method collectSameFileOccurrences = LSPInlineRenameLinkedMode.class
				.getDeclaredMethod("collectSameFileOccurrences", IDocument.class, int.class, IRegion.class); //$NON-NLS-1$
		collectSameFileOccurrences.setAccessible(true);

		@SuppressWarnings("unchecked") //
		var regions = (List<IRegion>) collectSameFileOccurrences.invoke(null, document, firstOffset, primaryRegion);

		// Primary region is always present and first
		assertEquals(firstOffset, regions.get(0).getOffset());
		assertEquals(idLength, regions.get(0).getLength());

		// Duplicates must be filtered out
		assertEquals(2, regions.size());
		assertEquals(secondOffset, regions.get(1).getOffset());
		assertEquals(idLength, regions.get(1).getLength());
	}

	/**
	 * Drives the inline-rename pipeline without relying on SWT key events.
	 * <p>
	 * The test simulates a user-typed identifier in the primary linked position,
	 * then calls {@code scheduleRenameJob()} and asserts that the
	 * {@link WorkspaceEdit} returned by the language server is applied back to the
	 * document.
	 * </p>
	 */
	@Test
	void testInlineRenameEndToEndSameFile(MockLanguageServerFactory factory) throws Exception {
		// Prepare a simple document with two occurrences of the same identifier
		var content = "compute();\ncompute();";
		IFile file = TestUtils.createUniqueTestFile(project, content);
		IDocument document = LSPEclipseUtils.getDocument(file);
		assertNotNull(document);

		int idLength = "compute".length();
		int firstOffset = content.indexOf("compute");
		int secondOffset = content.indexOf("compute", firstOffset + 1);

		// Simulated name typed by the user in linked mode
		String typedName = "inlineName";

		// Prepare rename result for realism (not strictly required for this test)
		Position firstPos = LSPEclipseUtils.toPosition(firstOffset, document);
		Position secondPos = LSPEclipseUtils.toPosition(secondOffset, document);

		var prepareRange = new Range(firstPos, new Position(firstPos.getLine(), firstPos.getCharacter() + idLength));

		// WorkspaceEdit returned by textDocument/rename: both occurrences updated to
		// typedName.
		// This verifies that scheduleRenameJob():
		// - restores the original name before calling textDocument/rename
		// - applies the WorkspaceEdit from the language server.
		var edits = new HashMap<String, List<TextEdit>>();
		String uri = LSPEclipseUtils.toUri(file).toString();
		edits.put(uri, List.of(
				new TextEdit(new Range(firstPos, new Position(firstPos.getLine(), firstPos.getCharacter() + idLength)),
						typedName),
				new TextEdit(
						new Range(secondPos, new Position(secondPos.getLine(), secondPos.getCharacter() + idLength)),
						typedName)));
		factory.withConfiguration((idx, server)-> {
			server.getTextDocumentService().setPrepareRenameResult(Either.forLeft(prepareRange));
			server.getTextDocumentService().setRenameEdit(new WorkspaceEdit(edits));
		});

		// Open a viewer so that the document is wired to a text viewer like in real
		// usage
		final ITextViewer viewer = TestUtils.openTextViewer(file);

		// Manually construct LSPInlineRenameLinkedMode to avoid brittle UI key-event
		// simulation
		var constructor = LSPInlineRenameLinkedMode.class.getDeclaredConstructor(IDocument.class, ITextViewer.class,
				int.class, IRegion.class, String.class, List.class, LanguageServerWrapper.class);
		constructor.setAccessible(true);

		var renameRegion = new Region(firstOffset, idLength);
		List<IRegion> occurrences = List.of(renameRegion, new Region(secondOffset, idLength));
		var mode = constructor.newInstance(document, viewer, Integer.valueOf(firstOffset), renameRegion, "compute",
				occurrences, null);

		// Simulate that the user has typed a new name in the primary linked position.
		// We update the document and the internal LinkedPosition that scheduleRenameJob
		// reads.
		int delta = typedName.length() - idLength;
		document.replace(firstOffset, idLength, typedName);
		document.replace(secondOffset + delta, idLength, typedName);

		var linkedPosField = LSPInlineRenameLinkedMode.class.getDeclaredField("linkedPosition");
		linkedPosField.setAccessible(true);
		linkedPosField.set(mode, new LinkedPosition(document, firstOffset, typedName.length()));

		// Invoke scheduleRenameJob() directly; it will:
		// - restore the original identifier
		// - call textDocument/rename(typedName)
		// - apply the WorkspaceEdit we configured above.
		Method schedule = LSPInlineRenameLinkedMode.class.getDeclaredMethod("scheduleRenameJob");
		schedule.setAccessible(true);
		schedule.invoke(mode);

		waitForAndAssertCondition("Inline rename workspace edit not applied", 5_000, () -> {
			assertEquals(typedName + "();\n" + typedName + "();", document.get());
			return true;
		});
	}

}
