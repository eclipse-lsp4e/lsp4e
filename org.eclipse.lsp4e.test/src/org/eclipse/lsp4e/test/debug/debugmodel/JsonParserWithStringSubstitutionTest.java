/*******************************************************************************
 * Copyright (c) 2023 HIS Hochschul-Informations-System eG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.debug.debugmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.variables.IDynamicVariable;
import org.eclipse.core.variables.IStringVariable;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.IValueVariable;
import org.eclipse.core.variables.IValueVariableListener;
import org.eclipse.lsp4e.debug.debugmodel.JsonParserWithStringSubstitution;
import org.eclipse.lsp4e.test.utils.AbstractTest;
import org.junit.jupiter.api.Test;

public class JsonParserWithStringSubstitutionTest extends AbstractTest {

	private static final class StringVariableManagerMock implements IStringVariableManager {

		String variableReference = "";
		String variableReplacement = "";

		StringVariableManagerMock(String variableReference, String variableReplacement) {
			this.variableReference = variableReference;
			this.variableReplacement = variableReplacement;
		}

		StringVariableManagerMock() {}

		@Override
		public IStringVariable[] getVariables() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IValueVariable[] getValueVariables() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IValueVariable getValueVariable(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public IDynamicVariable[] getDynamicVariables() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IDynamicVariable getDynamicVariable(String name) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getContributingPluginId(IStringVariable variable) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String performStringSubstitution(String expression) throws CoreException {
	        if (!expression.contains(variableReference)) {
	            throw new CoreException(Status.error("Unable to resolve variable"));
	        }
			return expression.replace(variableReference, variableReplacement);
		}

		@Override
		public String performStringSubstitution(String expression, boolean reportUndefinedVariables)
				throws CoreException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void validateStringVariables(String expression) throws CoreException {
			throw new UnsupportedOperationException();
		}

		@Override
		public IValueVariable newValueVariable(String name, String description) {
			throw new UnsupportedOperationException();
		}

		@Override
		public IValueVariable newValueVariable(String name, String description, boolean readOnly, String value) {
			throw new UnsupportedOperationException();

		}

		@Override
		public void addVariables(IValueVariable[] variables) throws CoreException {
			throw new UnsupportedOperationException();
		}

		@Override
		public void removeVariables(IValueVariable[] variables) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void addValueVariableListener(IValueVariableListener listener) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void removeValueVariableListener(IValueVariableListener listener) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String generateVariableExpression(String varName, String arg) {
			throw new UnsupportedOperationException();
		}
	}

	/**
	 * {@link JsonParserWithStringSubstitution} can only
	 * process json strings that define an object at the top level.
	 *
	 * Test if an exception is thrown when the json is _not_ an object at the top level.
	 */
	@Test
	public void testThrowsIllegaStateException() throws IllegalStateException, CoreException {
		final var json = "[\"value1\", \"value2\", \"value3\"]";
		final var jsonParser = new JsonParserWithStringSubstitution(new StringVariableManagerMock());
		assertThrows(IllegalStateException.class, () -> jsonParser.parseJsonObject(json));
	}

	/**
	 * {@link JsonParserWithStringSubstitution} can only substitute
	 * variables known to the {@link IStringVariableManager}.
	 *
	 * Test if an exception is thrown when the json contains a variable that is _not_
	 * known to the {@link IStringVariableManager}.
	 */
	@Test
	public void testThrowsCoreException() throws IllegalStateException, CoreException {
		final var json = "{\"key\":\"unknown_variable\"}";
		final var stringVariableManager = new StringVariableManagerMock("Test", "Test");
		final var jsonParser = new JsonParserWithStringSubstitution(stringVariableManager);
		assertThrows(CoreException.class, () -> jsonParser.parseJsonObject(json));
	}

	/**
	 * Substitute a known variable in a json object
	 */
	@Test
	public void testSubstituteVariableInJsonObject() throws IllegalStateException, CoreException {
		// # SETUP #
		final var key = "key";
		final var variableReference = "variableReference";
		final var variableReplacement = "variableReplacement";
		final var json = "{\"" + key + "\":\"" + variableReference + "\"}";
		final var stringVariableManager = new StringVariableManagerMock(variableReference, variableReplacement);
		final var jsonParser = new JsonParserWithStringSubstitution(stringVariableManager);

		// # TEST #
		Map<String, Object> parsedJson = jsonParser.parseJsonObject(json);

		// # ASSERT #
		final var resultValue = (String) parsedJson.get(key);
		assertEquals(variableReplacement, resultValue);
	}

	/**
	 * Substitute a known variable in an array in a json object.
	 */
	@Test
	public void testSubstituteVariableInJsonObjectWithArray() throws IllegalStateException, CoreException {
		// # SETUP #
		final var key = "key";
		final var variableReference = "variableReference";
		final var variableReplacement = "variableReplacement";
		final var json = "{\"" + key + "\":[\"" + variableReference + "\"]}";
		final var stringVariableManager = new StringVariableManagerMock(variableReference, variableReplacement);
		final var jsonParser = new JsonParserWithStringSubstitution(stringVariableManager);

		// # TEST #
		Map<String, Object> parsedJson = jsonParser.parseJsonObject(json);

		// # ASSERT #
		@SuppressWarnings("unchecked")
		final var resultArray =  (ArrayList<Object>) parsedJson.get(key);
		final var resultValue = (String) resultArray.get(0);
		assertEquals(variableReplacement, resultValue);
	}

	/**
	 * Substitute a known variable in a json object in a json object.
	 */
	@Test
	public void testSubstituteVariableInJsonObjectInJsonObject() throws IllegalStateException, CoreException {
		// # SETUP #
		final var key1 = "key1";
		final var key2 = "key2";
		final var variableReference = "variableReference";
		final var variableReplacement = "variableReplacement";
		final var json = "{\"" + key1 + "\":{\"" + key2 + "\":\"" + variableReference + "\"}}";
 		final var stringVariableManager = new StringVariableManagerMock(variableReference, variableReplacement);
		final var jsonParser = new JsonParserWithStringSubstitution(stringVariableManager);

		// # TEST #
		Map<String, Object> parsedJson = jsonParser.parseJsonObject(json);

		// # ASSERT #
		@SuppressWarnings("unchecked")
		final var secondObject =  (Map<String, Object>) parsedJson.get(key1);
		final var resultValue = (String) secondObject.get(key2);
		assertEquals(variableReplacement, resultValue);
	}
}
