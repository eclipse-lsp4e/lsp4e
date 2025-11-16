/*******************************************************************************
 * Copyright (c) 2017, 2018 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class DSPPlugin extends AbstractUIPlugin {
	public static final boolean DEBUG = Platform.getDebugBoolean("org.eclipse.lsp4e.debug/debug"); //$NON-NLS-1$

	// The plug-in ID
	public static final String PLUGIN_ID = "org.eclipse.lsp4e.debug"; //$NON-NLS-1$

	// Unique identifier for the DSP debug model launch config
	public static final String ID_DSP_DEBUG_MODEL = "org.eclipse.lsp4e.debug.model";

	// Launch configuration attribute keys
	/** String, one of {@link #DSP_MODE_LAUNCH} or {@link #DSP_MODE_CONNECT} */
	public static final String ATTR_DSP_MODE = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_MODE";
	public static final String DSP_MODE_LAUNCH = "launch server";
	public static final String DSP_MODE_CONNECT = "connect to server";
	/** String */
	public static final String ATTR_DSP_CMD = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_CMD";
	/** List<String> */
	public static final String ATTR_DSP_ARGS = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_ARGS";
	/** String - should be properly formed JSON */
	public static final String ATTR_DSP_PARAM = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_PARAM";
	/** String */
	public static final String ATTR_DSP_SERVER_HOST = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_SERVER_HOST";
	/** Integer */
	public static final String ATTR_DSP_SERVER_PORT = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_SERVER_PORT";
	/** Boolean */
	public static final String ATTR_DSP_MONITOR_DEBUG_ADAPTER = ID_DSP_DEBUG_MODEL + ".ATTR_DSP_MONITOR_ADAPTER";
	/** Boolean */
	public static final String ATTR_CUSTOM_DEBUG_ADAPTER = ID_DSP_DEBUG_MODEL + ".ATTR_CUSTOM_DEBUG_ADAPTER";
	/** Boolean */
	public static final String ATTR_CUSTOM_LAUNCH_PARAMS = ID_DSP_DEBUG_MODEL + ".ATTR_CUSTOM_LAUNCH_PARAMS";

	// The shared instance
	private static volatile @Nullable DSPPlugin plugin;

	public DSPPlugin() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static DSPPlugin getDefault() {
		Assert.isNotNull(plugin);
		return plugin;
	}

	/**
	 * Utility method to log errors.
	 *
	 * @param thr The exception through which we noticed the error
	 */
	public static void logError(final Throwable thr) {
		logError(thr.getMessage(), thr);
	}

	/**
	 * Utility method to log errors.
	 *
	 * @param message User comprehensible message
	 * @param thr     The exception through which we noticed the error
	 */
	public static void logError(final @Nullable String message, final @Nullable Throwable thr) {
		log(IStatus.ERROR, message, thr);
	}

	/**
	 * Log an info message for this plug-in
	 *
	 * @param message
	 */
	public static void logInfo(final String message) {
		log(IStatus.INFO, message, null);
	}

	/**
	 * Utility method to log warnings for this plug-in.
	 *
	 * @param message User comprehensible message
	 * @param thr     The exception through which we noticed the warning
	 */
	public static void logWarning(final String message, final @Nullable Throwable thr) {
		log(IStatus.WARNING, message, thr);
	}

	private static void log(final int severity, @Nullable String message, final @Nullable Throwable thr) {
		ResponseError error = null;
		if (thr instanceof ResponseErrorException responseErrorException) {
			error = responseErrorException.getResponseError();
		} else if (thr != null && thr.getCause() instanceof ResponseErrorException responseErrorException) {
			error = responseErrorException.getResponseError();
		}
		if (error != null) {
			try {
				message += " - response error: " + error.toString();
			} catch (Exception e) {
				message += " - response error formatting exception " + e.getMessage();
			}
		}
		getDefault().getLog().log(new Status(severity, PLUGIN_ID, 0, message, thr));
	}

}
