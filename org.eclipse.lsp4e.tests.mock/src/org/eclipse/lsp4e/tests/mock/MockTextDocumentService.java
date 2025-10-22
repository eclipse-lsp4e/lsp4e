/*******************************************************************************
 * Copyright (c) 2017, 2022 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Mickael Istria (Red Hat Inc.) - Support for delay and mock references
 *  Lucas Bullen (Red Hat Inc.) - Bug 508458 - Add support for codelens
 *  Pierre-Yves B. <pyvesdev@gmail.com> - Bug 525411 - [rename] input field should be filled with symbol to rename
 *  Rubén Porras Campo (Avaloq Evolution AG) - Add support for willSaveWaitUntil.
 *  Joao Dinis Ferreira (Avaloq Group AG) - Add support for position-dependent mock document highlights
 *******************************************************************************/
package org.eclipse.lsp4e.tests.mock;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.DocumentLinkParams;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.LinkedEditingRangeParams;
import org.eclipse.lsp4j.LinkedEditingRanges;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.PrepareRenameResult;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.SemanticTokensParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.TypeDefinitionParams;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.eclipse.lsp4j.WillSaveTextDocumentParams;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Either3;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

public class MockTextDocumentService implements TextDocumentService {

	private CompletionList mockCompletionList;
	private Hover mockHover;
	private List<? extends Location> mockDefinitionLocations;
	private List<? extends LocationLink> mockTypeDefinitions;
	private List<? extends TextEdit> mockFormattingTextEdits;
	private SignatureHelp mockSignatureHelp;
	private List<CodeLens> mockCodeLenses;
	private List<DocumentLink> mockDocumentLinks;
	private Map<Position, List<? extends DocumentHighlight>> mockDocumentHighlights;
	private LinkedEditingRanges mockLinkedEditingRanges;

	private CompletableFuture<DidOpenTextDocumentParams> didOpenCallback;
	private CompletableFuture<DidSaveTextDocumentParams> didSaveCallback;
	private CompletableFuture<DidCloseTextDocumentParams> didCloseCallback;
	private List<TextEdit> mockWillSaveWaitUntilTextEdits;
	private ConcurrentLinkedQueue<DidChangeTextDocumentParams> didChangeEvents = new ConcurrentLinkedQueue<>();

	private Function<?, ? extends CompletableFuture<?>> _futureFactory;
	private final List<LanguageClient> remoteProxies = new CopyOnWriteArrayList<>();
	private Location[] mockReferences = new Location[0];
	private List<Diagnostic> diagnostics;
	private List<Either<Command, CodeAction>> mockCodeActions;
	private List<ColorInformation> mockDocumentColors;
	private WorkspaceEdit mockRenameEdit;
	private Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior> mockPrepareRenameResult;
	private List<DocumentSymbol> documentSymbols;
	private SemanticTokens mockSemanticTokens;
	private List<FoldingRange> foldingRanges;
	public int codeActionRequests = 0;

	public <U> MockTextDocumentService(Function<U, CompletableFuture<U>> futureFactory) {
		this._futureFactory = futureFactory;
		// Some default values for mocks, can be overridden
		final var item = new CompletionItem();
		item.setLabel("Mock completion item");
		mockCompletionList = new CompletionList(false, List.of(item));
		mockHover = new Hover(List.of(Either.forLeft("Mock hover")), null);
		mockPrepareRenameResult = Either3
				.forSecond(new PrepareRenameResult(new Range(new Position(0, 0), new Position(0, 0)), "placeholder"));
		this.documentSymbols = Collections.emptyList();
		this.codeActionRequests = 0;
	}

	@SuppressWarnings("unchecked")
	private <U> CompletableFuture<U> futureFactory(U value) {
		return ((Function<U, CompletableFuture<U>>) this._futureFactory).apply(value);
	}

