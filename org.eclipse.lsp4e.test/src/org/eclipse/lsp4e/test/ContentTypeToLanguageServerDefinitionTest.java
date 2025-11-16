/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rastislav Wagner (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.lsp4e.ContentTypeToLanguageServerDefinition;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry;
import org.eclipse.lsp4e.test.utils.AbstractTest;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ContentTypeToLanguageServerDefinitionTest extends AbstractTest {

	public static final String SERVER_TO_DISABLE = "org.eclipse.lsp4e.test.server.disable";
	public static final String DISABLED_CONTENT_TYPE = "org.eclipse.lsp4e.test.content-type-disabled";
	public static final String DISABLED_SERVER_PREF = SERVER_TO_DISABLE + "/" + DISABLED_CONTENT_TYPE;

	@BeforeAll
	public static void setup() {
		LanguageServerPlugin.getDefault().getPreferenceStore().setValue(DISABLED_SERVER_PREF, "false");
	}

	@AfterAll
	public static void cleanup() {
		TestUtils.getDisabledLS().setUserEnabled(false);
	}

	@Test
	public void testDisabledLanguageServerMappingsPreference() {
		List<ContentTypeToLanguageServerDefinition> disabledDefinitions = LanguageServersRegistry.getInstance()
				.getContentTypeToLSPExtensions().stream().filter(lsDefinition -> !lsDefinition.isEnabled(null))
				.toList();
		assertTrue(disabledDefinitions.contains(TestUtils.getDisabledLS()));
	}

	@Test
	public void testDisableLanguageServerMapping() {
		ContentTypeToLanguageServerDefinition lsDefinition = TestUtils.getDisabledLS();

		lsDefinition.setUserEnabled(false);
		assertFalse(lsDefinition.isEnabled(null));
		assertTrue(LanguageServerPlugin.getDefault().getPreferenceStore().contains(DISABLED_SERVER_PREF));
		assertFalse(LanguageServerPlugin.getDefault().getPreferenceStore().getBoolean(DISABLED_SERVER_PREF));

		lsDefinition.setUserEnabled(true);
		assertTrue(lsDefinition.isEnabled(null));
		assertTrue(LanguageServerPlugin.getDefault().getPreferenceStore().contains(DISABLED_SERVER_PREF));
		assertTrue(LanguageServerPlugin.getDefault().getPreferenceStore().getBoolean(DISABLED_SERVER_PREF));
	}
}
