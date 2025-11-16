/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.test.color;

import static org.eclipse.lsp4e.test.utils.TestUtils.waitForAndAssertCondition;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.test.utils.AbstractTestWithProject;
import org.eclipse.lsp4e.test.utils.TestUtils;
import org.eclipse.lsp4e.tests.mock.MockLanguageServer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.ide.IDE;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ColorTest extends AbstractTestWithProject {

	private RGB color;

	@BeforeEach
	public void setUp() {
		color = new RGB(56, 78, 90); // a color that's not likely used anywhere else
		MockLanguageServer.INSTANCE.getTextDocumentService().setDocumentColors(List.of(new ColorInformation(new Range(new Position(0, 0), new Position(0, 1)), new Color(color.red / 255., color.green / 255., color.blue / 255., 255))));
	}

	@Test
	public void testColorProvider() throws Exception {
		ITextViewer viewer = TestUtils.openTextViewer(TestUtils.createUniqueTestFile(project, "\u2588\u2588\u2588\u2588\u2588"));
		StyledText widget = viewer.getTextWidget();
		waitForAndAssertCondition(3_000, widget.getDisplay(), () -> containsColor(widget, color, 10));
	}

	@Test
	public void testColorProviderExternalFile() throws Exception {
		File file = TestUtils.createTempFile("testColorProviderExternalFile", ".lspt");
		try (var out = new FileOutputStream(file)) {
			out.write("\u2588\u2588\u2588\u2588\u2588".getBytes());
		}
		ITextViewer viewer = LSPEclipseUtils.getTextViewer(IDE.openEditorOnFileStore(UI.getActivePage(), EFS.getStore(file.toURI())));
		StyledText widget = viewer.getTextWidget();
		waitForAndAssertCondition(3_000, widget.getDisplay(), () -> containsColor(widget, color, 10));
	}

	/**
	 * TODO consider reusing directly code from Test_org_eclipse_swt_custom_StyledText
	 */
	public static boolean containsColor(Control widget, RGB expectedRGB, int tolerance) {
		if (widget.getSize().x == 0) {
			return false;
		}
		final var gc = new GC(widget);
		final var image = new Image(widget.getDisplay(), widget.getSize().x, widget.getSize().y);
		gc.copyArea(image, 0, 0);
		gc.dispose();
		ImageData imageData = image.getImageData();
		int bestYet = 255;
		for (int x = 0; x < image.getBounds().width; x++) {
			for (int y = 0; y < image.getBounds().height; y++) {
				RGB pixelRGB = imageData.palette.getRGB(imageData.getPixel(x, y));
				final int dRGB = distance(expectedRGB, pixelRGB);
				bestYet = Math.min(bestYet, dRGB);
				if (dRGB < tolerance) {
					image.dispose();
					return true;
				}
			}
		}
		image.dispose();
		System.err.println("Smallest dRGB was " + bestYet);
		return false;
	}

	private static int distance(RGB from, RGB to) {
		final int dR = from.red - to.red;
		final int dG = from.green - to.green;
		final int dB = from.blue - to.blue;

		return (int) Math.sqrt((dR * dR + dG * dG + dB * dB) / 3);
	}
}
