package org.eclipse.lsp4e;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

public class MarkerAttributeComputer implements IMarkerAttributeComputer{

	private @Nullable String languageServerId;

	@Override
	public void initilize(@NonNull String languageServerId) {
	  this.languageServerId = languageServerId;
	}

	@Override
	public int attributeCount() {
		return 7;
	}

    @Override
	public Map<String, Object> computeMarkerAttributes(@NonNull IResource resource, @Nullable IDocument document, @NonNull Diagnostic diagnostic)
			throws CoreException {
		Map<String, Object> targetAttributes = new HashMap<>(attributeCount());
		targetAttributes.put(IMarkerAttributeComputer.LSP_DIAGNOSTIC, diagnostic);
		targetAttributes.put(IMarkerAttributeComputer.LANGUAGE_SERVER_ID, this.languageServerId);
		targetAttributes.put(IMarker.MESSAGE, diagnostic.getMessage());
		targetAttributes.put(IMarker.SEVERITY, LSPEclipseUtils.toEclipseMarkerSeverity(diagnostic.getSeverity()));

		if (document != null) {
			try {
				Range range = diagnostic.getRange();
				int documentLength = document.getLength();
				int start = Math.min(LSPEclipseUtils.toOffset(range.getStart(), document), documentLength);
				int end = Math.min(LSPEclipseUtils.toOffset(range.getEnd(), document), documentLength);
				if (start == end && documentLength > end) {
					end++;
					if (document.getLineOfOffset(end) != document.getLineOfOffset(start)) {
						start--;
						end--;
					}
				}
				targetAttributes.put(IMarker.CHAR_START, start);
				targetAttributes.put(IMarker.CHAR_END, end);
				targetAttributes.put(IMarker.LINE_NUMBER, document.getLineOfOffset(start) + 1);
			} catch (BadLocationException ex) {
				LanguageServerPlugin.logError(ex);
			}
		}
		return targetAttributes;
	}

}
