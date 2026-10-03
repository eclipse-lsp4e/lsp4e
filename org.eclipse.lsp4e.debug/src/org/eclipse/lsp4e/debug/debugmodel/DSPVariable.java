/*******************************************************************************
 * Copyright (c) 2017-2019 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Pierre-Yves B. <pyvesdev@gmail.com> - Bug 553139 - NullPointerException if the debug adapter does not support SetVariable
 *******************************************************************************/
package org.eclipse.lsp4e.debug.debugmodel;

import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4j.debug.SetExpressionArguments;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.ValueFormat;

public class DSPVariable extends DSPDebugElement implements IVariable {

	// null for scopes/evaluate results not contained in a DAP variables container
	private final @Nullable Integer parentVariablesReference;
	// set for evaluate results, whose value is modified via setExpression in this
	// frame
	private final @Nullable Integer evaluateFrameId;
	private final String name;
	private DSPValue dspValue;

	public DSPVariable(DSPDebugTarget debugTarget, @Nullable Integer parentVariablesReference, String name,
			String value, Integer childrenVariablesReference) {
		this(debugTarget, parentVariablesReference, null, name, value, childrenVariablesReference);
	}

	DSPVariable(DSPDebugTarget debugTarget, @Nullable Integer parentVariablesReference,
			@Nullable Integer evaluateFrameId, String name, String value, Integer childrenVariablesReference) {
		super(debugTarget);
		this.parentVariablesReference = parentVariablesReference;
		this.evaluateFrameId = evaluateFrameId;
		this.name = name;
		this.dspValue = new DSPValue(this, childrenVariablesReference, value);
	}

	@Override
	public void setValue(String expression) throws DebugException {
		final Integer parentRef = parentVariablesReference;
		final Integer frameId = evaluateFrameId;
		final CompletableFuture<@Nullable Void> update;
		if (parentRef != null) {
			final var setVariableArgs = new SetVariableArguments();
			setVariableArgs.setVariablesReference(parentRef);
			setVariableArgs.setValue(expression);
			setVariableArgs.setName(getName());
			setVariableArgs.setFormat(new ValueFormat());
			update = getDebugProtocolServer().setVariable(setVariableArgs)
					.thenAcceptAsync(res -> updateValue(res.getVariablesReference(), res.getValue(), expression));
		} else if (frameId != null) {
			final var setExpressionArgs = new SetExpressionArguments();
			setExpressionArgs.setFrameId(frameId);
			setExpressionArgs.setExpression(getName());
			setExpressionArgs.setValue(expression);
			setExpressionArgs.setFormat(new ValueFormat());
			update = getDebugProtocolServer().setExpression(setExpressionArgs)
					.thenAcceptAsync(res -> updateValue(res.getVariablesReference(), res.getValue(), expression));
		} else {
			throw new DebugException(new Status(IStatus.ERROR, DSPPlugin.PLUGIN_ID, DebugException.NOT_SUPPORTED,
					"Variable '" + name + "' does not support value modification", null)); //$NON-NLS-1$ //$NON-NLS-2$
		}
		update.exceptionally(e -> {
			DSPPlugin.logError("Failed to set value of '" + name + "'", e); //$NON-NLS-1$ //$NON-NLS-2$
			return null;
		});
	}

	private void updateValue(@Nullable Integer childrenVariablesReference, @Nullable String value,
			String fallbackValue) {
		this.dspValue = new DSPValue(this, childrenVariablesReference == null ? 0 : childrenVariablesReference,
				value == null ? fallbackValue : value);
		this.fireChangeEvent(DebugEvent.CONTENT);
	}

	@Override
	public void setValue(IValue value) throws DebugException {
		// TODO
	}

	@Override
	public boolean supportsValueModification() {
		final var capabilities = getDebugTarget().getCapabilities();
		if (capabilities == null) {
			return false;
		}
		if (parentVariablesReference != null) {
			return Boolean.TRUE.equals(capabilities.getSupportsSetVariable());
		}
		return evaluateFrameId != null && Boolean.TRUE.equals(capabilities.getSupportsSetExpression());
	}

	@Override
	public boolean verifyValue(String expression) throws DebugException {
		return true;
	}

	@Override
	public boolean verifyValue(IValue value) throws DebugException {
		// TODO
		return false;
	}

	@Override
	public IValue getValue() throws DebugException {
		return this.dspValue;
	}

	/**
	 * @return variables reference of the parent container that underlies this
	 *         object. Note that this is only valid while the thread owning its
	 *         stack frame remains stopped: see
	 *         {@link https://microsoft.github.io/debug-adapter-protocol/overview}.
	 *         {@code null} if this object is not contained in a variables
	 *         container, e.g. for evaluate results.
	 */
	public @Nullable Integer getParentVariablesReference() {
		return this.parentVariablesReference;
	}

	@Override
	public String getName() throws DebugException {
		return name;
	}

	@Override
	public String getReferenceTypeName() throws DebugException {
		// TODO
		return name;
	}

	@Override
	public boolean hasValueChanged() throws DebugException {
		// TODO
		return false;
	}
}
