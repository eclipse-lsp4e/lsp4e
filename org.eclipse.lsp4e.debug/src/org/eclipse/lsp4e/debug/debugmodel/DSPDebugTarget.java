/*******************************************************************************
 * Copyright (c) 2017-2020 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Pierre-Yves Bigourdan <pyvesdev@gmail.com>:
 *    Bug 552451 - Should the DSPProcess be added to the Launch when "attach" is used?
 *    Bug 553196 - Toolbar & console terminate buttons always enabled even when the DSPDebugTarget is terminated
 *    Bug 553234 - Implement DSPDebugTarget disconnect methods
 *    Bug 567158 - IllegalArgumentException when terminating DPSProcess that was never initialised
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4e.debug.console.DSPProcess;
import org.eclipse.lsp4e.debug.console.DSPStreamsProxy;
import org.eclipse.lsp4e.debug.debugmodel.TransportStreams.DefaultTransportStreams;
import org.eclipse.lsp4e.internal.ArrayUtil;
import org.eclipse.lsp4j.debug.BreakpointEventArguments;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.ExitedEventArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.LoadedSourceEventArguments;
import org.eclipse.lsp4j.debug.ModuleEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.eclipse.lsp4j.debug.ProcessEventArguments;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArguments;
import org.eclipse.lsp4j.debug.RunInTerminalRequestArgumentsKind;
import org.eclipse.lsp4j.debug.RunInTerminalResponse;
import org.eclipse.lsp4j.debug.StartDebuggingRequestArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminateArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.validation.ReflectiveMessageValidator;

public class DSPDebugTarget extends DSPDebugElement implements IDebugTarget, IDebugProtocolClient {
	private static final boolean TRACE_IO = Platform.getDebugBoolean("org.eclipse.lsp4e.debug/trace/io"); //$NON-NLS-1$
	private static final boolean TRACE_MESSAGES = Platform.getDebugBoolean("org.eclipse.lsp4e.debug/trace/messages"); //$NON-NLS-1$

	/**
	 * Any events we receive from the adapter that require further contact with the
	 * adapter needs to be farmed off to another thread as the events arrive on the
	 * same thread. (Note for requests, use the *Async versions on
	 * completeablefuture to achieve the same effect.)
	 */
	private final ExecutorService threadPool = Executors.newCachedThreadPool();

	private final ILaunch launch;
	private Future<?> debugProtocolFuture = lateNonNull();
	private IDebugProtocolServer debugProtocolServer = lateNonNull();
	/**
	 * Debug adapters are not supposed to send initialized event until after
	 * replying to initializeRequest with Capabilities. However some debug adapters
	 * don't respect this (or didn't in the past). Even if they do respect this,
	 * sometimes due to the multithreaded event handling, the initialized event
	 * arrives before the capabilities are stored. Therefore use a future to guard
	 * reading the capabilities before they are ready.
	 */
	private final CompletableFuture<@Nullable Capabilities> capabilitiesFuture = new CompletableFuture<>();
	/**
	 * Once we have received initialized event, this member will be "done" as a flag
	 */
	private final CompletableFuture<@Nullable Void> initialized = new CompletableFuture<>();

	/**
	 * The debuggees that this target has spawned, for example when handling the
	 * {@link #startDebugging(StartDebuggingRequestArguments)} notification
	 */
	private final Set<DSPDebugTarget> debuggees = new HashSet<>();

	/**
	 * The cached set of current threads. This should generally not be directly
	 * accessed and instead accessed via {@link #getThreads()} which will ensure
	 * they are up to date (against the {@link #refreshThreads} flag).
	 */
	private final Map<Integer, DSPThread> threads = Collections.synchronizedMap(new TreeMap<>());

	private volatile boolean exitedReceived = false;
	private volatile boolean fTerminated = false;
	private volatile boolean fSentTerminateRequest = false;
	private String targetName = lateNonNull();

	private @Nullable DSPBreakpointManager breakpointManager;
	private @Nullable DSPProcess process;
	private TransportStreams transportStreams;
	// the suppliers can be used to reconnect to running adapter, or for
	// startDebuggin
	private final Supplier<TransportStreams> streamsSupplier;

	/**
	 * User supplied debug parameters for {@link IDebugProtocolServer#launch(Map)}
	 * and {@link IDebugProtocolServer#attach(Map)}
	 */
	private final Map<String, Object> dspParameters;

	/**
	 * Kept for backward compatibility
	 */
	public DSPDebugTarget(ILaunch launch, Runnable cleanup, InputStream in, OutputStream out,
			Map<String, Object> dspParameters) {
		this(launch, () -> new DefaultTransportStreams(in, out) {
			@Override
			public void close() {
				super.close();
				cleanup.run();
			}
		}, dspParameters);
	}

	public DSPDebugTarget(ILaunch launch, Supplier<TransportStreams> streamsSupplier,
			Map<String, Object> dspParameters) {
		super(null);
		this.transportStreams = streamsSupplier.get();
		this.streamsSupplier = streamsSupplier;
		this.launch = launch;
		this.dspParameters = dspParameters;
	}

	public void initialize(IProgressMonitor monitor) throws CoreException {
		SubMonitor subMonitor = SubMonitor.convert(monitor, 100);
		try {

			PrintWriter traceMessages;
			if (TRACE_MESSAGES) {
				traceMessages = new PrintWriter(System.out);
			} else {
				traceMessages = null;
			}
			if (TRACE_IO) {
				transportStreams = transportStreams.withTrace();
			}
			// TODO this Function copied from createClientLauncher so that I can replace
			// threadpool, so make this wrapper more accessible

			UnaryOperator<MessageConsumer> wrapper = consumer -> {
				MessageConsumer result = consumer;
				if (traceMessages != null) {
					result = message -> {
						traceMessages.println(message);
						traceMessages.flush();
						consumer.consume(message);
					};
				}
				if (true) {
					result = new ReflectiveMessageValidator(result);
				}
				return result;
			};

			InputStream in2 = transportStreams.in;
			OutputStream out2 = transportStreams.out;
			ExecutorService threadPool2 = threadPool;
			Launcher<? extends IDebugProtocolServer> debugProtocolLauncher = createLauncher(wrapper, in2, out2,
					threadPool2);

			debugProtocolFuture = debugProtocolLauncher.startListening();
			debugProtocolServer = debugProtocolLauncher.getRemoteProxy();

			CompletableFuture<?> future = initialize(dspParameters, subMonitor);
			future.thenRun(this::triggerUpdateThreads); // VSCode always queries "threads" after configurationDone
			monitorGet(future, subMonitor);
		} catch (Exception e) {
			terminated();
			throw e;
		} finally {
			subMonitor.done();
		}
	}

	/**
	 * As the main reason for extending {@link DSPDebugTarget} is to interface to a
	 * custom debug adapter that has more functionality than the protocol defines.
	 * Overriding this method allows an extender to provide their own service
	 * interface that extends {@link IDebugProtocolServer}.
	 *
	 * For more information on how to <a href=
	 * "https://github.com/eclipse-lsp4j/lsp4j/tree/main/documentation#extending-the-protocol">extend
	 * the protocol</a> using LSP4J
	 */
	protected Launcher<? extends IDebugProtocolServer> createLauncher(UnaryOperator<MessageConsumer> wrapper,
			InputStream in, OutputStream out, ExecutorService threadPool) {
		Launcher<IDebugProtocolServer> debugProtocolLauncher = DSPLauncher.createClientLauncher(this, in, out,
				threadPool, wrapper);
		return debugProtocolLauncher;
	}

	private CompletableFuture<?> initialize(Map<String, Object> dspParameters, IProgressMonitor monitor) {
		final var arguments = new InitializeRequestArguments();
		arguments.setClientID("lsp4e.debug");
		String adapterId = "adapterId";
		if (dspParameters.get("type") instanceof String type) {
			adapterId = type;
		}
		arguments.setAdapterID(adapterId);
		arguments.setPathFormat("path");
		arguments.setSupportsVariableType(true);
		arguments.setSupportsVariablePaging(true);
		arguments.setLinesStartAt1(true);
		arguments.setColumnsStartAt1(true);
		arguments.setSupportsRunInTerminalRequest(true);
		arguments.setSupportsStartDebuggingRequest(Boolean.TRUE);
		targetName = Objects.toString(dspParameters.get("program"), "Debug Adapter Target");

		monitor.subTask("Initializing connection to debug adapter");
		boolean isLaunchRequest = "launch".equals(dspParameters.getOrDefault("request", "launch"));
		CompletableFuture<?> launchAttachFuture = getDebugProtocolServer().initialize(arguments)
				.thenAccept((Capabilities capabilities) -> {
					monitor.worked(10);
					if (capabilities == null) {
						DSPPlugin.logError(
								"Debug adapter unexpectedly returned 'null' Capabilities from initializeRequest. "
										+ "A default Capabilities will be used instead.",
								null);
						capabilities = new Capabilities();
					}
					capabilitiesFuture.complete(capabilities);
				}).thenCompose(v -> {
					monitor.worked(10);
					if (isLaunchRequest) {
						monitor.subTask("Launching program");
						return getDebugProtocolServer().launch(dspParameters);
					} else {
						monitor.subTask("Attaching to running program");
						return getDebugProtocolServer().attach(dspParameters);
					}
				}).handle((q, t) -> {
					if (t != null) {
						initialized.completeExceptionally(t);
					}
					return q;
				});
		CompletableFuture<@Nullable Void> configurationDoneFuture = CompletableFuture
				.allOf(initialized, capabilitiesFuture).thenRun(() -> monitor.worked(10));
		if (ILaunchManager.DEBUG_MODE.equals(launch.getLaunchMode())) {
			configurationDoneFuture = configurationDoneFuture.thenCompose(v -> {
				monitor.worked(10);
				monitor.subTask("Sending breakpoints");
				breakpointManager = new DSPBreakpointManager(getBreakpointManager(), getDebugProtocolServer(),
						getCapabilities());
				return breakpointManager.initialize();
			});
		}
		configurationDoneFuture = configurationDoneFuture.thenCompose(v -> {
			monitor.worked(30);
			monitor.subTask("Sending configuration done");
			if (Boolean.TRUE.equals(castNonNull(getCapabilities()).getSupportsConfigurationDoneRequest())) {
				return getDebugProtocolServer().configurationDone(new ConfigurationDoneArguments());
			}
			return CompletableFuture.completedFuture(null);
		});
		return CompletableFuture.allOf(launchAttachFuture, configurationDoneFuture);
	}

	private void terminated() {
		fTerminated = true;
		for (final DSPThread t : getThreads()) {
			try {
				t.terminate();
			} catch (DebugException e) {
				DSPPlugin.logError(e);
			}
		}
		final var process = this.process;
		if (process != null && process.canTerminate()) {
			process.terminateWithoutProtocolRequest();
		}
		fireTerminateEvent();
		debuggees.forEach(DSPDebugTarget::terminated);
		if (breakpointManager != null) {
			breakpointManager.shutdown();
		}
		if (debugProtocolFuture != null) {
			debugProtocolFuture.cancel(true);
			/*
			 * If the debugProtocolFuture is running the message loop on the same thread we
			 * are in currently, the cancel call will set the interrupt flag. That isn't
			 * necessary as the message loop isn't waiting on anything (since this code is
			 * running). The interrupt flag may affect future processing, like terminating
			 * processes. Therefore clear the flag. See lsp4e#264
			 */
			java.lang.Thread.interrupted();
		}
		transportStreams.close();
	}

	@Override
	public void initialized() {
		initialized.complete(null);
	}

	@Override
	public CompletableFuture<Void> startDebugging(StartDebuggingRequestArguments args) {
		final var parameters = new HashMap<String, Object>(/* dspParameters */);
		parameters.putAll(args.getConfiguration());
		try {
			final var newTarget = new DSPDebugTarget(launch, streamsSupplier, parameters);
			launch.addDebugTarget(newTarget);
			newTarget.initialize(new NullProgressMonitor());
			debuggees.add(newTarget);
		} catch (CoreException e) {
			DSPPlugin.logError(e);
		}
		return CompletableFuture.completedFuture(null);
	}

	/**
	 * Throws a debug exception with a status code of
	 * <code>TARGET_REQUEST_FAILED</code>.
	 *
	 * @param message exception message
	 * @param e       underlying exception or <code>null</code>
	 * @throws DebugException if a problem is encountered
	 */
	@Override
	protected void requestFailed(String message, @Nullable Throwable e) throws DebugException {
		throw newTargetRequestFailedException(message, e);
	}

	@Override
	public DSPDebugTarget getDebugTarget() {
		return this;
	}

	@Override
	public ILaunch getLaunch() {
		return launch;
	}

	@Override
	public <T> @Nullable T getAdapter(Class<T> adapter) {
		return null;
	}

	@Override
	public boolean canTerminate() {
		return !isTerminated();
	}

	@Override
	public boolean isTerminated() {
		return fTerminated;
	}

	@Override
	public void terminated(TerminatedEventArguments body) {
		/*
		 * According to the Debug Adapter Protocol, the 'terminated' event signals that
		 * the debuggee has finished, but the debug adapter may continue to run in
		 * order to shut down cleanly and emit a corresponding 'exited' event. Do not
		 * tear down the adapter connection immediately here so that a subsequent
		 * 'exited' event can still be delivered. If no 'exited' event is received
		 * within a grace period, fall back to terminating the session.
		 */
		CompletableFuture.runAsync(() -> {
			if (!exitedReceived && !isTerminated()) {
				terminated();
			}
		}, CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS, threadPool));
	}

	/**
	 * This implementation follows the "Debug session end" guidelines in
	 * https://microsoft.github.io/debug-adapter-protocol/overview:
	 *
	 * "When the development tool ends a debug session, the sequence of events is
	 * slightly different based on whether the session has been initially 'launched'
	 * or 'attached':
	 *
	 * Debuggee launched: if a debug adapter supports the terminate request, the
	 * development tool uses it to terminate the debuggee gracefully, i.e. it gives
	 * the debuggee a chance to cleanup everything before terminating. If the
	 * debuggee does not terminate but continues to run (or hits a breakpoint), the
	 * debug session will continue, but if the development tool tries again to
	 * terminate the debuggee, it will then use the disconnect request to end the
	 * debug session unconditionally. The disconnect request is expected to
	 * terminate the debuggee (and any child processes) forcefully.
	 *
	 * Debuggee attached: If the debuggee has been 'attached' initially, the
	 * development tool issues a disconnect request. This should detach the debugger
	 * from the debuggee but will allow it to continue."
	 */
	@Override
	public void terminate() throws DebugException {
		boolean shouldSendTerminateRequest = !fSentTerminateRequest
				&& Boolean.TRUE.equals(castNonNull(getCapabilities()).getSupportsTerminateRequest())
				&& "launch".equals(dspParameters.getOrDefault("request", "launch"));
		if (shouldSendTerminateRequest) {
			fSentTerminateRequest = true;
			getDebugProtocolServer().terminate(new TerminateArguments()).thenRunAsync(this::terminated);
		} else {
			final var arguments = new DisconnectArguments();
			arguments.setTerminateDebuggee(true);
			getDebugProtocolServer().disconnect(arguments).thenRunAsync(this::terminated);
		}
	}

	@Override
	public void continued(ContinuedEventArguments body) {
		threadPool.execute(() -> {
			DSPDebugElement source = null;
			source = getThread(body.getThreadId());
			if (source == null || body.getAllThreadsContinued() == null || body.getAllThreadsContinued()) {
				ArrayUtil.forEach(getThreads(), DSPThread::continued);
			}
			if (source != null) {
				source.fireResumeEvent(DebugEvent.CLIENT_REQUEST);
			}
		});
	}

	@Override
	public void stopped(StoppedEventArguments body) {
		triggerUpdateThreads().thenRunAsync(() -> {
			DSPThread source = null;
			if (body.getThreadId() != null) {
				source = getThread(body.getThreadId());
			}
			if (source == null || body.getAllThreadsStopped() == null || body.getAllThreadsStopped()) {
				for (final DSPThread t : getThreads()) {
					t.stopped();
					t.fireChangeEvent(DebugEvent.CHANGE);
				}
			}

			if (source != null) {
				source.stopped();
				source.fireSuspendEvent(calcDetail(body.getReason()));
			}
		}, threadPool);
	}

	private int calcDetail(String reason) {
		if (reason.equals("breakpoint") || reason.equals("entry") || reason.equals("exception")) { //$NON-NLS-1$
			return DebugEvent.BREAKPOINT;
		} else if (reason.equals("step")) { //$NON-NLS-1$
			return DebugEvent.STEP_OVER;
		} else if (reason.equals("pause")) { //$NON-NLS-1$
			return DebugEvent.CLIENT_REQUEST;
		} else {
			return DebugEvent.UNSPECIFIED;
		}
	}

	@Override
	public boolean canResume() {
		return !isTerminated() && isSuspended() && getThreads().length > 0;
	}

	@Override
	public boolean canSuspend() {
		return !isTerminated() && !isSuspended() && getThreads().length > 0;
	}

	@Override
	public boolean isSuspended() {
		DSPThread[] dspThreads = getThreads();
		return ArrayUtil.anyMatch(dspThreads, DSPThread::isSuspended);
	}

	@Override
	public void resume() throws DebugException {
		DSPThread[] dspThreads = getThreads();
		// TODO add support to protocol for ContinueArguments to support all
		if (dspThreads.length > 0) {
			dspThreads[0].resume();
		}
	}

	@Override
	public void suspend() throws DebugException {
		DSPThread[] dspThreads = getThreads();
		// TODO add support to protocol for PauseArguments to support all
		if (dspThreads.length > 0) {
			dspThreads[0].suspend();
		}
	}

	@Override
	public boolean canDisconnect() {
		return !isDisconnected();
	}

	@Override
	public void disconnect() throws DebugException {
		getDebugProtocolServer().disconnect(new DisconnectArguments()).thenRun(this::terminated);
	}

	@Override
	public boolean isDisconnected() {
		return fTerminated;
	}

	@Override
	public boolean supportsStorageRetrieval() {
		return false;
	}

	@Override
	public IMemoryBlock getMemoryBlock(long startAddress, long length) throws DebugException {
		throw new DebugException(new Status(IStatus.OK, DSPPlugin.PLUGIN_ID, DebugException.TARGET_REQUEST_FAILED,
				"not implemented", null));
	}

	@Override
	public @Nullable IProcess getProcess() {
		return process;
	}

	@Override
	public DSPThread[] getThreads() {
		return threads.values().toArray(DSPThread[]::new);
	}

	/**
	 * Get a thread object without connecting to the debug adapter. This is for when
	 * we get a thread before it is fully populated, so return a new thread in that
	 * case and let it be populated later
	 *
	 * @param threadId
	 * @return
	 */
	private DSPThread getThread(Integer threadId) {
		return threads.computeIfAbsent(threadId, id -> new DSPThread(this, threadId));
	}

	@Override
	public boolean hasThreads() throws DebugException {
		return getThreads().length > 0;
	}

	@Override
	public String getName() {
		return targetName;
	}

	@Override
	public boolean supportsBreakpoint(IBreakpoint breakpoint) {
		return !isTerminated() && breakpointManager != null && breakpointManager.supportsBreakpoint(breakpoint);
	}

	@Override
	public void exited(ExitedEventArguments args) {
		/*
		 * The debug adapter reports that the debuggee has exited. At this point it is
		 * safe to terminate the debug session and dispose the connection to the
		 * adapter.
		 */
		exitedReceived = true;
		terminated();
	}

	@Override
	public void thread(ThreadEventArguments args) {
		triggerUpdateThreads();
	}

	private CompletableFuture<?> triggerUpdateThreads() {
		return getDebugProtocolServer().threads().thenAcceptAsync(threadsResponse -> {
			var threadIds = Arrays.stream(threadsResponse.getThreads()).map(Thread::getId).collect(Collectors.toSet());
			boolean contentChanged = false;
			synchronized (threads) {
				contentChanged = threads.keySet().removeIf(Predicate.not(threadIds::contains));
				for (Thread thread : threadsResponse.getThreads()) {
					DSPThread dspThread = threads.get(thread.getId());
					if (dspThread == null) {
						dspThread = new DSPThread(this, thread.getId());
						threads.put(dspThread.getId(), dspThread);
						contentChanged = true;
					}
					dspThread.update(thread);
				}
			}
			if (contentChanged) {
				fireChangeEvent(DebugEvent.CONTENT);
			}
		});
	}

	@Override
	public void output(OutputEventArguments args) {
		String output = args.getOutput();
		if (args.getCategory() == null //
				|| OutputEventArgumentsCategory.CONSOLE.equals(args.getCategory()) //
				|| OutputEventArgumentsCategory.STDOUT.equals(args.getCategory())) {
			// TODO put this data in a different region with a different colour
			getOrCreateStreamsProxy().getOutputStreamMonitor().append(output);
		} else if (OutputEventArgumentsCategory.STDERR.equals(args.getCategory())) {
			getOrCreateStreamsProxy().getErrorStreamMonitor().append(output);
		} else if (DSPPlugin.DEBUG) {
			System.out.println("output: " + args);
		}
	}

	private DSPStreamsProxy getOrCreateStreamsProxy() {
		var process = this.process;
		if (process == null) {
			// create dummy process to have a console
			process(null);
			process = castNonNull(this.process);
		}
		return process.getStreamsProxy();
	}

	@Override
	public void module(ModuleEventArguments args) {
		// TODO
	}

	@Override
	public void loadedSource(LoadedSourceEventArguments args) {
		// TODO
	}

	@Override
	public void process(@Nullable ProcessEventArguments args) {
		process = new DSPProcess(this, args);
		launch.addProcess(process);
	}

	/**
	 * runInTerminal request; value of command field is 'runInTerminal'.
	 * <p>
	 * With this request a debug adapter can run a command in a terminal.
	 */
	@Override
	public CompletableFuture<RunInTerminalResponse> runInTerminal(RunInTerminalRequestArguments args) {
		if (RunInTerminalRequestArgumentsKind.EXTERNAL.equals(args.getKind())) {
			// TODO handle external run in terminal in an external way?
		}
		// TODO use a "real" terminal (like the one in TMF?) for
		// RunInTerminalRequestArgumentsKind.INTEGRATED terminal
		final var cmd = new StringBuilder();
		for (String arg : args.getArgs()) {
			if (arg.contains(" ")) {
				cmd.append('"');
				cmd.append(arg);
				cmd.append('"');
			} else {
				cmd.append(arg);
			}
			cmd.append(' ');
		}
		if (cmd.length() > 0) {
			cmd.setLength(cmd.length() - 1);
		}
		final var processBuilder = new ProcessBuilder(args.getArgs());
		// processBuilder.inheritIO();
		if (args.getCwd() != null) {
			processBuilder.directory(new File(args.getCwd()));
		}
		if (args.getEnv() != null) {
			Set<Entry<String, @Nullable String>> entrySet = args.getEnv().entrySet();
			for (Entry<String, @Nullable String> entry : entrySet) {
				String name = entry.getKey();
				String value = entry.getValue();
				if (value == null) {
					processBuilder.environment().remove(name);
				} else {
					processBuilder.environment().put(name, value);
				}
			}
		}
		try {
			if (DSPPlugin.DEBUG) {
				System.out.println("Launching: " + cmd);
			}
			Process start = processBuilder.start();
			DebugPlugin.newProcess(launch, start, cmd.toString());

			try {
				// TODO the python adapter (that uses the runInTerminal feature) does not like
				// to be told too early that the process is ready
				java.lang.Thread.sleep(1000);
			} catch (InterruptedException e) {
				java.lang.Thread.currentThread().interrupt();
			}
			final var response = new RunInTerminalResponse();
			// TODO, no standard way of getting ID. Can via reflection and/or custom
			// launcher like CDT does.
			response.setProcessId(null); // Explicitly indicate we don't know the process id
			return CompletableFuture.completedFuture(response);
		} catch (IOException e) {
			// TODO
			return CompletableFuture.completedFuture(null);
		}

	}

	@Override
	public void breakpoint(BreakpointEventArguments args) {
		if (breakpointManager != null)
			breakpointManager.breakpointEvent(args);
	}

	@Override
	public void breakpointAdded(IBreakpoint breakpoint) {
		if (breakpointManager != null) {
			breakpointManager.breakpointAdded(breakpoint);
		}
	}

	@Override
	public void breakpointRemoved(IBreakpoint breakpoint, @Nullable IMarkerDelta delta) {
		if (breakpointManager != null) {
			breakpointManager.breakpointRemoved(breakpoint, delta);
		}
	}

	@Override
	public void breakpointChanged(IBreakpoint breakpoint, @Nullable IMarkerDelta delta) {
		if (breakpointManager != null) {
			breakpointManager.breakpointChanged(breakpoint, delta);
		}
	}

	@Override
	public IDebugProtocolServer getDebugProtocolServer() {
		return debugProtocolServer;
	}

	/**
	 * Return the Capabilities of the currently attached debug adapter.
	 *
	 * Clients should not call this method until after the debug adapter has been
	 * initialized.
	 *
	 * @return the current capabilities if they have been retrieved, or else
	 *         return @{code null}
	 */
	public @Nullable Capabilities getCapabilities() {
		return capabilitiesFuture.getNow(null);
	}
}
