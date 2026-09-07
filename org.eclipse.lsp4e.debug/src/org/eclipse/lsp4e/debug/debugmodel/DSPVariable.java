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

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.ValueFormat;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

public class DSPVariable extends DSPDebugElement implements IVariable {

	private final Integer parentVariablesReference;
	private final String name;
	private DSPValue dspValue;

	public DSPVariable(DSPDebugTarget debugTarget, Integer parentVariablesReference, String name, String value,
			Integer childrenVariablesReference) {
		super(debugTarget);
		this.parentVariablesReference = parentVariablesReference;
		this.name = name;
		this.dspValue = new DSPValue(this, childrenVariablesReference, value);
	}

	@Override
	public void setValue(String expression) throws DebugException {
		final var setVariableArgs = new SetVariableArguments();
		setVariableArgs.setVariablesReference(parentVariablesReference);
		setVariableArgs.setValue(expression);
		setVariableArgs.setName(getName());
		setVariableArgs.setFormat(new ValueFormat());
		IDebugProtocolServer debugAdapter = getDebugProtocolServer();
		debugAdapter.setVariable(setVariableArgs).thenAcceptAsync(res -> {
			String v = res.getValue();
			if (v == null) {
				v = expression;
			}
			this.dspValue = new DSPValue(this, res.getVariablesReference(), v);
			this.fireChangeEvent(DebugEvent.CONTENT);
		});
	}

	@Override
	public void setValue(IValue value) throws DebugException {
		// TODO
	}

	@Override
	public boolean supportsValueModification() {
		final var capabilities = getDebugTarget().getCapabilities();
		return capabilities != null && Boolean.TRUE.equals(capabilities.getSupportsSetVariable());
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
	 * @return The variable ID sent by the server that underlies this object
	 */
	public Integer getParentVariablesReferenceId() {
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
