/*******************************************************************************
 * Copyright (c) 2022 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.presentation;

import org.eclipse.core.runtime.Adapters;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IWatchExpressionDelegate;
import org.eclipse.debug.core.model.IWatchExpressionListener;
import org.eclipse.debug.core.model.IWatchExpressionResult;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.lsp4e.debug.debugmodel.DSPStackFrame;
import org.eclipse.lsp4e.debug.debugmodel.DSPValue;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateResponse;

public class DAPWatchExpression implements IWatchExpressionDelegate {

	@Override
	public void evaluateExpression(String expression, IDebugElement context, IWatchExpressionListener listener) {
		if (context.getDebugTarget() instanceof DSPDebugTarget dapDebugger) {
			EvaluateArguments args = new EvaluateArguments();
			args.setExpression(expression);
			DSPStackFrame frame = Adapters.adapt(context, DSPStackFrame.class);
			args.setFrameId(frame.getFrameId());
			dapDebugger.getDebugProtocolServer().evaluate(args).thenAccept(
					res -> listener.watchEvaluationFinished(createWatchResult(dapDebugger, expression, res)));
		}
	}

	private IWatchExpressionResult createWatchResult(DSPDebugTarget dapDebugger, String expression,
			EvaluateResponse res) {
		return new IWatchExpressionResult() {
			@Override
			public boolean hasErrors() {
				return false;
			}

			@Override
			public IValue getValue() {
				return new DSPValue(dapDebugger, res.getVariablesReference(), res.getResult());
			}

			@Override
			public String getExpressionText() {
				return expression;
			}

			@Override
			public DebugException getException() {
				return null;
			}

			@Override
			public String[] getErrorMessages() {
				return null;
			}
		};
	}

}