	@Override
	public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
		return futureFactory(Either.forRight(mockCompletionList));
	}

	@Override
	public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<Hover> hover(HoverParams position) {
		return CompletableFuture.completedFuture(mockHover);
	}

	@Override
	public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams position) {
		return CompletableFuture.completedFuture(mockSignatureHelp);
	}

	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(
			DefinitionParams position) {
		return CompletableFuture.completedFuture(Either.forLeft(mockDefinitionLocations));
	}

	@Override
	public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
		return futureFactory(List.of(this.mockReferences));
	}

	@Override
	public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
		return CompletableFuture.completedFuture((mockDocumentHighlights != null) //
				? mockDocumentHighlights.getOrDefault(params.getPosition(), Collections.emptyList())
				: null);
	}

	@Override
	public CompletableFuture<LinkedEditingRanges> linkedEditingRange(LinkedEditingRangeParams position) {
		return CompletableFuture.completedFuture(mockLinkedEditingRanges);
	}

	@Override
	public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(
			DocumentSymbolParams params) {
		String documentURI = params.getTextDocument().getUri();
		if (!documentURI.startsWith("file:///")) {
			throw new IllegalArgumentException("invalid uri: " + documentURI);
		}
		return CompletableFuture.completedFuture(documentSymbols.stream().map(symbol -> {
			Either<SymbolInformation, DocumentSymbol> res = Either.forRight(symbol);
			return res;
		}).toList());
	}

	@Override
	public CompletableFuture<List<DocumentLink>> documentLink(DocumentLinkParams params) {
		return CompletableFuture.completedFuture(mockDocumentLinks);
	}

	@Override
	public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
		codeActionRequests++;
		return futureFactory(this.mockCodeActions);
	}

	@Override
	public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
		if (mockCodeLenses != null) {
			return CompletableFuture.completedFuture(mockCodeLenses);
		}
		File file = new File(URI.create(params.getTextDocument().getUri()));
		if (file.exists() && file.length() > 100) {
			return CompletableFuture
					.completedFuture(List.of(new CodeLens(new Range(new Position(1, 0), new Position(1, 1)),
							new Command("Hi, I'm a CodeLens", null), null)));
		}
		return CompletableFuture.completedFuture(Collections.emptyList());
	}

	@Override
	public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
		return CompletableFuture.completedFuture(mockFormattingTextEdits);
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
		Range range = params.getRange();
		List<? extends TextEdit> rangeEdits = mockFormattingTextEdits.stream()
				.filter(edit -> inRange(range, edit.getRange())).collect(Collectors.toList());

		return CompletableFuture.completedFuture(rangeEdits);
	}

	private boolean inRange(Range containingRange, Range includedRange) {
		if (containingRange == null || includedRange == null) {
			throw new IllegalArgumentException();
		}

		if (containingRange.getStart().getLine() <= includedRange.getStart().getLine()
				&& containingRange.getEnd().getLine() >= includedRange.getEnd().getLine()) {
			return (containingRange.getStart().getLine() != includedRange.getStart().getLine()
					|| containingRange.getStart().getCharacter() <= includedRange.getStart().getCharacter())
					&& (containingRange.getEnd().getLine() != includedRange.getEnd().getLine()
							|| containingRange.getEnd().getCharacter() >= includedRange.getEnd().getCharacter());
		}

		return false;
	}

	@Override
	public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
		return CompletableFuture.completedFuture(mockRenameEdit);
	}

	@Override
	public CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> prepareRename(
			PrepareRenameParams params) {
		return CompletableFuture.completedFuture(this.mockPrepareRenameResult);
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		if (didOpenCallback != null) {
			didOpenCallback.complete(params);
			didOpenCallback = null;
		}

		if (this.diagnostics != null && !this.diagnostics.isEmpty()) {
			// we're not sure which remote proxy to use, but we know we should only use one
			// per didOpen
			// for proper LS interaction; so a strategy is to use the first one and rotate
			// the others
			// for further executions
			synchronized (this.remoteProxies) {
				// and we synchronize to avoid concurrent read/write on the list
				this.remoteProxies.get(0).publishDiagnostics(
						new PublishDiagnosticsParams(params.getTextDocument().getUri(), this.diagnostics));
				Collections.rotate(this.remoteProxies, 1);
			}
		}
//		if (this.foldingRanges != null && !this.foldingRanges.isEmpty()) {
//			synchronized (this.remoteProxies) {
//				// and we synchronize to avoid concurrent read/write on the list
//				this.remoteProxies.get(0).foldpublishDiagnostics(
//						new PublishDiagnosticsParams(params.getTextDocument().getUri(), this.diagnostics));
//				Collections.rotate(this.remoteProxies, 1);
//		}
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		this.didChangeEvents.add(params);
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		if (didCloseCallback != null) {
			didCloseCallback.complete(params);
			didCloseCallback = null;
		}
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		if (didSaveCallback != null) {
			didSaveCallback.complete(params);
			didSaveCallback = null;
		}
	}

	@Override
	public CompletableFuture<List<TextEdit>> willSaveWaitUntil(WillSaveTextDocumentParams params) {
		if (mockWillSaveWaitUntilTextEdits != null) {
			return CompletableFuture.completedFuture(mockWillSaveWaitUntilTextEdits);
		}
		return null;
	}

	@Override
	public CompletableFuture<List<ColorInformation>> documentColor(DocumentColorParams params) {
		return CompletableFuture.completedFuture(this.mockDocumentColors);
	}

	@Override
	public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> typeDefinition(
			TypeDefinitionParams position) {
		return CompletableFuture.completedFuture(Either.forRight(this.mockTypeDefinitions));
	}

	@Override
	public CompletableFuture<CodeAction> resolveCodeAction(CodeAction unresolved) {
		return CompletableFuture.completedFuture(unresolved);
	}

	public void setMockCompletionList(CompletionList completionList) {
		this.mockCompletionList = completionList;
	}

	public void setDidOpenCallback(CompletableFuture<DidOpenTextDocumentParams> didOpenExpectation) {
		this.didOpenCallback = didOpenExpectation;
	}

	public List<DidChangeTextDocumentParams> getDidChangeEvents() {
		return new ArrayList<>(this.didChangeEvents);
	}

	public void setDidSaveCallback(CompletableFuture<DidSaveTextDocumentParams> didSaveExpectation) {
		this.didSaveCallback = didSaveExpectation;
	}

	public void setDidCloseCallback(CompletableFuture<DidCloseTextDocumentParams> didCloseExpectation) {
		this.didCloseCallback = didCloseExpectation;
	}

	public void setMockHover(Hover hover) {
		this.mockHover = hover;
	}

	public void setPrepareRenameResult(Either<Range, PrepareRenameResult> result) {
		this.mockPrepareRenameResult = result == null ? null : result.map(Either3::forFirst, Either3::forSecond);
	}

	public void setMockCodeLenses(List<CodeLens> codeLenses) {
		this.mockCodeLenses = codeLenses;
	}

	public void setMockDefinitionLocations(List<? extends Location> definitionLocations) {
		this.mockDefinitionLocations = definitionLocations;
	}

	public void setMockReferences(Location... locations) {
		this.mockReferences = locations;
	}

	public void setMockFormattingTextEdits(List<? extends TextEdit> formattingTextEdits) {
		this.mockFormattingTextEdits = formattingTextEdits;
	}

	public void setMockDocumentLinks(List<DocumentLink> documentLinks) {
		this.mockDocumentLinks = documentLinks;
	}

	public void reset() {
		this.mockCompletionList = new CompletionList();
		this.mockDefinitionLocations = Collections.emptyList();
		this.mockTypeDefinitions = Collections.emptyList();
		this.mockHover = null;
		this.mockCodeLenses = null;
		this.mockReferences = null;
		this.remoteProxies.clear();
		this.mockCodeActions = new ArrayList<>();
		this.mockRenameEdit = null;
		this.documentSymbols = Collections.emptyList();
		this.foldingRanges = new ArrayList<>();
		this.codeActionRequests = 0;
	}

	public void setDiagnostics(List<Diagnostic> diagnostics) {
		this.diagnostics = diagnostics;
	}

	public void addRemoteProxy(LanguageClient remoteProxy) {
		this.remoteProxies.add(remoteProxy);
	}

	public void setCodeActions(List<Either<Command, CodeAction>> codeActions) {
		this.mockCodeActions = codeActions;
	}

	public void setSignatureHelp(SignatureHelp signatureHelp) {
		this.mockSignatureHelp = signatureHelp;
	}

	public void setDocumentHighlights(Map<Position, List<? extends DocumentHighlight>> documentHighlights) {
		this.mockDocumentHighlights = documentHighlights;
	}

	public void setLinkedEditingRanges(LinkedEditingRanges linkedEditingRanges) {
		this.mockLinkedEditingRanges = linkedEditingRanges;
	}

	public void setDocumentColors(List<ColorInformation> colors) {
		this.mockDocumentColors = colors;
	}

	public void setRenameEdit(WorkspaceEdit edit) {
		this.mockRenameEdit = edit;
	}

	public void setMockTypeDefinitions(List<? extends LocationLink> mockTypeDefinitions) {
		this.mockTypeDefinitions = mockTypeDefinitions;
	}

	public void setDocumentSymbols(List<DocumentSymbol> symbols) {
		this.documentSymbols = symbols;
	}

	public void setWillSaveWaitUntilCallback(List<TextEdit> textEdits) {
		this.mockWillSaveWaitUntilTextEdits = textEdits;
	}

	public void setSemanticTokens(final SemanticTokens semanticTokens) {
		this.mockSemanticTokens = semanticTokens;
	}

	@Override
	public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
		return CompletableFuture.completedFuture(this.mockSemanticTokens);
	}

	private static final Range DUMMY_RANGE = new Range(new Position(0, 0), new Position(0, 0));

	@Override
	public CompletableFuture<List<TypeHierarchyItem>> prepareTypeHierarchy(TypeHierarchyPrepareParams params) {
		return CompletableFuture.completedFuture(List.of(new TypeHierarchyItem("a", SymbolKind.Class,
				params.getTextDocument().getUri(), DUMMY_RANGE, DUMMY_RANGE, null)));
	}

	@Override
	public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySubtypes(TypeHierarchySubtypesParams params) {
		return CompletableFuture.completedFuture(List.of(
				new TypeHierarchyItem(params.getItem().getName() + "a", SymbolKind.Class,
						params.getItem().getUri() + "/a", DUMMY_RANGE, DUMMY_RANGE, null),
				new TypeHierarchyItem(params.getItem().getName() + "b", SymbolKind.Class,
						params.getItem().getUri() + "/b", DUMMY_RANGE, DUMMY_RANGE, null)));
	}

	@Override
	public CompletableFuture<List<TypeHierarchyItem>> typeHierarchySupertypes(TypeHierarchySupertypesParams params) {
		return CompletableFuture.completedFuture(List.of(
				new TypeHierarchyItem("X" + params.getItem().getName(), SymbolKind.Class,
						params.getItem().getUri() + "/X", DUMMY_RANGE, DUMMY_RANGE, null),
				new TypeHierarchyItem("Y" + params.getItem().getName(), SymbolKind.Class,
						params.getItem().getUri() + "/Y", DUMMY_RANGE, DUMMY_RANGE, null)));
	}

	public void setFoldingRanges(List<FoldingRange> foldingRanges) {
		this.foldingRanges = foldingRanges;
	}

	@Override
	public CompletableFuture<List<FoldingRange>> foldingRange(FoldingRangeRequestParams params) {
		return CompletableFuture.completedFuture(this.foldingRanges);
	}
}
