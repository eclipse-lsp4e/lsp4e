/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   See git history
 *******************************************************************************/
package org.eclipse.lsp4e.operations.completion;

import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.swt.graphics.Image;

public final class LSContextInformation implements IContextInformation {

	private final String contextDisplayString;
	private final String informationDisplayString;
	/**
	 * The location of the active parameter in the signature.
	 */
	private @Nullable LocationInString activeParameter;

	public LSContextInformation(String contextDisplayString, String informationDisplayString,
			@Nullable LocationInString activeParameter) {
		this.contextDisplayString = contextDisplayString;
		this.informationDisplayString = informationDisplayString;
		this.activeParameter = activeParameter;
	}

	@Override
	public String getContextDisplayString() {
		return contextDisplayString;
	}

	@Override
	public String getInformationDisplayString() {
		return informationDisplayString;
	}

	/**
	 * Get the location of the active parameter in the signature.
	 */
	public @Nullable LocationInString getActiveParameter() {
		return activeParameter;
	}

	/**
	 * Set the location of the active parameter in the signature.
	 *
	 * @param activeParameter
	 */
	public void setActiveParameter(@Nullable LocationInString activeParameter) {
		this.activeParameter = activeParameter;
	}

	@Override
	public int hashCode() {
		return Objects.hash(contextDisplayString, informationDisplayString);
	}

	@Override
	public boolean equals(@Nullable Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LSContextInformation other = (LSContextInformation) obj;
		return Objects.equals(contextDisplayString, other.contextDisplayString)
				&& Objects.equals(informationDisplayString, other.informationDisplayString);
	}

	@Override
	public @Nullable Image getImage() {
		return null;
	}

}
