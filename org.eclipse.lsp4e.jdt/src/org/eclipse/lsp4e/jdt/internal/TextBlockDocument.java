/*******************************************************************************
 * Copyright (c) 2026 Daniel Schmid and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Daniel Schmid - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt.internal;

import java.net.URI;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.AbstractDocument;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DefaultLineTracker;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextStore;
import org.eclipse.lsp4e.LanguageServerPlugin;

/**
 * A {@link IDocument} that is a view of the String content of a text block
 * within a Java source file.
 * 
 * This document only supports a single edit which will be reflected in the
 * original document. The edit will be reflected in the referenced full document
 * but this document becomes invalid in the process.
 */
public class TextBlockDocument extends AbstractDocument implements IAdaptable {
	private final URI uri;
	private int doNotSkipTrailingSpacesAt;

	public TextBlockDocument(IDocument fullDocument, IRegion documentRegion, char[] rawContent, char[] content,
			URI uri, int doNotSkipTrailingSpacesAtParam) {
		this.uri = uri;
		this.doNotSkipTrailingSpacesAt = doNotSkipTrailingSpacesAtParam;
		setTextStore(new ITextStore() {

			private boolean valid = true;

			@Override
			public void set(@Nullable String text) {
				replace(0, getLength(), text);
			}

			@Override
			public void replace(int offset, int length, @Nullable String text) {
				checkValid();
				if (text == null) {
					text = "";
				}
				if (offset < 0 || offset + length > content.length) {
					LanguageServerPlugin
							.logError("range out of bounds in text block, offset=" + offset + ", length=" + length);
					return;
				}
				int newOffset = TextBlockSourceContentMapper.mapContentToSource(rawContent, content, offset,
						doNotSkipTrailingSpacesAt);
				int newEnd = TextBlockSourceContentMapper.mapContentToSource(rawContent, content, offset + length,
						doNotSkipTrailingSpacesAt);
				if (newOffset < 0 || newEnd < 0) {
					LanguageServerPlugin.logError("could not map range for replacing document content, offset=" + offset
							+ ", length=" + length);
					return;
				}
				try {
					int start = documentRegion.getOffset() + newOffset;
					int newLength = newEnd - newOffset;
					if (start > 0) {
						// To ensure the cursor is always moved along with the replacement, make sure
						// that the
						// character before the cursor is included
						text = fullDocument.getChar(start - 1) + text;
						start--;
						newLength++;
					}
					fullDocument.replace(start, newLength, text);
					doNotSkipTrailingSpacesAt = -1;
					invalidate();
				} catch (BadLocationException e) {
					LanguageServerPlugin.logError(e);
				}
			}

			@Override
			public int getLength() {
				checkValid();
				return content.length;
			}

			@Override
			public String get(int offset, int length) {
				checkValid();
				return new String(content, offset, length);
			}

			@Override
			public char get(int offset) {
				checkValid();
				return content[offset];
			}

			private void checkValid() {
				if (!valid) {
					throw new UnsupportedOperationException("document is invalidated");
				}
			}

			private void invalidate() {
				valid = false;
			}
		});
		setLineTracker(new DefaultLineTracker());
		getTracker().set(new String(content));
		completeInitialization();
	}

	@Override
	public <T> @Nullable T getAdapter(Class<T> adapter) {
		if (adapter == URI.class) {
			return (T) uri;
		}
		return null;
	}

	public URI getURI() {
		return uri;
	}
}