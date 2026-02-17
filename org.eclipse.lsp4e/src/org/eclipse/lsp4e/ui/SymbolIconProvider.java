/*******************************************************************************
 * Copyright (c) 2024 Advantest GmbH and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Dietrich Travkin (Solunar GmbH) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.lsp4e.operations.symbols.SymbolsUtil;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;


/**
 * Customizable class for creating document symbol icons with overlays depending on the symbol's
 * {@link SymbolKind} and {@link SymbolTag}s. This class is meant to be used with {@link LabelProvider}s.
 */
public class SymbolIconProvider {

	/**
	 * Returns an overlay icon {@link ImageDescriptor} for the given severity.
	 *
	 * @param severity one of <code>IMarker.SEVERITY_ERROR</code> or <code>IMarker.SEVERITY_WARNING</code>
	 * @return image descriptor for a warning or an error, or <code>null</code> in all other cases
	 */
	protected @Nullable ImageDescriptor getOverlayForMarkerSeverity(int severity) {
		return switch(severity) {
			case IMarker.SEVERITY_ERROR   -> LSPImages.getSharedImageDescriptor(ISharedImages.IMG_DEC_FIELD_ERROR);
			case IMarker.SEVERITY_WARNING -> LSPImages.getSharedImageDescriptor(ISharedImages.IMG_DEC_FIELD_WARNING);
			default -> null;
		};
	}

	/**
	 * Returns an underlay icon {@link ImageDescriptor} if the given argument is true, <code>null</code> otherwise.
	 *
	 * @param deprecated if a symbol is deprecated
	 * @return a deprecation underlay icon or <code>null</code>
	 */
	protected @Nullable ImageDescriptor getUnderlayForDeprecation(boolean deprecated) {
		if (!deprecated) {
			return null;
		}
		return LSPImages.imageDescriptorOverlayFromSymbolTag(SymbolTag.Deprecated);
	}

	private static final List<SymbolTag> VISIBILITY_PRECEDENCE = List.of(
			SymbolTag.Public, SymbolTag.Protected, SymbolTag.Package,
			SymbolTag.Internal, SymbolTag.File, SymbolTag.Private);

	/**
	 * Returns a list of visibility {@link SymbolTag}s with decreasing precedence.
	 * May be overridden by subclasses to change the visibility overlay icons shown.
	 *
	 * @return a list of visibility {@link SymbolTag}s
	 */
	protected List<SymbolTag> getVisibilityPrecedence() {
		return VISIBILITY_PRECEDENCE;
	}

	// In order to keep the number of overlay icons rather small in the UI, we do not show the following symbol tags:
	// SymbolTag.Nullable, SymbolTag.NonNull, SymbolTag.Declaration, SymbolTag.Definition
	private static final List<SymbolTag> ADDITIONAL_TAGS_PRECEDENCE = List.of(
			SymbolTag.Static, SymbolTag.Final, SymbolTag.Abstract,
			SymbolTag.Overrides, SymbolTag.Implements, SymbolTag.Virtual, SymbolTag.Sealed,
			SymbolTag.Synchronized, SymbolTag.Transient, SymbolTag.Volatile,
			SymbolTag.ReadOnly);

	/**
	 * Returns a list of {@link SymbolTag}s excluding visibility and deprecation tags with decreasing precedence.
	 * May be overridden by subclasses to change the overlay icons shown in addition to visibility and deprecation.
	 * The default implementation also excludes the following tags:
	 * <code>SymbolTag.Nullable</code>, <code>SymbolTag.NonNull</code>,
	 * <code>SymbolTag.Declaration</code>, <code>SymbolTag.Definition</code>
	 *
	 * @return a list of {@link SymbolTag}s without visibility and deprecation tags
	 */
	protected List<SymbolTag> getAdditionalTagsPrecedence() {
		return ADDITIONAL_TAGS_PRECEDENCE;
	}

	/**
	 * Returns the visibility {@link SymbolTag} to be shown in the UI. All other {@link SymbolTag}s will be ignored.
	 *
	 * @param symbolTags a document symbol's {@link SymbolTag}s
	 * @return the highest precedence visibility {@link SymbolTag} if available
	 *
	 * @see #getVisibilityPrecedence()
	 */
	protected final Optional<SymbolTag> getHighestPrecedenceVisibilitySymbolTag(List<SymbolTag> symbolTags) {
		final var precedenceList = getVisibilityPrecedence();
		return symbolTags.stream()
				.filter(precedenceList::contains)
				.min(Comparator.comparing(precedenceList::indexOf));
	}

