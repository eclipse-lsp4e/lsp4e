/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.console;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4j.debug.ProcessEventArguments;

/**
 * Represents that Debug Adapter process and provides a console to see any
 * communication to/from the adapter.
 */
public class DSPProcess implements IProcess {

	private final DSPDebugTarget target;
	private final DSPStreamsProxy proxy;
	private final @Nullable ProcessEventArguments processArgs;
	private final Optional<ProcessHandle> handle;
	private boolean terminated;

	public DSPProcess(DSPDebugTarget target) {
		this(target, null);
	}

	public DSPProcess(DSPDebugTarget dspDebugTarget, @Nullable ProcessEventArguments args) {
		this.target = dspDebugTarget;
		this.proxy = new DSPStreamsProxy(target.getDebugProtocolServer());
		this.processArgs = args;
		handle = args != null && args.getSystemProcessId() != null ? ProcessHandle.of(args.getSystemProcessId()) : Optional.empty();
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getAdapter(Class<T> adapter) {
		if (adapter.isInstance(handle)) {
			return (T) handle.orElse(null);
		}
		return target.getAdapter(adapter);
	}

	@Override
	public boolean canTerminate() {
		return !isTerminated();
	}

	@Override
	public boolean isTerminated() {
		return handle.map(h -> !h.isAlive()).orElse(terminated);
	}

	/**
	 * Terminates the OS process and notifies the debug framework without issuing
	 * any additional protocol requests to the debug adapter. This is used when the
	 * adapter has already signalled that the debuggee has ended (for example via
	 * 'terminated'/'exited' events).
	 */
	public void terminateWithoutProtocolRequest() {
		terminated = true;
		handle.ifPresent(h -> {
			h.destroy(); // normal termination
			CompletableFuture.runAsync(h::destroyForcibly, CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)); // forced termination if normal is not sufficient
		});
		DebugPlugin.getDefault().fireDebugEventSet(new DebugEvent[] { new DebugEvent(this, DebugEvent.TERMINATE) });
	}

	@Override
	public void terminate() throws DebugException {
		terminateWithoutProtocolRequest();
		target.terminate();
	}

	@Override
	public String getLabel() {
		final var processArgs = this.processArgs;
		if (processArgs != null && processArgs.getName() != null) {
			return processArgs.getName();
		}
		return target.getName();
	}

	@Override
	public ILaunch getLaunch() {
		return target.getLaunch();
	}

	@Override
	public @NonNull DSPStreamsProxy getStreamsProxy() {
		return proxy;
	}

	@Override
	public void setAttribute(String key, @Nullable String value) {
		// TODO
	}

	@Override
	public @Nullable String getAttribute(String key) {
		if (ATTR_PROCESS_ID.equals(key)) {
			return handle.map(ProcessHandle::pid).map(Object::toString).orElse(null);
		}
		return null;
	}

	@Override
	public int getExitValue() throws DebugException {
		if (handle.isPresent() && handle.get().isAlive()) {
			throw new DebugException(Status.error(handle.get().pid() + " is still running"));
		}
		return 0;
	}

}
