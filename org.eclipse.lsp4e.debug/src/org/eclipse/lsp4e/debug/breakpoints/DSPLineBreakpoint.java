/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.breakpoints;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.LineBreakpoint;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.debug.DSPPlugin;

public class DSPLineBreakpoint extends LineBreakpoint {

	public static final String ID = "org.eclipse.lsp4e.debug.breakpoints.markerType.lineBreakpoint";

	/** Marker attribute key for a conditional expression. */
	public static final String ATTR_CONDITION = "org.eclipse.lsp4e.debug.breakpoints.condition";

	/** Marker attribute key for inline breakpoint column (1-based). */
	public static final String ATTR_COLUMN = "org.eclipse.lsp4e.debug.breakpoints.column";

	/** Marker attribute key for hit condition expression. */
	public static final String ATTR_HIT_CONDITION = "org.eclipse.lsp4e.debug.breakpoints.hitCondition";

	public DSPLineBreakpoint() {
	}

	public DSPLineBreakpoint(final IResource resource, final int lineNumber) throws CoreException {
		run(getMarkerRule(resource), monitor -> {
			IMarker marker = resource.createMarker(ID);
			setMarker(marker);
			marker.setAttribute(IBreakpoint.ENABLED, Boolean.TRUE);
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			marker.setAttribute(IBreakpoint.ID, getModelIdentifier());
			marker.setAttribute(IMarker.MESSAGE, resource.getName() + " [line: " + lineNumber + "]");
		});
	}

	public DSPLineBreakpoint(final IResource resource, String fileName, final int lineNumber) throws CoreException {
		run(getMarkerRule(resource), monitor -> {
			IMarker marker = resource.createMarker(ID);
			setMarker(marker);
			marker.setAttribute(IBreakpoint.ENABLED, Boolean.TRUE);
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			marker.setAttribute(IBreakpoint.ID, getModelIdentifier());
			marker.setAttribute(IMarker.MESSAGE, resource.getName() + " [line: " + lineNumber + "]");
		});
	}

	@Override
	public String getModelIdentifier() {
		return DSPPlugin.ID_DSP_DEBUG_MODEL;
	}

	/**
	 * @return the inline breakpoint column (1-based) or {@code -1} if unset.
	 */
	public int getColumn() {
		final IMarker m = getMarker();
		return m == null ? -1 : m.getAttribute(ATTR_COLUMN, -1);
	}

	/**
	 * Sets or clears the inline breakpoint column. Values <= 0 clear the column.
	 */
	public void setColumn(final int column) throws CoreException {
		final IMarker m = getMarker();
		if (m != null) {
			m.setAttribute(ATTR_COLUMN, column <= 0 ? null : column);
		}
	}

	/**
	 * @return the breakpoint condition or {@code null} if none.
	 */
	public @Nullable String getCondition() {
		final IMarker m = getMarker();
		return m == null ? null : m.getAttribute(ATTR_CONDITION, (String) null);
	}

	/**
	 * Sets or clears the breakpoint condition. A {@code null} or blank value clears
	 * the condition.
	 */
	public void setCondition(final @Nullable String condition) throws CoreException {
		final IMarker m = getMarker();
		if (m != null) {
			m.setAttribute(ATTR_CONDITION, condition == null || condition.isBlank() ? null : condition);
		}
	}

	/**
	 * @return the hit condition or {@code null} if none.
	 */
	public @Nullable String getHitCondition() {
		final IMarker m = getMarker();
		return m == null ? null : m.getAttribute(ATTR_HIT_CONDITION, (String) null);
	}

	/**
	 * Sets or clears the hit condition. A {@code null} or blank value clears it.
	 */
	public void setHitCondition(final @Nullable String hitCondition) throws CoreException {
		final IMarker m = getMarker();
		if (m != null) {
			m.setAttribute(ATTR_HIT_CONDITION, hitCondition == null || hitCondition.isBlank() ? null : hitCondition);
		}
	}
}