	/**
	 * Returns a list of a document symbol's {@link SymbolTag}s excluding visibility and deprecation tags
	 * sorted according to their precedence. Symbol tags with higher precedence are more likely to be shown in the UI.
	 *
	 * @param symbolTags a document symbol's {@link SymbolTag}s
	 * @return a sorted list of {@link SymbolTag}s excluding visibility and deprecation tags
	 *
	 * @see #getAdditionalTagsPrecedence()
	 */
	protected final List<SymbolTag> getAdditionalSymbolTagsSorted(List<SymbolTag> symbolTags) {
		final var precedenceList = getAdditionalTagsPrecedence();
		return symbolTags.stream()
				.filter(precedenceList::contains)
				.sorted(Comparator.comparing(precedenceList::indexOf))
				.toList();
	}

	private @Nullable ImageDescriptor getOverlayForVisibility(List<SymbolTag> symbolTags) {
		Optional<SymbolTag> visibilityTag = getHighestPrecedenceVisibilitySymbolTag(symbolTags);

		if (visibilityTag.isEmpty()) {
			return null;
		}

		return LSPImages.imageDescriptorOverlayFromSymbolTag(visibilityTag.get());
	}

	/**
	 * Determines an image key (identifier) for the given arguments that can be used with the image registry.
	 * Instead of just determining the icon for a document symbol, the given optional visibility {@link SymbolTag}s
	 * are considered, i.e. fields and methods (incl. constructors) get different symbol icons depending on their visibility.
	 *
	 * @param kind a document symbol's kind, e.g. field, method, constructor, class, property
	 * @param symbolTags a document symbol's {@link SymbolTag}s, only visibility tags are considered
	 * @return an image's key (identifier) for the use with the image registry
	 *
	 * @see LSPImages#getImage(String)
	 * @see LSPImages#getImageDescriptor(String)
	 */
	protected String getImageKeyFromSymbolKindWithVisibility(SymbolKind kind, List<SymbolTag> symbolTags) {

		Optional<SymbolTag> visibilityTag = getHighestPrecedenceVisibilitySymbolTag(symbolTags);

		if (visibilityTag.isEmpty()) {
			return LSPImages.imageKeyFromSymbolKind(kind);
		}

		SymbolTag visibility = visibilityTag.get();

		if (kind == SymbolKind.Field) {
			return switch (visibility) {
				case Private -> LSPImages.IMG_FIELD_VIS_PRIVATE;
				case Package -> LSPImages.IMG_FIELD_VIS_PACKAGE;
				case Protected -> LSPImages.IMG_FIELD_VIS_PROTECTED;
				case Public -> LSPImages.IMG_FIELD_VIS_PUBLIC;
				case Internal -> LSPImages.IMG_FIELD_VIS_INTERNAL;
				case File -> LSPImages.IMG_FIELD_VIS_FILE;
				default -> LSPImages.IMG_FIELD;
			};
		} else if (kind == SymbolKind.Method || kind == SymbolKind.Constructor) {
			return switch (visibility) {
				case Private -> LSPImages.IMG_METHOD_VIS_PRIVATE;
				case Package -> LSPImages.IMG_METHOD_VIS_PACKAGE;
				case Protected -> LSPImages.IMG_METHOD_VIS_PROTECTED;
				case Public -> LSPImages.IMG_METHOD_VIS_PUBLIC;
				case Internal -> LSPImages.IMG_METHOD_VIS_INTERNAL;
				case File -> LSPImages.IMG_METHOD_VIS_FILE;
				default -> LSPImages.IMG_METHOD;
			};
		}

		return LSPImages.imageKeyFromSymbolKind(kind);
	}

	/**
	 * Returns an image for the given arguments.
	 *
	 * @param symbolKind the kind of symbol
	 * @param symbolTags the symbol tags
	 * @return a new or cached image for the given symbol kind with overlay icons computed for the given arguments.
	 *
	 * @see #getImageFor(SymbolKind, List, int)
	 */
	public @Nullable Image getImageFor(@Nullable SymbolKind symbolKind, @Nullable List<SymbolTag> symbolTags) {
		return getImageFor(symbolKind, symbolTags, -1);
	}

