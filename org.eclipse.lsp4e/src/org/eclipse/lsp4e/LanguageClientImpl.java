package org.eclipse.lsp4e;

import org.eclipse.lsp4e.client.DefaultLanguageClient;

/**
 * This class is deprecated, and only present to avoid breaking
 * existing code due to the refactor done to make the previous
 * class officially available as API.
 * <p>
 * Use {@link DefaultLanguageClient} instead.
 */
@Deprecated(forRemoval = true)
public class LanguageClientImpl extends DefaultLanguageClient {

}
