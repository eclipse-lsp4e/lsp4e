/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.test.message;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ide.IDE;
import org.junit.jupiter.api.Test;

public class ShowMessageTest extends AbstractTestWithProject {

	@Test
	public void testShowMessage() throws CoreException {
		IFile file = TestUtils.createUniqueTestFile(project, "");
		IDE.openEditor(UI.getActivePage(), file);
		final var messageContent = "test notification " + System.currentTimeMillis();
		final var message = new MessageParams(MessageType.Error, messageContent);
		Display display = Display.getDefault();
		Set<Shell> currentShells = Stream.of(display.getShells()).filter(Shell::isVisible).collect(Collectors.toSet());
		List<LanguageClient> remoteProxies = MockLanguageServer.INSTANCE.getRemoteProxies();
		remoteProxies.forEach(client -> client.showMessage(message));
		waitForAndAssertCondition(3_000,
				() -> Stream.of(display.getShells()).filter(Shell::isVisible).count() > currentShells.size());
	}
}
