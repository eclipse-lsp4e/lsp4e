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
package org.eclipse.lsp4e.operations.inlinecompletion.internal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.internal.IdentifierUtil;
import org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEnablement;
import org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEvent;
import org.eclipse.lsp4e.operations.inlinecompletion.InlineCompletionEventListener;

/**
 * The contributions to the {@code org.eclipse.lsp4e.inlineCompletion} extension
 * point.
 */
public final class InlineCompletionExtensions {

	private static final String EXTENSION_POINT_ID = LanguageServerPlugin.PLUGIN_ID + ".inlineCompletion"; //$NON-NLS-1$
	private static final String EVENT_LISTENER_ELEMENT = "eventListener"; //$NON-NLS-1$
	private static final String ENABLEMENT_ELEMENT = "enablement"; //$NON-NLS-1$
	private static final String WORD_DETECTOR_ELEMENT = "wordDetector"; //$NON-NLS-1$
	private static final String CLASS_ATTRIBUTE = "class"; //$NON-NLS-1$
	private static final String CONTENT_TYPE_ATTRIBUTE = "contentType"; //$NON-NLS-1$

	/**
	 * Words are runs of identifier characters, see {@link IdentifierUtil}.
	 */
	public static final IWordDetector DEFAULT_WORD_DETECTOR = new IWordDetector() {
		@Override
		public boolean isWordStart(char c) {
			return IdentifierUtil.isIdentifierPart(c);
		}

		@Override
		public boolean isWordPart(char c) {
			return IdentifierUtil.isIdentifierPart(c);
		}
	};

	private static final class Holder {
		static final InlineCompletionExtensions INSTANCE = new InlineCompletionExtensions();
	}

	private final List<InlineCompletionEventListener> eventListeners;
	private final @Nullable InlineCompletionEnablement enablement;
	private final Map<String, IWordDetector> wordDetectors;
	private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor(runnable -> {
		final var thread = new Thread(runnable, "LSP4E Inline Completion Events"); //$NON-NLS-1$
		thread.setDaemon(true);
		return thread;
	});

	private InlineCompletionExtensions() {
		final IConfigurationElement[] elements = Platform.getExtensionRegistry()
				.getConfigurationElementsFor(EXTENSION_POINT_ID);
		eventListeners = createAll(elements, EVENT_LISTENER_ELEMENT, InlineCompletionEventListener.class);
		enablement = createFirst(elements, ENABLEMENT_ELEMENT, InlineCompletionEnablement.class);
		wordDetectors = createWordDetectors(elements);
	}

	public static InlineCompletionExtensions getInstance() {
		return Holder.INSTANCE;
	}

	private static <T> List<T> createAll(IConfigurationElement[] elements, String name, Class<T> type) {
		final var result = new ArrayList<T>();
		for (IConfigurationElement element : elements) {
			if (name.equals(element.getName())) {
				final T extension = create(element, type);
				if (extension != null) {
					result.add(extension);
				}
			}
		}
		return List.copyOf(result);
	}

	private static <T> @Nullable T createFirst(IConfigurationElement[] elements, String name, Class<T> type) {
		final List<T> extensions = createAll(elements, name, type);
		if (extensions.size() > 1) {
			LanguageServerPlugin.logWarning("Several " + name + " contributions to " + EXTENSION_POINT_ID //$NON-NLS-1$ //$NON-NLS-2$
					+ ", using " + extensions.get(0).getClass().getName()); //$NON-NLS-1$
		}
		return extensions.isEmpty() ? null : extensions.get(0);
	}

	private static Map<String, IWordDetector> createWordDetectors(IConfigurationElement[] elements) {
		final var result = new HashMap<String, IWordDetector>();
		for (IConfigurationElement element : elements) {
			final String contentType = element.getAttribute(CONTENT_TYPE_ATTRIBUTE);
			if (WORD_DETECTOR_ELEMENT.equals(element.getName()) && contentType != null && !contentType.isBlank()) {
				final IWordDetector detector = create(element, IWordDetector.class);
				final IWordDetector existing = detector == null ? null : result.putIfAbsent(contentType, detector);
				if (existing != null) {
					LanguageServerPlugin.logWarning("Several " + WORD_DETECTOR_ELEMENT + " contributions for " //$NON-NLS-1$ //$NON-NLS-2$
							+ contentType + ", using " + existing.getClass().getName()); //$NON-NLS-1$
				}
			}
		}
		return Map.copyOf(result);
	}

	private static <T> @Nullable T create(IConfigurationElement element, Class<T> type) {
		try {
			final Object extension = element.createExecutableExtension(CLASS_ATTRIBUTE);
			if (type.isInstance(extension)) {
				return type.cast(extension);
			}
			LanguageServerPlugin.logError(element.getAttribute(CLASS_ATTRIBUTE) + " contributed by " //$NON-NLS-1$
					+ element.getContributor().getName() + " does not implement " + type.getName()); //$NON-NLS-1$
		} catch (CoreException e) {
			LanguageServerPlugin.logError(e);
		}
		return null;
	}

	/**
	 * Returns whether inline completion is enabled according to the contributed
	 * {@link InlineCompletionEnablement}.
	 *
	 * @return {@code true} if there is no such contribution or it enables inline
	 *         completion
	 */
	public boolean isEnabled() {
		final InlineCompletionEnablement current = enablement;
		if (current == null) {
			return true;
		}
		try {
			return current.isEnabled();
		} catch (RuntimeException e) {
			LanguageServerPlugin.logError(e);
			return false;
		}
	}

	/**
	 * Returns the word detector contributed for the content type of a document or
	 * for its nearest base type.
	 *
	 * @param document
	 *            the document
	 * @return the contributed detector, or {@link #DEFAULT_WORD_DETECTOR}
	 */
	public IWordDetector getWordDetector(IDocument document) {
		if (!wordDetectors.isEmpty()) {
			for (IContentType contentType : LSPEclipseUtils.getDocumentContentTypes(document)) {
				for (IContentType type = contentType; type != null; type = type.getBaseType()) {
					final IWordDetector detector = wordDetectors.get(type.getId());
					if (detector != null) {
						return detector;
					}
				}
			}
		}
		return DEFAULT_WORD_DETECTOR;
	}

	/**
	 * Delivers an event to all contributed {@link InlineCompletionEventListener}s on
	 * a background thread.
	 *
	 * @param event
	 *            the event
	 * @return a future completed once all listeners handled the event
	 */
	public CompletableFuture<@Nullable Void> fireEvent(InlineCompletionEvent event) {
		return CompletableFuture.runAsync(() -> {
			for (InlineCompletionEventListener listener : eventListeners) {
				SafeRunner.run(() -> listener.handle(event));
			}
		}, eventExecutor);
	}
}
