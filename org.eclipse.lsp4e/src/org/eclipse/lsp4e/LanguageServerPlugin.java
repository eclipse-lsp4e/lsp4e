/*******************************************************************************
 * Copyright (c) 2016 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.lsp4e.ui.LSPImages;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import com.google.common.base.Throwables;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hashing;

public class LanguageServerPlugin extends AbstractUIPlugin {

	/**
	 * Used by {@link #logError(String, Throwable)} to prevent logging the same
	 * exception of certain types repeatedly
	 */
	private static final ConcurrentMap<HashCode, Integer> EXCEPTIONS_COUNTER = new ConcurrentHashMap<>();

	/** Job family identifier for the "background update markers from diagnostics" job. */
	public static final Object FAMILY_UPDATE_MARKERS = new Object();
	/** Job family identifier for the "initialize language server" job. */
	public static final Object FAMILY_INITIALIZE_LANGUAGE_SERVER = new Object();

	public static final String PLUGIN_ID = "org.eclipse.lsp4e"; //$NON-NLS-1$
	private static final String TRACE_ID = PLUGIN_ID + "/trace"; //$NON-NLS-1$

	public static final boolean DEBUG = Platform.getDebugBoolean("org.eclipse.lsp4e/debug"); //$NON-NLS-1$

	// The shared instance
	private static volatile @Nullable LanguageServerPlugin plugin;

	public LanguageServerPlugin() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		LanguageServiceAccessor.shutdownAllDispatchers();
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static LanguageServerPlugin getDefault() {
		Assert.isNotNull(plugin);
		return plugin;
	}

	@Override
	protected void initializeImageRegistry(ImageRegistry registry) {
		LSPImages.initalize(registry);
	}

	/**
	 * Utility method to log errors.
	 *
	 * @param thr
	 *            The exception through which we noticed the error
	 */
	public static void logError(final Throwable thr) {
		logError(thr.getMessage(), thr);
	}

	/**
	 * Utility method to log errors.
	 *
	 * @param message
	 *            User comprehensible message
	 * @param thr
	 *            The exception through which we noticed the error
	 */
	public static void logError(final @Nullable String message, final @Nullable Throwable thr) {
		final var plugin = LanguageServerPlugin.plugin;
		if (plugin != null) {
			if (!DEBUG && thr instanceof BadLocationException) {
				final HashCode key = Hashing.sha256().hashString(Throwables.getStackTraceAsString(thr), UTF_8);
				if (EXCEPTIONS_COUNTER.getOrDefault(key, 0) > 2)
					return;
				EXCEPTIONS_COUNTER.compute(key, (k, v) -> v == null ? 1 : ++v);
			}
			plugin.getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, 0, message, thr));
		}
	}

	/**
	 * Log an info message for this plug-in
	 *
	 * @param message
	 */
	public static void logInfo(final String message) {
		if (plugin != null) {
			plugin.getLog().log(new Status(IStatus.INFO, PLUGIN_ID, 0, message, null));
		}
	}

	/**
	 * Utility method to log warnings for this plug-in.
	 *
	 * @param message
	 *            User comprehensible message
	 */
	public static void logWarning(final @Nullable String message) {
		logWarning(message, null);
	}

	/**
	 * Utility method to log warnings for this plug-in.
	 *
	 * @param message
	 *            User comprehensible message
	 * @param thr
	 *            The exception through which we noticed the warning
	 */
	public static void logWarning(final @Nullable String message, final @Nullable Throwable thr) {
		if (plugin != null) {
			plugin.getLog().log(new Status(IStatus.WARNING, PLUGIN_ID, 0, message, thr));
		}
	}

	/**
	 * Returns whether log trace is enabled for this plugin.
	 *
	 * @return true if the trace debug option is enabled, false otherwise
	 */
	public static boolean isLogTraceEnabled() {
		return Platform.getDebugBoolean(TRACE_ID);
	}

}
