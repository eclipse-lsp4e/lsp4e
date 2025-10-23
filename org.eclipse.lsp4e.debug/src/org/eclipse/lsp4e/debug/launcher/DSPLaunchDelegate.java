/*******************************************************************************
 * Copyright (c) 2017, 2023 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.launcher;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4e.debug.debugmodel.JsonParserWithStringSubstitution;
import org.eclipse.lsp4e.debug.debugmodel.TransportStreams;
import org.eclipse.lsp4e.debug.debugmodel.TransportStreams.DefaultTransportStreams;
import org.eclipse.lsp4e.debug.debugmodel.TransportStreams.SocketTransportStreams;
import org.eclipse.osgi.util.NLS;

public class DSPLaunchDelegate implements ILaunchConfigurationDelegate {

	/**
	 * Structured arguments for the
	 * {@link DSPLaunchDelegate#launch(DSPLaunchDelegateLaunchBuilder) method.
	 *
	 * Use this class to simplify calling the launch method and make the source code
	 * of consumers a little easier to read.
	 */
	public static class DSPLaunchDelegateLaunchBuilder {
		private ILaunchConfiguration configuration;
		private String mode;
		private ILaunch launch;
		private @Nullable IProgressMonitor monitor;
		private boolean launchNotConnect;
		private @Nullable String debugCmd;
		private @Nullable List<String> debugCmdArgs;
		private String @Nullable [] debugCmdEnvVars;
		private boolean monitorDebugAdapter;
		private @Nullable String server;
		private int port;
		private Map<String, Object> dspParameters;

		private DSPLaunchDelegateLaunchBuilder(DSPLaunchDelegateLaunchBuilder other) {
			this.configuration = other.configuration;
			this.mode = other.mode;
			this.launch = other.launch;
			this.monitor = other.monitor;

			this.launchNotConnect = other.launchNotConnect;

			this.debugCmd = other.debugCmd;
			this.debugCmdArgs = other.debugCmdArgs == null ? null : new ArrayList<>(other.debugCmdArgs);
			this.monitorDebugAdapter = other.monitorDebugAdapter;

			this.server = other.server;
			this.port = other.port;

			this.dspParameters = new HashMap<>(other.dspParameters);
		}

		/**
		 * Construct a builder using the normal arguments to
		 * {@link ILaunchConfigurationDelegate#launch(ILaunchConfiguration, String, ILaunch, IProgressMonitor)}
		 *
		 * @param configuration
		 * @param mode
		 * @param launch
		 * @param monitor
		 */
		public DSPLaunchDelegateLaunchBuilder(ILaunchConfiguration configuration, String mode, ILaunch launch,
				@Nullable IProgressMonitor monitor) {
			this.configuration = configuration;
			this.mode = mode;
			this.launch = launch;
			this.monitor = monitor;
			this.dspParameters = new HashMap<>();
		}

		public DSPLaunchDelegateLaunchBuilder setLaunchDebugAdapter(String debugCmd,
				@Nullable List<String> debugCmdArgs) {
			return setLaunchDebugAdapter(debugCmd, debugCmdArgs, (String[]) null);
		}

		public DSPLaunchDelegateLaunchBuilder setLaunchDebugAdapter(String debugCmd,
				@Nullable List<String> debugCmdArgs, @Nullable Map<String, String> envVars) {
			return setLaunchDebugAdapter(debugCmd, debugCmdArgs, envVars == null ? null
					: envVars.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new));
		}

		public DSPLaunchDelegateLaunchBuilder setLaunchDebugAdapter(String debugCmd,
				@Nullable List<String> debugCmdArgs, String @Nullable [] envVars) {
			this.launchNotConnect = true;
			this.debugCmd = debugCmd;
			this.debugCmdArgs = debugCmdArgs;
			this.debugCmdEnvVars = envVars;
			return this;
		}

		public DSPLaunchDelegateLaunchBuilder setAttachDebugAdapter(String server, int port) {
			this.launchNotConnect = false;
			this.server = server;
			this.port = port;
			return this;
		}

		public DSPLaunchDelegateLaunchBuilder setMonitorDebugAdapter(boolean monitorDebugAdapter) {
			this.monitorDebugAdapter = monitorDebugAdapter;
			return this;
		}

		public DSPLaunchDelegateLaunchBuilder setDspParameters(Map<String, Object> dspParameters) {
			this.dspParameters = dspParameters;
			return this;
		}

		public ILaunchConfiguration getConfiguration() {
			return configuration;
		}

		public String getMode() {
			return mode;
		}

		public ILaunch getLaunch() {
			return launch;
		}

		public @Nullable IProgressMonitor getMonitor() {
			return monitor;
		}

		public boolean isLaunchNotConnect() {
			return launchNotConnect;
		}

		public @Nullable String getDebugCmd() {
			return debugCmd;
		}

		public @Nullable List<String> getDebugCmdArgs() {
			return debugCmdArgs;
		}

		public String @Nullable [] getDebugCmdEnvVars() {
			return debugCmdEnvVars;
		}

		public boolean isMonitorDebugAdapter() {
			return monitorDebugAdapter;
		}

		public @Nullable String getServer() {
			return server;
		}

		public int getPort() {
			return port;
		}

		public Map<String, Object> getDspParameters() {
			return dspParameters;
		}

		@Override
		public String toString() {
			return "DSPLaunchDelegateLaunchBuilder [configuration=" + configuration + ", mode=" + mode + ", launch="
					+ launch + ", monitor=" + monitor + ", launchNotConnect=" + launchNotConnect + ", debugCmd="
					+ debugCmd + ", debugCmdArgs=" + debugCmdArgs //
					+ ", debugCmdEnvVars=" + (debugCmdEnvVars == null ? null : List.of(debugCmdEnvVars))
					+ ", monitorDebugAdapter=" + monitorDebugAdapter + ", server=" + server + ", port=" + port
					+ ", dspParameters=" + dspParameters + "]";
		}
	}

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch,
			@Nullable IProgressMonitor monitor) throws CoreException {
		final var builder = new DSPLaunchDelegateLaunchBuilder(configuration, mode, launch, monitor);
		builder.launchNotConnect = DSPPlugin.DSP_MODE_LAUNCH
				.equals(configuration.getAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_LAUNCH));
		builder.debugCmd = configuration.getAttribute(DSPPlugin.ATTR_DSP_CMD, "");
		builder.debugCmdArgs = configuration.getAttribute(DSPPlugin.ATTR_DSP_ARGS, List.of());
		builder.debugCmdEnvVars = DebugPlugin.getDefault().getLaunchManager().getEnvironment(configuration);
		builder.monitorDebugAdapter = configuration.getAttribute(DSPPlugin.ATTR_DSP_MONITOR_DEBUG_ADAPTER, false);
		builder.server = configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_HOST, "");
		builder.port = configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_PORT, 0);

		String dspParametersJson = configuration.getAttribute(DSPPlugin.ATTR_DSP_PARAM, "{}");
		try {
			final var jsonUtils = new JsonParserWithStringSubstitution(
					VariablesPlugin.getDefault().getStringVariableManager());
			Map<String, Object> customParams = jsonUtils.parseJsonObjectAndRemoveNulls(dspParametersJson);
			builder.dspParameters.putAll(customParams);
		} catch (IllegalStateException e) {
			throw new CoreException(createErrorStatus("Json launch parameters were not correctly formatted.", null));
		}

		launch(builder);
	}

	public void launch(DSPLaunchDelegateLaunchBuilder builderSrc) throws CoreException {
		// Make a copy so we can modify locally as needed.
		final var builder = new DSPLaunchDelegateLaunchBuilder(builderSrc);
		final var subMonitor = SubMonitor.convert(builder.monitor, 100);
		builder.dspParameters = new HashMap<>(builder.dspParameters);

		boolean customDebugAdapter = builder.configuration.getAttribute(DSPPlugin.ATTR_CUSTOM_DEBUG_ADAPTER, false);
		boolean customLaunchParameters = builder.configuration.getAttribute(DSPPlugin.ATTR_CUSTOM_LAUNCH_PARAMS, false);

		if (customDebugAdapter) {
			builder.launchNotConnect = DSPPlugin.DSP_MODE_LAUNCH
					.equals(builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_LAUNCH));
			builder.debugCmd = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_CMD, "");
			builder.debugCmdArgs = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_ARGS, List.of());
			builder.debugCmdEnvVars = DebugPlugin.getDefault().getLaunchManager().getEnvironment(builder.configuration);
			builder.monitorDebugAdapter = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_MONITOR_DEBUG_ADAPTER,
					false);
			builder.server = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_HOST, "");
			builder.port = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_PORT, 0);
		}
		if (customLaunchParameters) {
			String dspParametersJson = builder.configuration.getAttribute(DSPPlugin.ATTR_DSP_PARAM, "");
			if (!dspParametersJson.isBlank()) {
				try {
					final var jsonUtils = new JsonParserWithStringSubstitution(
							VariablesPlugin.getDefault().getStringVariableManager());
					Map<String, Object> customParams = jsonUtils.parseJsonObjectAndRemoveNulls(dspParametersJson);
					builder.dspParameters.putAll(customParams);
				} catch (IllegalStateException | CoreException e) {
					throw new CoreException(
							createErrorStatus("Json launch parameters were not correctly formatted.", null));
				}
			}
		}

		// DSP supports run/debug as a simple flag to the debug server.
		// See LaunchRequestArguments.noDebug
		if (ILaunchManager.DEBUG_MODE.equals(builder.mode)) {
			subMonitor.setTaskName("Starting debug session");
			builder.dspParameters.put("noDebug", false);
		} else if (ILaunchManager.RUN_MODE.equals(builder.mode)) {
			subMonitor.setTaskName("Starting run session");
			builder.dspParameters.put("noDebug", true);
		} else {
			throw new CoreException(createErrorStatus(NLS.bind("Unsupported launch mode '{0}'.", builder.mode), null));
		}

		final Supplier<TransportStreams> streamSupplier;
		try {
			if (builder.launchNotConnect) {
				InputStream inputStream;
				OutputStream outputStream;
				Runnable cleanup;
				final var command = new ArrayList<String>();

				final var debugCmd = builder.debugCmd;
				if (debugCmd == null || debugCmd.isBlank()) {
					throw new CoreException(createErrorStatus("Debug command unspecified.", null));
				}
				command.add(debugCmd);

				final var debugCmdArgs = builder.debugCmdArgs;
				if (debugCmdArgs != null && !debugCmdArgs.isEmpty()) {
					command.addAll(debugCmdArgs);
				}

				subMonitor
						.subTask(NLS.bind("Launching debug adapter: {0}", "\"" + String.join("\" \"", command) + "\""));
				Process debugAdapterProcess = DebugPlugin.exec(command.toArray(String[]::new), null,
						builder.debugCmdEnvVars);
				if (builder.monitorDebugAdapter) {
					// Uglish workaround: should instead have a dedicated ProcessFactory reading
					// process attribute rather than launch one
					String initialCaptureOutput = builder.launch.getAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT);
					builder.launch.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, Boolean.toString(true));
					IProcess debugAdapterIProcess = DebugPlugin.newProcess(builder.launch, debugAdapterProcess,
							"Debug Adapter");
					final IStreamsProxy debugAdapterStreamsProxy = castNonNull(debugAdapterIProcess.getStreamsProxy());
					builder.launch.setAttribute(DebugPlugin.ATTR_CAPTURE_OUTPUT, initialCaptureOutput);

					final var bytes = new ConcurrentLinkedQueue<Byte>();
					inputStream = new InputStream() {
						@Override
						public int read() throws IOException {
							while (debugAdapterProcess.isAlive()) {
								final Byte b = bytes.poll();
								if (b != null) {
									return b;
								} else {
									try {
										Thread.sleep(50);
									} catch (InterruptedException e) {
										Thread.currentThread().interrupt();
										DSPPlugin.logError(e);
									}
								}
							}
							return -1;
						}
					};
					final var consoleEncoding = builder.launch.getAttribute(DebugPlugin.ATTR_CONSOLE_ENCODING);
					final var consoleCharset = consoleEncoding == null //
							? Charset.defaultCharset()
							: Charset.forName(consoleEncoding);
					castNonNull(debugAdapterStreamsProxy.getOutputStreamMonitor()).addListener((text, monitor) -> {
						for (byte b : text.getBytes(consoleCharset)) {
							bytes.add(b);
						}
					});
					outputStream = new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							debugAdapterStreamsProxy.write(new String(new byte[] { (byte) b }));
						}

						@Override
						public void write(byte[] b, int off, int len) throws IOException {
							debugAdapterStreamsProxy.write(new String(b, off, len));
						}
					};
					cleanup = () -> {
						try {
							debugAdapterIProcess.terminate();
							debugAdapterProcess.destroy();
						} catch (DebugException e) {
							DSPPlugin.logError(e);
						}
					};
				} else {
					inputStream = debugAdapterProcess.getInputStream();
					outputStream = debugAdapterProcess.getOutputStream();
					cleanup = debugAdapterProcess::destroyForcibly;
				}
				// TODO: encapsulate logic starting the process in the supplier
				streamSupplier = () -> new DefaultTransportStreams(inputStream, outputStream) {
					@Override
					public void close() {
						super.close();
						cleanup.run();
					}
				};
			} else {
				final var server = builder.server;
				if (server == null || server.isBlank()) {
					throw new CoreException(createErrorStatus("Debug server host unspecified.", null));
				}
				if (builder.port < 1 || builder.port > 65535) {
					throw new CoreException(
							createErrorStatus("Debug server port unspecified or out of range 1-65535.", null));
				}

				subMonitor.subTask(NLS.bind("Connecting to debug adapter: {0}:{1}", builder.server, builder.port));
				streamSupplier = () -> new SocketTransportStreams(server, builder.port);
			}

			subMonitor.setWorkRemaining(80);

			ILaunch launch = builder.launch;
			Map<String, Object> dspParameters = builder.dspParameters;
			IDebugTarget target = createDebugTarget(subMonitor, streamSupplier, launch, dspParameters);
			builder.launch.addDebugTarget(target);
		} catch (OperationCanceledException e1) {
			throw new CoreException(createErrorStatus("Failed to start debugging", e1));
		} finally {
			subMonitor.done();
		}
	}

	/**
	 * For extenders/consumers of {@link DSPLaunchDelegate} who want to provide
	 * customization of the IDebugTarget, this method allows extenders to hook in a
	 * custom debug target implementation. The debug target is normally a subclass
	 * of {@link DSPDebugTarget}, but does not have to be. The arguments to this
	 * method are normally just passed to {@link DSPDebugTarget} constructor.
	 */
	protected IDebugTarget createDebugTarget(SubMonitor subMonitor, Supplier<TransportStreams> streamsSupplier,
			ILaunch launch, Map<String, Object> dspParameters) throws CoreException {
		final var target = new DSPDebugTarget(launch, streamsSupplier, dspParameters);
		target.initialize(subMonitor.split(80));
		return target;
	}

	/**
	 * Creates an IStatus with an IStats.ERROR containing the given message and
	 * optional exception.
	 *
	 * @param message error message
	 * @param cause   underlying exception
	 */
	private IStatus createErrorStatus(String message, @Nullable Throwable cause) {
		return new Status(IStatus.ERROR, DSPPlugin.PLUGIN_ID, 0, message, cause);
	}
}
