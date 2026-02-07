package org.eclipse.lsp4e.test;


import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class PathsTest {

	@Test
	void nonAscii() throws Exception {
		URI uri = Paths.get("Eclipse アプリケーション/").toUri();
		assertNotNull( Paths.get(uri));
	}
	
}
