/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IRegisterGroup;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.StackFrame;

public class DSPStackFrame extends DSPDebugElement implements IStackFrame {

	private static final IRegisterGroup[] NO_REGISTER_GROUPS = new IRegisterGroup[0];

	private final DSPThread thread;
	private StackFrame stackFrame;
	private final int depth;
	private IVariable @Nullable [] cachedVariables;

	public DSPStackFrame(DSPThread thread, StackFrame stackFrame, int depth) {
		super(thread.getDebugTarget());
		this.thread = thread;
		this.stackFrame = stackFrame;
		this.depth = depth;
	}

	public DSPStackFrame replace(StackFrame newStackFrame, int newDepth) {
		if (newDepth == depth && Objects.equals(newStackFrame.getSource(), stackFrame.getSource())) {
			stackFrame = newStackFrame;
			cachedVariables = null;
			return this;
		}
		return new DSPStackFrame(thread, newStackFrame, newDepth);
	}

	@Override
	public void terminate() throws DebugException {
		getDebugTarget().terminate();
	}

	@Override
	public boolean isTerminated() {
		return getDebugTarget().isTerminated();
	}

	@Override
	public boolean canTerminate() {
		return getDebugTarget().canTerminate();
	}

	@Override
	public void suspend() throws DebugException {
		getThread().suspend();
	}

	@Override
	public void resume() throws DebugException {
		getThread().resume();
	}

	@Override
	public boolean isSuspended() {
		return getDebugTarget().isSuspended();
	}

	@Override
	public boolean canSuspend() {
		return getThread().canSuspend();
	}

	@Override
	public boolean canResume() {
		return getThread().canResume();
	}

	@Override
	public void stepReturn() throws DebugException {
		getThread().stepReturn();
	}

	@Override
	public void stepOver() throws DebugException {
		getThread().stepOver();
	}

	@Override
	public void stepInto() throws DebugException {
		getThread().stepInto();
	}

	@Override
	public boolean isStepping() {
		return getThread().isStepping();
	}

	@Override
	public boolean canStepReturn() {
		return getThread().canStepReturn();
	}

	@Override
	public boolean canStepOver() {
		return getThread().canStepOver();
	}

	@Override
	public boolean canStepInto() {
		return getThread().canStepInto();
	}

	@Override
	public boolean hasVariables() throws DebugException {
		return true;
	}

	@Override
	public boolean hasRegisterGroups() throws DebugException {
		return false;
	}

	@Override
	public IVariable[] getVariables() throws DebugException {
		var cachedVariables = this.cachedVariables;
		if (cachedVariables == null) {
			final var arguments = new ScopesArguments();
			arguments.setFrameId(stackFrame.getId());
			Scope[] scopes = complete(getDebugTarget().getDebugProtocolServer().scopes(arguments)).getScopes();
			final var vars = new ArrayList<DSPVariable>();
			for (Scope scope : scopes) {
				final var variable = new DSPVariable(getDebugTarget(), -1, scope.getName(), "",
						scope.getVariablesReference());
				vars.add(variable);
			}
			cachedVariables = this.cachedVariables = vars.toArray(IVariable[]::new);
		}
		return cachedVariables;
	}

	@Override
	public DSPThread getThread() {
		return thread;
	}

	@Override
	public IRegisterGroup[] getRegisterGroups() throws DebugException {
		return NO_REGISTER_GROUPS;
	}

	@Override
	public String getName() throws DebugException {
		return stackFrame.getName();
	}

	@Override
	public int getLineNumber() throws DebugException {
		return stackFrame.getLine();
	}

	@Override
	public int getCharStart() throws DebugException {
		return -1;
	}

	@Override
	public int getCharEnd() throws DebugException {
		return -1;
	}

	public @Nullable String getSourceName() {
		final var source = stackFrame.getSource();
		return source == null ? null : source.getPath();
	}

	public Integer getFrameId() {
		return stackFrame.getId();
	}

	public int getFrameInstructionAddressBits() {
		String addr = stackFrame.getInstructionPointerReference();
		if (addr == null || addr.length() > 10) {
			return 64;
		}
		return 32;
	}

	public BigInteger getFrameInstructionAddress() {
		String addr = stackFrame.getInstructionPointerReference();
		if (addr == null || addr.length() == 0) {
			return BigInteger.ZERO;
		}
		if (addr.startsWith("0x")) {
			addr = addr.substring(2);
		}
		return new BigInteger(addr, 16);
	}

	@Override
	public String toString() {
		return "StackFrame [depth=" + depth + ", line=" + stackFrame.getLine() + ", thread=" + thread + ", stackFrame="
				+ stackFrame + "]";
	}

	/**
	 * Return the stack depth of this frame. The top of the stack is 0.
	 *
	 * @return stack depth
	 */
	public int getDepth() {
		return depth;
	}

	/**
	 * Evaluate the given expression in the context of this frame.
	 *
	 * @param expression any expression
	 * @return future with an IVariable that has the result
	 */
	public CompletableFuture<IVariable> evaluate(String expression) {
		final var args = new EvaluateArguments();
		args.setContext(EvaluateArgumentsContext.HOVER);
		args.setFrameId(getFrameId());
		args.setExpression(expression);
		CompletableFuture<EvaluateResponse> evaluate = getDebugProtocolServer().evaluate(args);
		CompletableFuture<IVariable> future = evaluate.thenApply(res -> new DSPVariable(getDebugTarget(),
				res.getVariablesReference(), expression, res.getResult(), res.getVariablesReference()));
		return future;

	}
}
