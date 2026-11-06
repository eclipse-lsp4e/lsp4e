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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.widgets.Display;

/**
 * Animates the {@link InlineCompletionStatus.State#FETCHING} state on a status
 * bar label by pulsing the icon of {@link InlineCompletionStatus.State#READY},
 * so that the icon stays the same while a request is in flight. All methods
 * must be called on the UI thread.
 */
public class InlineCompletionStatusSpinner {

	private static final int FPS = 15;
	private static final int MILLIS_PER_FRAME = 1000 / FPS;
	private static final int FRAMES = 12;
	private static final double MIN_OPACITY = 0.3;

	private final CLabel label;
	private final Display display;
	private List<Image> images = List.of();
	private @Nullable Image pulsedImage;
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
		this.display = label.getDisplay();
		start();
	}

	/**
	 * Starts the animation, pulsing the current icon of
	 * {@link InlineCompletionStatus.State#READY}.
	 */
	public void start() {
		stop();
		final Image image = InlineCompletionStatus.State.READY.getImage();
		if (image != pulsedImage) {
			final List<Image> previous = images;
			images = image == null ? List.of() : createFrames(image);
			pulsedImage = image;
			if (!images.isEmpty() && !label.isDisposed()) {
				// the label must not keep showing a frame disposed below
				label.setImage(images.get(0));
			}
			previous.forEach(Image::dispose);
		}
		if (!images.isEmpty()) {
			animationTask = scheduler.scheduleAtFixedRate(this::advanceFrame, 0, MILLIS_PER_FRAME,
					TimeUnit.MILLISECONDS);
		}
	}

	private static List<Image> createFrames(Image image) {
		final var frames = new ArrayList<Image>(FRAMES);
		for (int i = 0; i < FRAMES; i++) {
			final double opacity = MIN_OPACITY + (1 - MIN_OPACITY) * (1 + Math.cos(2 * Math.PI * i / FRAMES)) / 2;
			final ImageDataProvider provider = zoom -> withOpacity(image.getImageData(zoom), opacity);
			frames.add(new Image(image.getDevice(), provider));
		}
		return frames;
	}

	private static ImageData withOpacity(ImageData data, double opacity) {
		final var result = (ImageData) data.clone();
		final ImageData mask = data.getTransparencyType() == SWT.TRANSPARENCY_ALPHA ? null : data.getTransparencyMask();
		final var alphas = new byte[data.width * data.height];
		for (int y = 0; y < data.height; y++) {
			for (int x = 0; x < data.width; x++) {
				final int alpha = mask == null ? data.getAlpha(x, y) : mask.getPixel(x, y) == 0 ? 0 : 255;
				alphas[y * data.width + x] = (byte) Math.round(alpha * opacity);
			}
		}
		result.alphaData = alphas;
		result.alpha = -1;
		result.transparentPixel = -1;
		result.maskData = null;
		return result;
	}

	private void advanceFrame() {
		if (display.isDisposed()) {
			return;
		}
		display.asyncExec(() -> {
			final ScheduledFuture<?> task = animationTask;
			final List<Image> current = images;
			if (!label.isDisposed() && task != null && !task.isCancelled() && !current.isEmpty()) {
				label.setImage(current.get(Math.floorMod(imageIndex.getAndIncrement(), current.size())));
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
		images = List.of();
		pulsedImage = null;
	}
}