	/**
	 * Returns an image for the given arguments.
	 * Uses caching for all combinations of a symbol kind and a set of overlays.
	 * Deprecation is shown if the <em>deprecated</em> parameter is <code>true</code>
	 * or {@link SymbolTag#Deprecated} is in the set of symbol tags.
	 *
	 * @param symbolKind the kind of symbol
	 * @param symbolTags the symbol tags
	 * @param severity one of -1, {@link IMarker#SEVERITY_WARNING}, and {@link IMarker#SEVERITY_ERROR}. -1 indicates no overlay icon.
	 * @return a new or cached image for the given symbol kind with overlay icons computed for the given arguments.
	 *
	 * @see #getImageFor(SymbolKind, List)
	 */
	public @Nullable Image getImageFor(final @Nullable SymbolKind symbolKind,
			final @Nullable List<SymbolTag> symbolTags, int severity) {

		if (symbolKind == null) {
			return LSPImages.imageFromSymbolKind(symbolKind);
		}

		final List<SymbolTag> finalSymbolTags = symbolTags != null ? symbolTags : Collections.emptyList();

		String baseImageKey = getImageKeyFromSymbolKindWithVisibility(symbolKind, finalSymbolTags);

		ImageDescriptor severityImageDescriptor = getOverlayForMarkerSeverity(severity);
		ImageDescriptor deprecatedImageDescriptor = getUnderlayForDeprecation(SymbolsUtil.isDeprecated(finalSymbolTags));

		List<SymbolTag> additionalTags = getAdditionalSymbolTagsSorted(finalSymbolTags);

		ImageDescriptor topLeftOverlayDescriptor = null;
		ImageDescriptor topRightOverlayDescriptor = null;
		ImageDescriptor bottomLeftOverlayDescriptor = severityImageDescriptor;
		ImageDescriptor bottomRightOverlayDescriptor = null;
		ImageDescriptor underlayDescriptor = deprecatedImageDescriptor;

		// special case for a constructor with visibility tag => we need to add a "C" overlay to show it's a constructor
		if (SymbolKind.Constructor == symbolKind
				&& !baseImageKey.equals(LSPImages.imageKeyFromSymbolKind(symbolKind))) {
			topRightOverlayDescriptor = LSPImages.getImageDescriptor(LSPImages.IMG_OVR_CONSTRUCTOR);
		}

		if (!additionalTags.isEmpty()) {
			topLeftOverlayDescriptor = LSPImages.imageDescriptorOverlayFromSymbolTag(additionalTags.get(0));

			if (additionalTags.size() > 1) {
				if (SymbolKind.Constructor == symbolKind) {
					// a constructor has an overlay in the top right corner,
					// in this case we place the second symbol tag's overlay icon at the lower right corner
					bottomRightOverlayDescriptor = LSPImages.imageDescriptorOverlayFromSymbolTag(additionalTags.get(1));
				} else {
					topRightOverlayDescriptor = LSPImages.imageDescriptorOverlayFromSymbolTag(additionalTags.get(1));
				}
			}
		}

		if (SymbolKind.Field == symbolKind || SymbolKind.Method == symbolKind) {
			// In these cases the visibility is already expressed by the symbol icon, so we can display one more symbol tag
			if (additionalTags.size() > 2) {
				bottomRightOverlayDescriptor = LSPImages.imageDescriptorOverlayFromSymbolTag(additionalTags.get(2));
			}
		} else if (SymbolKind.Constructor != symbolKind) {
			// We place the visibility overlay icon on the lower right corner, similar to JDT.
			// The top left and top right corners remain for additional symbol tags (besides visibility, severity, deprecation)
			// In case of constructors we already have a "C" for "constructor" in the upper right corner
			// and have use the lower right corner for another additional symbol tag.
			bottomRightOverlayDescriptor = getOverlayForVisibility(finalSymbolTags);
		}

		return LSPImages.getImageWithOverlays(baseImageKey, topLeftOverlayDescriptor, topRightOverlayDescriptor,
				bottomLeftOverlayDescriptor, bottomRightOverlayDescriptor, underlayDescriptor);
	}

}
