/*******************************************************************************
 * Copyright (c) 2026 Daniel Schmid and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  - Daniel Schmid - Initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.jdt.internal;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServers;
import org.eclipse.lsp4e.LanguageServers.LanguageServerDocumentExecutor;
import org.eclipse.lsp4e.internal.CancellationSupport;
import org.eclipse.lsp4e.jdt.LSJavaTextBlockLanguageDetector;
import org.eclipse.lsp4e.jdt.Messages;
import org.eclipse.lsp4e.operations.completion.LSCompletionProposal;
import org.eclipse.lsp4e.operations.completion.LSContentAssistProcessor;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;

@SuppressWarnings({ "restriction" })
public class JavaTextBlockProposalComputer implements IJavaCompletionProposalComputer {

	private static final Pattern TEXT_BLOCK_LANGUAGE_INDICATOR_COMMENT_PATTERN = Pattern
			.compile("\\W*\\s*language=(\\w+)\\W*", Pattern.CASE_INSENSITIVE);

	private static final TimeUnit TIMEOUT_UNIT = TimeUnit.MILLISECONDS;
	private static final long TIMEOUT_LENGTH = 750;

	private @Nullable LSContentAssistProcessor textBlockContentAssistProcessor;

	private @Nullable String currentErrorMessage;
	private @Nullable List<LSJavaTextBlockLanguageDetector> textBlockLanguageDetectors;

	@Override
	public void sessionStarted() {
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {

		CancellationSupport cancellationSupport = new CancellationSupport();
		currentErrorMessage = null;
		CompletableFuture<List<ICompletionProposal>> proposalFuture = CompletableFuture.supplyAsync(() -> {
			try {
				TextBlockInformation textBlockInformation = findTextBlockInformation(context);
				if (textBlockInformation == null) {
					return List.of();
				}
				TextBlockDocument doc = textBlockInformation.document();
				LanguageServerDocumentExecutor documentExecutor = openDocumentExecutor(textBlockInformation.document());

				try {
					ICompletionProposal[] results = getTextBlockContentAssistProcessor().computeCompletionProposals(doc,
							textBlockInformation.invocationOffsetInContent());
					List<ICompletionProposal> proposals = new ArrayList<>(results.length);
					for (ICompletionProposal proposal : results) {
						if (proposal instanceof LSCompletionProposal p) {
							proposals.add(new TextBlockProposal(p, doc));
						}
					}
					return proposals;
				} finally {
					markTempDocumentClosed(documentExecutor, doc.getURI(), cancellationSupport);
				}
			} catch (CoreException | InvalidInputException e) {
				LanguageServerPlugin.logError(e);
				this.currentErrorMessage = createErrorMessage(e);
			}
			return List.of();
		});
		cancellationSupport.execute(proposalFuture);

		try {
			return proposalFuture.get(TIMEOUT_LENGTH, TIMEOUT_UNIT);
		} catch (InterruptedException e) {
			cancellationSupport.cancel();
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			LanguageServerPlugin.logError(e);
			this.currentErrorMessage = createErrorMessage(e);
		} catch (TimeoutException e) {
			cancellationSupport.cancel();
		}
		return List.of();
	}

	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {

		TextBlockInformation textBlockInformation;
		try {
			textBlockInformation = findTextBlockInformation(context);
			if (textBlockInformation == null) {
				return List.of();
			}

			CancellationSupport cancellationSupport = new CancellationSupport();

			LanguageServerDocumentExecutor executor = openDocumentExecutor(textBlockInformation.document());
			try {
				IContextInformation[] contextInformation = getTextBlockContentAssistProcessor()
						.computeContextInformation(textBlockInformation.document(),
								textBlockInformation.invocationOffsetInContent());
				return contextInformation == null ? List.of() : List.of(contextInformation);
			} finally {
				markTempDocumentClosed(executor, textBlockInformation.document().getURI(), cancellationSupport);
			}
		} catch (CoreException | InvalidInputException e) {
			LanguageServerPlugin.logError(e);
		}
		return List.of();
	}

	@Override
	public @Nullable String getErrorMessage() {
		return currentErrorMessage;
	}

	@Override
	public void sessionEnded() {
	}

	private String createErrorMessage(Exception ex) {
		return Messages.javaSpecificCompletionError + " : " //$NON-NLS-1$
				+ (ex.getMessage() != null ? ex.getMessage() : ex.toString());
	}

	private @Nullable TextBlockInformation findTextBlockInformation(ContentAssistInvocationContext context)
			throws InvalidInputException, CoreException {
		ITextViewer viewer = context.getViewer();
		if (viewer == null) {
			return null;
		}
		IDocument fullDocument = viewer.getDocument();
		if (fullDocument == null || !(context instanceof JavaContentAssistInvocationContext ctx)) {
			return null;
		}
		ICompilationUnit compilationUnit = ctx.getCompilationUnit();
		if (compilationUnit == null
				|| !(compilationUnit.getElementAt(ctx.getInvocationOffset()) instanceof ISourceReference ref)) {
			return null;
		}
		ISourceRange sourceRange = ref.getSourceRange();
		if (sourceRange == null || !SourceRange.isAvailable(sourceRange)) {
			return null;
		}
		// TODO reuse scanner?
		IScanner scanner = ToolFactory.createScanner(true, false, false, false);
		scanner.setSource(fullDocument.get().toCharArray());
		scanner.resetTo(sourceRange.getOffset(), sourceRange.getOffset() + sourceRange.getLength());
		int token;
		String language = null;
		do {
			token = scanner.getNextToken();
			switch (token) {
			case ITerminalSymbols.TokenNameSEMICOLON, ITerminalSymbols.TokenNameLBRACE,
					ITerminalSymbols.TokenNameRBRACE ->
				language = null;
			case ITerminalSymbols.TokenNameCOMMENT_LINE, ITerminalSymbols.TokenNameCOMMENT_BLOCK -> {
				Matcher matcher = TEXT_BLOCK_LANGUAGE_INDICATOR_COMMENT_PATTERN
						.matcher(String.valueOf(scanner.getCurrentTokenSource()));
				if (matcher.matches()) {
					language = matcher.group(1);// TODO ensure group present with custom regex
				} else {
					language = null;
				}
			}
			}
		} while (token != ITerminalSymbols.TokenNameEOF
				&& scanner.getCurrentTokenEndPosition() < ctx.getInvocationOffset());
		if (token != ITerminalSymbols.TokenNameTextBlock) {
			return null;
		}

		URI uri = createURI(language, compilationUnit, viewer, new Region(scanner.getCurrentTokenStartPosition(),
				scanner.getCurrentTokenEndPosition() - scanner.getCurrentTokenStartPosition()));
		if (uri == null) {
			return null;
		}

		char[] rawTokenSource = scanner.getRawTokenSource();

		char[] mappedContent = scanner.getCurrentTokenSource();
		int invocationOffsetWithinRawTokenSource = ctx.getInvocationOffset() - scanner.getCurrentTokenStartPosition();
		int invocationOffsetInContent = TextBlockSourceContentMapper.mapSourceToContent(rawTokenSource,
				mappedContent, invocationOffsetWithinRawTokenSource, invocationOffsetWithinRawTokenSource);

		int spacesToInsert = getNumberOfSpacesBeforePositionIfTrailingWhitespace(rawTokenSource,
				invocationOffsetWithinRawTokenSource);
		int doNotSkipTrailingSpacesAt = -1;
		if (spacesToInsert > 0) {
			// as text blocks trim trailing spaces, we need to add them back at the cursor
			// for completion as people trying to insert something after whitespace
			char[] newMappedContent = new char[mappedContent.length + spacesToInsert];
			System.arraycopy(mappedContent, 0, newMappedContent, 0, invocationOffsetInContent);
			System.arraycopy(rawTokenSource, invocationOffsetWithinRawTokenSource - spacesToInsert, newMappedContent,
					invocationOffsetInContent, spacesToInsert);
			System.arraycopy(mappedContent, invocationOffsetInContent, newMappedContent,
					invocationOffsetInContent + spacesToInsert, mappedContent.length - invocationOffsetInContent);
			mappedContent = newMappedContent;
			invocationOffsetInContent += spacesToInsert;
			doNotSkipTrailingSpacesAt = invocationOffsetWithinRawTokenSource;
		}

		TextBlockDocument doc = createDocumentForTextBlockToken(fullDocument, scanner, uri, mappedContent,
				doNotSkipTrailingSpacesAt);

		if (invocationOffsetInContent == -1) {
			return null;
		}

		return new TextBlockInformation(doc, invocationOffsetInContent);
	}

	private @Nullable URI createURI(@Nullable String language, ICompilationUnit compilationUnit, ITextViewer textViewer,
			IRegion textBlockRegion)
			throws CoreException {
		if (language != null) {
			return URI.create("javatextblock:///" + UUID.randomUUID() + "." + language);
		}

		List<LSJavaTextBlockLanguageDetector> detectors = textBlockLanguageDetectors;
		if (detectors == null) {
			detectors = new ArrayList<>();
			IConfigurationElement[] elements = Platform.getExtensionRegistry()
					.getConfigurationElementsFor("org.eclipse.lsp4e.jdt.LSJavaTextBlockLanguageDetector");
			for (IConfigurationElement element : elements) {
				if (element.createExecutableExtension("class") instanceof LSJavaTextBlockLanguageDetector detector) {
					detectors.add(detector);
				}
			}
			textBlockLanguageDetectors = detectors;
		}
		for (LSJavaTextBlockLanguageDetector detector : detectors) {
			URI uri = detector.createURIForTextBlock(compilationUnit, textViewer, textBlockRegion);
			if (uri != null) {
				return uri;
			}
		}

		return null;
	}

	private int getNumberOfSpacesBeforePositionIfTrailingWhitespace(char[] rawSource, int rawSourcePosition) {
		for (int i = rawSourcePosition; i < rawSource.length && rawSource[i] != '\r' && rawSource[i] != '\n'; i++) {
			if (!Character.isWhitespace(rawSource[i])) {
				return 0;
			}
		}
		for (int i = 0; i < rawSourcePosition - 1; i++) {
			char c = rawSource[rawSourcePosition - 1 - i];
			if (c == '\r' || c == '\n') {
				return 0;
			}
			if (!Character.isWhitespace(c)) {
				return i;
			}
		}
		return 0;
	}

	private LSContentAssistProcessor getTextBlockContentAssistProcessor() {
		LSContentAssistProcessor processor = textBlockContentAssistProcessor;
		if (processor == null) {
			processor = new LSContentAssistProcessor(false, false);
			textBlockContentAssistProcessor = processor;
		}
		return processor;
	}

	private TextBlockDocument createDocumentForTextBlockToken(IDocument fullDocument, IScanner scanner,
			URI uri, char[] mappedContent, int doNotSkipTrailingSpacesAt) {
		return new TextBlockDocument(fullDocument,
				new Region(scanner.getCurrentTokenStartPosition(),
						scanner.getCurrentTokenEndPosition() - scanner.getCurrentTokenStartPosition()),
				scanner.getRawTokenSource(), mappedContent, uri, doNotSkipTrailingSpacesAt);
	}

	private LanguageServerDocumentExecutor openDocumentExecutor(TextBlockDocument doc) {
		return LanguageServers.forDocument(doc)
				.withFilter(capabilities -> capabilities.getCompletionProvider() != null);
	}

	private void markTempDocumentClosed(LanguageServerDocumentExecutor executor, URI uri,
			CancellationSupport cancellationSupport) {
		CompletableFuture<List<Object>> notifyOfDocument = executor.collectAll((w, ls) -> {
			VersionedTextDocumentIdentifier id = new VersionedTextDocumentIdentifier(uri.toString(), 0);
			w.sendNotification(l -> l.getTextDocumentService().didClose(new DidCloseTextDocumentParams(id)));
			return CompletableFuture.completedFuture(null);
		});
		cancellationSupport.execute(notifyOfDocument);
	}

	static class TextBlockProposal implements IJavaCompletionProposal {

		private final LSCompletionProposal proposalInTextBlock;
		private final IDocument doc;

		public TextBlockProposal(LSCompletionProposal proposalInTextBlock, IDocument doc) {
			this.proposalInTextBlock = proposalInTextBlock;
			this.doc = doc;
		}

		@Override
		public @Nullable Point getSelection(IDocument document) {
			return null;
		}

		@Override
		public @Nullable Image getImage() {
			return proposalInTextBlock.getImage();
		}

		@Override
		public String getDisplayString() {
			return proposalInTextBlock.getDisplayString();
		}

		@Override
		public @Nullable IContextInformation getContextInformation() {
			return proposalInTextBlock.getContextInformation();
		}

		@Override
		public @Nullable String getAdditionalProposalInfo() {
			return proposalInTextBlock.getAdditionalProposalInfo();
		}

		@Override
		public void apply(IDocument document) {
			proposalInTextBlock.apply(doc);
		}

		@Override
		public int getRelevance() {
			return new LSJavaProposal(proposalInTextBlock).getRelevance();
		}
	}

	record TextBlockInformation(TextBlockDocument document, int invocationOffsetInContent) {

	}

}
