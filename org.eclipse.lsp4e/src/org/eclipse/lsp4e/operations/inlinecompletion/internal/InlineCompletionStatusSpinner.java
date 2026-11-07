/*******************************************************************************
 * Copyright (c) 2025, 2026 Avaloq Group AG.
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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Image;

/**
 * Animates the {@link InlineCompletionStatus.State#FETCHING} state on a status
 * bar label. All methods must be called on the UI thread.
 */
public class InlineCompletionStatusSpinner {

	private static final String FRAMES_PATH = "icons/full/obj16/inlinecompletion/spinner"; //$NON-NLS-1$
	private static final int FPS = 15;
	private static final int MILLIS_PER_FRAME = 1000 / FPS;

	private final CLabel label;
	private final List<Image> images = new ArrayList<>();
	private final AtomicInteger imageIndex = new AtomicInteger(0);
	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
		final var thread = new Thread(runnable, "LSP4E Inline Completion Status Spinner"); //$NON-NLS-1$
		thread.setDaemon(true);
		return thread;
	});
	private @Nullable ScheduledFuture<?> animationTask;

	/**
	 * Creates a spinner and starts the animation.
	 *
	 * @param label
	 *            the label to animate
	 */
	public InlineCompletionStatusSpinner(CLabel label) {
		this.label = label;
		loadImages();
		start();
	}

	private void loadImages() {
		final Enumeration<URL> entries = LanguageServerPlugin.getDefault().getBundle().findEntries(FRAMES_PATH,
				"*.png", false); //$NON-NLS-1$
		if (entries == null) {
			return;
		}
		final List<URL> frames = Collections.list(entries);
		frames.sort(Comparator.comparing(URL::getPath));
		for (URL frame : frames) {
			images.add(ImageDescriptor.createFromURL(frame).createImage());
		}
	}

	public void start() {
		stop();
		if (!images.isEmpty()) {
			animationTask = scheduler.scheduleAtFixedRate(this::advanceFrame, 0, MILLIS_PER_FRAME,
					TimeUnit.MILLISECONDS);
		}
	}

	private void advanceFrame() {
		label.getDisplay().asyncExec(() -> {
			final ScheduledFuture<?> task = animationTask;
			if (!label.isDisposed() && task != null && !task.isCancelled()) {
				label.setImage(images.get(imageIndex.getAndUpdate(index -> (index + 1) % images.size())));
			}
		});
	}

	public void stop() {
		final ScheduledFuture<?> task = animationTask;
		if (task != null) {
			task.cancel(false);
			animationTask = null;
		}
	}

	public void dispose() {
		stop();
		scheduler.shutdownNow();
		images.forEach(Image::dispose);
		images.clear();
	}
}
