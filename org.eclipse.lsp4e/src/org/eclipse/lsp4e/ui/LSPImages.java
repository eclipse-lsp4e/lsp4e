/*******************************************************************************
 * Copyright (c) 2016-2023 Rogue Wave Software Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Michał Niewrzał (Rogue Wave Software Inc.) - initial implementation
 *  Dietrich Travkin (Solunar GmbH) - add overlay images computation, dispose cached images
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.operations.symbols.SymbolsUtil;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.SymbolTag;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Bundle;

public final class LSPImages {

	private LSPImages() {
		// this class shouldn't be instantiated
	}

	private static @Nullable ImageRegistry imageRegistry;

	private static final Map<java.awt.Color, Image> colorToImageCache = new HashMap<>();

	/**
	 * Cache for symbol images with various overlays and / or an underlay.
	 *
	 * First key: the element's kind (e.g. class or method);
	 * Second key: hash value calculated for a set of overlay image descriptors,
	 *             see {@link #getImageWithOverlays(SymbolKind, ImageDescriptor[])};
	 * Value: the base image with overlays and an optional underlay combined in one image.
	 */
	private static final Map<SymbolKind, Map<Integer, Image>> overlayImagesCache = new HashMap<>();

	private static final String ICONS_PATH = "$nl$/icons/full/"; //$NON-NLS-1$
	private static final String OBJECT = ICONS_PATH + "obj16/"; // basic colors - size 16x16 //$NON-NLS-1$
	private static final String ACTION = ICONS_PATH + "elcl16/"; // basic colors - size 16x16 //$NON-NLS-1$
	private static final String OVERLAY = ICONS_PATH + "ovr16/"; // basic colors - size 7x8 and 14x16 //$NON-NLS-1$

	private static final Image EMPTY_IMAGE = new Image(UI.getDisplay(), 16, 16);

	public static final String IMG_MODULE = "IMG_MODULE"; //$NON-NLS-1$
	public static final String IMG_NAMESPACE = "IMG_NAMESPACE"; //$NON-NLS-1$
	public static final String IMG_PACKAGE = "IMG_PACKAGE"; //$NON-NLS-1$
	public static final String IMG_CLASS = "IMG_CLASS"; //$NON-NLS-1$
	public static final String IMG_TYPE_PARAMETER = "IMG_TYPE_PARAMETER"; //$NON-NLS-1$
	public static final String IMG_METHOD = "IMG_METOHD"; //$NON-NLS-1$
	public static final String IMG_PROPERTY = "IMG_PROPERTY"; //$NON-NLS-1$
	public static final String IMG_FIELD = "IMG_FIELD"; //$NON-NLS-1$
	public static final String IMG_CONSTRUCTOR = "IMG_CONSTRUCTOR"; //$NON-NLS-1$
	public static final String IMG_ENUM = "IMG_ENUM"; //$NON-NLS-1$
	public static final String IMG_ENUM_MEMBER = "IMG_ENUM_MEMBER"; //$NON-NLS-1$
	public static final String IMG_STRUCT = "IMG_STRUCT"; //$NON-NLS-1$
	public static final String IMG_INTERACE = "IMG_INTERFACE"; //$NON-NLS-1$
	public static final String IMG_FUNCTION = "IMG_FUNCTION"; //$NON-NLS-1$
	public static final String IMG_VARIABLE = "IMG_VARIABLE"; //$NON-NLS-1$
	public static final String IMG_CONSTANT = "IMG_CONSTANT"; //$NON-NLS-1$
	public static final String IMG_OBJECT = "IMG_OBJECT"; //$NON-NLS-1$
	public static final String IMG_TEXT = "IMG_TEXT"; //$NON-NLS-1$
	public static final String IMG_STRING = IMG_TEXT;
	public static final String IMG_NUMBER = "IMG_NUMBER"; //$NON-NLS-1$
	public static final String IMG_BOOLEAN = "IMG_BOOLEAN"; //$NON-NLS-1$
	public static final String IMG_ARRAY = "IMG_ARRAY"; //$NON-NLS-1$
	public static final String IMG_NULL = "IMG_NULL"; //$NON-NLS-1$
	public static final String IMG_UNIT = "IMG_UNIT"; //$NON-NLS-1$
	public static final String IMG_VALUE = "IMG_VALUE"; //$NON-NLS-1$
	public static final String IMG_KEYWORD = "IMG_KEYWORD"; //$NON-NLS-1$
	public static final String IMG_SNIPPET = "IMG_SNIPPET"; //$NON-NLS-1$
	public static final String IMG_COLOR = "IMG_COLOR"; //$NON-NLS-1$
	public static final String IMG_REFERENCE = "IMG_REFERENCE"; //$NON-NLS-1$
	public static final String IMG_TERMINATE_CO = "IMG_TERMINATE_CO"; //$NON-NLS-1$

	public static final String IMG_SUPERTYPE = "IMG_SUPERTYPE"; //$NON-NLS-1$
	public static final String IMG_SUBTYPE = "IMG_SUBTYPE"; //$NON-NLS-1$

	public static final String IMG_OVR_CONSTRUCTOR = "IMG_OVR_CONSTRUCTOR"; //$NON-NLS-1$
	public static final String IMG_OVR_DEPRECATED = "IMG_OVR_DEPRECATED"; //$NON-NLS-1$
	public static final String IMG_OVR_PRIVATE = "IMG_OVR_PRIVATE"; //$NON-NLS-1$
	public static final String IMG_OVR_PACKAGE = "IMG_OVR_PACKAGE"; //$NON-NLS-1$
	public static final String IMG_OVR_PROTECTED = "IMG_OVR_PROTECTED"; //$NON-NLS-1$
	public static final String IMG_OVR_PUBLIC = "IMG_OVR_PUBLIC"; //$NON-NLS-1$
	public static final String IMG_OVR_INTERNAL = "IMG_OVR_INTERNAL"; //$NON-NLS-1$
	public static final String IMG_OVR_FILE_VIS = "IMG_OVR_FILE_VIS"; //$NON-NLS-1$
	public static final String IMG_OVR_ABSTRACT = "IMG_OVR_ABSTRACT"; //$NON-NLS-1$
	public static final String IMG_OVR_VIRTUAL = "IMG_OVR_VIRTUAL"; //$NON-NLS-1$
	public static final String IMG_OVR_FINAL = "IMG_OVR_FINAL"; //$NON-NLS-1$
	public static final String IMG_OVR_SEALED = "IMG_OVR_SEALED"; //$NON-NLS-1$
	public static final String IMG_OVR_STATIC = "IMG_OVR_STATIC"; //$NON-NLS-1$
	public static final String IMG_OVR_SYNC = "IMG_OVR_SYNC"; //$NON-NLS-1$
	public static final String IMG_OVR_TRANSIENT = "IMG_OVR_TRANSIENT"; //$NON-NLS-1$
	public static final String IMG_OVR_VOLATILE = "IMG_OVR_VOLATILE"; //$NON-NLS-1$
	public static final String IMG_OVR_NULLABLE = "IMG_OVR_NULLABLE"; //$NON-NLS-1$
	public static final String IMG_OVR_NON_NULL = "IMG_OVR_NON_NULL"; //$NON-NLS-1$
	public static final String IMG_OVR_DECLARATION = "IMG_OVR_DECLARATION"; //$NON-NLS-1$
	public static final String IMG_OVR_DEFINITION = "IMG_OVR_DEFINITION"; //$NON-NLS-1$
	public static final String IMG_OVR_READ_ONLY = "IMG_OVR_READ_ONLY"; //$NON-NLS-1$
	public static final String IMG_OVR_IMPLEMENT = "IMG_OVR_IMPLEMENT"; //$NON-NLS-1$
	public static final String IMG_OVR_OVERRIDE = "IMG_OVR_OVERRIDE"; //$NON-NLS-1$


	public static void initalize(ImageRegistry registry) {
		imageRegistry = registry;

		declareRegistryImage(IMG_MODULE, OBJECT + "module.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_NAMESPACE, OBJECT + "namespace.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_PACKAGE, OBJECT + "package.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_CLASS, OBJECT + "class.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_TYPE_PARAMETER, OBJECT + "type_parameter.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_METHOD, OBJECT + "method.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_PROPERTY, OBJECT + "property.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_FIELD, OBJECT + "field.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_CONSTRUCTOR, OBJECT + "constructor.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_ENUM, OBJECT + "enum.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_ENUM_MEMBER, OBJECT + "enum_member.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_STRUCT, OBJECT + "struct.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_INTERACE, OBJECT + "interface.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_FUNCTION, OBJECT + "function.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_VARIABLE, OBJECT + "variable.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_CONSTANT, OBJECT + "constant.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OBJECT, OBJECT + "object.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_STRING, OBJECT + "string.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_NUMBER, OBJECT + "number.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_BOOLEAN, OBJECT + "boolean.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_ARRAY, OBJECT + "array.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_NULL, OBJECT + "null.svg"); //$NON-NLS-1$

		declareRegistryImage(IMG_TEXT, OBJECT + "text.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_UNIT, OBJECT + "unit.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_VALUE, OBJECT + "value.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_KEYWORD, OBJECT + "keyword.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_SNIPPET, OBJECT + "snippet.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_COLOR, OBJECT + "color.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_REFERENCE, OBJECT + "reference.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_SUPERTYPE, ACTION + "super_co.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_SUBTYPE, ACTION + "sub_co.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_TERMINATE_CO, OBJECT + "terminate_co.png"); //$NON-NLS-1$

		declareRegistryImage(IMG_OVR_CONSTRUCTOR, OVERLAY + "constr_ovr.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_DEPRECATED, OVERLAY + "deprecated.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_PRIVATE, OVERLAY + "private_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_PACKAGE, OVERLAY + "package_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_PROTECTED, OVERLAY + "protected_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_PUBLIC, OVERLAY + "public_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_INTERNAL, OVERLAY + "internal_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_FILE_VIS, OVERLAY + "file_visable_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_ABSTRACT, OVERLAY + "abstract_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_VIRTUAL, OVERLAY + "virtual_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_FINAL, OVERLAY + "final_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_SEALED, OVERLAY + "sealed_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_STATIC, OVERLAY + "static_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_SYNC, OVERLAY + "synch_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_TRANSIENT, OVERLAY + "transient_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_VOLATILE, OVERLAY + "volatile_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_NULLABLE, OVERLAY + "nullable_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_NON_NULL, OVERLAY + "non_null_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_DECLARATION, OVERLAY + "declaration_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_DEFINITION, OVERLAY + "definition_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_READ_ONLY, OVERLAY + "read_only_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_IMPLEMENT, OVERLAY + "implement_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_OVERRIDE, OVERLAY + "override_co.svg"); //$NON-NLS-1$
	}

	private static void declareRegistryImage(String key, String path) {
		ImageDescriptor desc = ImageDescriptor.getMissingImageDescriptor();
		Bundle bundle = Platform.getBundle(LanguageServerPlugin.PLUGIN_ID);
		URL url = null;
		if (bundle != null) {
			url = FileLocator.find(bundle, new Path(path), null);
			if (url != null) {
				desc = ImageDescriptor.createFromURL(url);
			}
		}
		getImageRegistry().put(key, desc);
	}

	/**
	 * Returns the <code>Image</code> identified by the given key, or <code>null</code> if it does not exist.
	 */
	public static @Nullable Image getImage(String key) {
		return getImageRegistry().get(key);
	}

	/**
	 * Returns the <code>ImageDescriptor</code> identified by the given key, or <code>null</code> if it does not exist.
	 */
	public static @Nullable ImageDescriptor getImageDescriptor(String key) {
		return getImageRegistry().getDescriptor(key);
	}

	public static ImageRegistry getImageRegistry() {
		ImageRegistry imageRegistry = LSPImages.imageRegistry;
		if (imageRegistry == null) {
			imageRegistry = LSPImages.imageRegistry = LanguageServerPlugin.getDefault().getImageRegistry();
		}
		return imageRegistry;
	}

	/**
	 * @param imageId See static IMG_* fields of {@link ISharedImages}
	 * @return the workbench's shared image for the , or null if not found
	 */
	public static @Nullable Image getSharedImage(@Nullable String imageId) {
		if(imageId == null) {
			return null;
		}
		return PlatformUI.getWorkbench().getSharedImages().getImage(imageId);
	}

	/**
	 * @param imageId See static IMG_* fields of {@link ISharedImages}
	 * @return the workbench's shared image descriptor for the workbench, or null if not found
	 */
	public static @Nullable ImageDescriptor getSharedImageDescriptor(@Nullable String imageId) {
		if(imageId == null) {
			return null;
		}
		return PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(imageId);
	}

	public static @Nullable Image imageFromSymbolKind(@Nullable SymbolKind kind) {
		return switch (kind) {
		case Array -> getImage(IMG_ARRAY);
		case Boolean -> getImage(IMG_BOOLEAN);
		case Class -> getImage(IMG_CLASS);
		case Constant -> getImage(IMG_CONSTANT);
		case Constructor -> getImage(IMG_CONSTRUCTOR);
		case Enum -> getImage(IMG_ENUM);
		case EnumMember -> getImage(IMG_ENUM_MEMBER);
		case Struct -> getImage(IMG_STRUCT);
		case Field -> getImage(IMG_FIELD);
		case File -> getSharedImage(ISharedImages.IMG_OBJ_FILE);
		case Function -> getImage(IMG_FUNCTION);
		case Interface -> getImage(IMG_INTERACE);
		case Method -> getImage(IMG_METHOD);
		case Module -> getImage(IMG_MODULE);
		case Namespace -> getImage(IMG_NAMESPACE);
		case Number -> getImage(IMG_NUMBER);
		case Object -> getImage(IMG_OBJECT);
		case Package -> getImage(IMG_PACKAGE);
		case Property -> getImage(IMG_PROPERTY);
		case String -> getImage(IMG_STRING);
		case TypeParameter -> getImage(IMG_TYPE_PARAMETER);
		case Variable -> getImage(IMG_VARIABLE);
		case Null -> getImage(IMG_NULL);
		case Event, Key, Operator -> EMPTY_IMAGE;
		case null -> EMPTY_IMAGE;
		};
	}

	public static @Nullable Image imageFromCompletionItem(CompletionItem completionItem) {
		return switch (completionItem.getKind()) {
		case Text -> getImage(IMG_TEXT);
		case Method -> getImage(IMG_METHOD);
		case Function -> getImage(IMG_FUNCTION);
		case Constructor -> getImage(IMG_CONSTRUCTOR);
		case Field -> getImage(IMG_FIELD);
		case Variable -> getImage(IMG_VARIABLE);
		case Class -> getImage(IMG_CLASS);
		case Interface -> getImage(IMG_INTERACE);
		case Module -> getImage(IMG_MODULE);
		case Property -> getImage(IMG_PROPERTY);
		case Unit -> getImage(IMG_UNIT);
		case Value -> getImage(IMG_VALUE);
		case Enum -> getImage(IMG_ENUM);
		case EnumMember -> getImage(IMG_ENUM_MEMBER);
		case Struct -> getImage(IMG_STRUCT);
		case Keyword -> getImage(IMG_KEYWORD);
		case Snippet -> getImage(IMG_SNIPPET);
		case Color -> getImageForColor(completionItem);
		case File -> getSharedImage(ISharedImages.IMG_OBJ_FILE);
		case Folder -> getSharedImage(ISharedImages.IMG_OBJ_FOLDER);
		case Reference -> getImage(IMG_REFERENCE);
		case Constant -> getImage(IMG_CONSTANT);
		case TypeParameter -> getImage(IMG_TYPE_PARAMETER);
		case Event, Operator -> null;
		case null -> null;
		};
	}

	public static @Nullable Image imageOverlayFromSymbolTag(SymbolTag symbolTag) {
		return switch (symbolTag) {
		case Deprecated -> getImage(IMG_OVR_DEPRECATED);
		case Private -> getImage(IMG_OVR_PRIVATE);
		case Package -> getImage(IMG_OVR_PACKAGE);
		case Protected -> getImage(IMG_OVR_PROTECTED);
		case Public -> getImage(IMG_OVR_PUBLIC);
		case Internal -> getImage(IMG_OVR_INTERNAL);
		case File -> getImage(IMG_OVR_FILE_VIS);
		case Static -> getImage(IMG_OVR_STATIC);
		case Abstract -> getImage(IMG_OVR_ABSTRACT);
		case Final -> getImage(IMG_OVR_FINAL);
		case Sealed -> getImage(IMG_OVR_SEALED);
		case Transient -> getImage(IMG_OVR_TRANSIENT);
		case Volatile -> getImage(IMG_OVR_VOLATILE);
		case Synchronized -> getImage(IMG_OVR_SYNC);
		case Virtual -> getImage(IMG_OVR_VIRTUAL);
		case Nullable -> getImage(IMG_OVR_NULLABLE);
		case NonNull -> getImage(IMG_OVR_NON_NULL);
		case Declaration -> getImage(IMG_OVR_DECLARATION);
		case Definition -> getImage(IMG_OVR_DEFINITION);
		case ReadOnly -> getImage(IMG_OVR_READ_ONLY);
		};
	}

	public static @Nullable ImageDescriptor imageDescriptorOverlayFromSymbolTag(SymbolTag symbolTag) {
		return switch (symbolTag) {
		case Deprecated -> getImageDescriptor(IMG_OVR_DEPRECATED);
		case Private -> getImageDescriptor(IMG_OVR_PRIVATE);
		case Package -> getImageDescriptor(IMG_OVR_PACKAGE);
		case Protected -> getImageDescriptor(IMG_OVR_PROTECTED);
		case Public -> getImageDescriptor(IMG_OVR_PUBLIC);
		case Internal -> getImageDescriptor(IMG_OVR_INTERNAL);
		case File -> getImageDescriptor(IMG_OVR_FILE_VIS);
		case Static -> getImageDescriptor(IMG_OVR_STATIC);
		case Abstract -> getImageDescriptor(IMG_OVR_ABSTRACT);
		case Final -> getImageDescriptor(IMG_OVR_FINAL);
		case Sealed -> getImageDescriptor(IMG_OVR_SEALED);
		case Transient -> getImageDescriptor(IMG_OVR_TRANSIENT);
		case Volatile -> getImageDescriptor(IMG_OVR_VOLATILE);
		case Synchronized -> getImageDescriptor(IMG_OVR_SYNC);
		case Virtual -> getImageDescriptor(IMG_OVR_VIRTUAL);
		case Nullable -> getImageDescriptor(IMG_OVR_NULLABLE);
		case NonNull -> getImageDescriptor(IMG_OVR_NON_NULL);
		case Declaration -> getImageDescriptor(IMG_OVR_DECLARATION);
		case Definition -> getImageDescriptor(IMG_OVR_DEFINITION);
		case ReadOnly -> getImageDescriptor(IMG_OVR_READ_ONLY);
		};
	}

	private static @Nullable Image getImageForColor(CompletionItem completionItem) {
		String hexValue = null;

		// TODO most probably can be extended for more cases
		String docString = LSPEclipseUtils.getDocString(completionItem.getDocumentation());
		if (docString != null && docString.startsWith("#")) { //$NON-NLS-1$
			hexValue = docString;
		} else if (completionItem.getLabel().startsWith("#")) { //$NON-NLS-1$
			hexValue = completionItem.getLabel();
		}
		if (hexValue == null) {
			return null;
		}

		java.awt.Color decodedColor = null;
		try {
			decodedColor = java.awt.Color.decode(hexValue);
		} catch (NumberFormatException e) {
			LanguageServerPlugin.logError(e);
			return null;
		}

		return colorToImageCache.computeIfAbsent(decodedColor, key -> {
			// TODO most probably some scaling should be done for HIDPI
			final var image = new Image(Display.getDefault(), 16, 16);
			final var gc = new GC(image);
			final var color = new Color(Display.getDefault(), key.getRed(), key.getGreen(),
					key.getBlue(), key.getAlpha());
			gc.setBackground(color);
			gc.fillRectangle(0, 0, 16, 16);
			gc.dispose();
			return image;
		});
	}

	private static final List<SymbolTag> VISIBILITY_PRECEDENCE = Arrays.asList(new SymbolTag[] {
			SymbolTag.Public, SymbolTag.Protected, SymbolTag.Package, SymbolTag.Private,
			SymbolTag.Internal, SymbolTag.File });

	// precedence for remaining symbol tags (without visibility tags and deprecation tag)
	private static final List<SymbolTag> ADDITIONAL_TAGS_PRECEDENCE = Arrays.asList(new SymbolTag[] {
			SymbolTag.Static, SymbolTag.Abstract, SymbolTag.Virtual, SymbolTag.Final, SymbolTag.Sealed,
			SymbolTag.Synchronized, SymbolTag.Transient, SymbolTag.Volatile,
			SymbolTag.Nullable, SymbolTag.NonNull, SymbolTag.ReadOnly,
			SymbolTag.Declaration, SymbolTag.Definition });

	private static class VisibilitySymbolTagComparator implements Comparator<SymbolTag> {
		@Override
		public int compare(SymbolTag tag1, SymbolTag tag2) {
			return VISIBILITY_PRECEDENCE.indexOf(tag1) - VISIBILITY_PRECEDENCE.indexOf(tag2);
		}
	}

	private static class AdditionalSymbolTagComparator implements Comparator<SymbolTag> {
		@Override
		public int compare(SymbolTag tag1, SymbolTag tag2) {
			return ADDITIONAL_TAGS_PRECEDENCE.indexOf(tag1) - ADDITIONAL_TAGS_PRECEDENCE.indexOf(tag2);
		}
	}

	private static List<SymbolTag> getVisibilitySymbolTagsSorted(List<SymbolTag> symbolTags) {
		return symbolTags.stream()
				.filter(tag -> VISIBILITY_PRECEDENCE.contains(tag))
				.sorted(new VisibilitySymbolTagComparator())
				.collect(Collectors.toList());
	}

	private static List<SymbolTag> getAdditionalSymbolTagsSorted(List<SymbolTag> symbolTags) {
		return symbolTags.stream()
				.filter(tag -> ADDITIONAL_TAGS_PRECEDENCE.contains(tag))
				.sorted(new AdditionalSymbolTagComparator())
				.collect(Collectors.toList());
	}

	private static @Nullable ImageDescriptor getOverlayForVisibility(List<SymbolTag> symbolTags) {
		List<SymbolTag> visibilityTags = getVisibilitySymbolTagsSorted(symbolTags);

		if (visibilityTags.isEmpty()) {
			return null;
		}

		SymbolTag highestPrioVisibilityTag = visibilityTags.get(0);
		return LSPImages.imageDescriptorOverlayFromSymbolTag(highestPrioVisibilityTag);
	}

	private static @Nullable ImageDescriptor getOverlayForMarkerSeverity(int severity) {
		if (severity != IMarker.SEVERITY_WARNING && severity != IMarker.SEVERITY_ERROR) {
			return null;
		}

		String overlayId = null;
		if (severity == IMarker.SEVERITY_ERROR) {
			overlayId = ISharedImages.IMG_DEC_FIELD_ERROR;
		} else if (severity == IMarker.SEVERITY_WARNING) {
			overlayId = ISharedImages.IMG_DEC_FIELD_WARNING;
		}

		if (overlayId != null) {
			return LSPImages.getSharedImageDescriptor(overlayId);
		}
		return null;
	}

	private static @Nullable ImageDescriptor getUnderlayForDeprecation(boolean deprecated) {
		if (!deprecated) {
			return null;
		}
		return LSPImages.imageDescriptorOverlayFromSymbolTag(SymbolTag.Deprecated);
	}

	private static @Nullable Image getImageWithOverlays(SymbolKind symbolKind, ImageDescriptor @Nullable[] overlays) {
		Image baseImage = LSPImages.imageFromSymbolKind(symbolKind);

		if (baseImage == null) {
			return null;
		}

		@SuppressWarnings("null")
		long numOverlays = Arrays.stream(overlays)
				// Despite the IDE's warning, this null check were not necessary, this check is needed,
				// since the array entries could be null.
				.filter(e -> e != null).count();
		if (numOverlays == 0) {
			return baseImage;
		}

		int hashCode = Arrays.hashCode(overlays);

		Map<Integer, Image> overlayImagesForSymbolKind = overlayImagesCache.computeIfAbsent(symbolKind,
				kind -> new HashMap<>());

		return overlayImagesForSymbolKind.computeIfAbsent(hashCode,
				hash -> new DecorationOverlayIcon(baseImage, overlays).createImage());
	}

	/**
	 * Returns an image for the given arguments.
	 *
	 * @param symbolKind the kind of symbol
	 * @param symbolTags the symbol tags
	 * @return a new or cached image for the given symbol kind with overlay icons computed for the given arguments.
	 *
	 * @see #getImageFor(SymbolKind, List, boolean, int)
	 */
	public static @Nullable Image getImageFor(@Nullable SymbolKind symbolKind, @Nullable List<SymbolTag> symbolTags) {
		return getImageFor(symbolKind, symbolTags, false, -1);
	}

	/**
	 * Returns an image for the given arguments.
	 * Uses caching for all combinations of a symbol kind and a set of overlays.
	 * Deprecation is shown if the <em>deprecated</em> parameter is <code>true</code>
	 * or {@link SymbolTag#Deprecated} is in the set of symbol tags.
	 *
	 * @param symbolKind the kind of symbol
	 * @param symbolTags the symbol tags
	 * @param deprecated whether to add a deprecation overlay icon even if there is no {@link SymbolTag#Deprecated} in the tags.
	 * @param severity one of -1, {@link IMarker#SEVERITY_WARNING}, and {@link IMarker#SEVERITY_ERROR}. -1 indicates no overlay icon.
	 * @return a new or cached image for the given symbol kind with overlay icons computed for the given arguments.
	 */
	public static @Nullable Image getImageFor(@Nullable SymbolKind symbolKind, @Nullable List<SymbolTag> symbolTags,
			boolean deprecated, int severity) {

		if (symbolKind == null) {
			return EMPTY_IMAGE;
		}

		if (symbolTags == null) {
			symbolTags = Collections.emptyList();
		}

		ImageDescriptor severityImageDescriptor = getOverlayForMarkerSeverity(severity);
		ImageDescriptor visibilityImageDescriptor = getOverlayForVisibility(symbolTags);
		ImageDescriptor deprecatedImageDescriptor = getUnderlayForDeprecation(deprecated || SymbolsUtil.isDeprecated(symbolTags));

		List<SymbolTag> additionalTags = getAdditionalSymbolTagsSorted(symbolTags);
		ImageDescriptor topRightOverlayDescriptor = null;
		ImageDescriptor bottomRightOverlayDescriptor = null;

		if (!additionalTags.isEmpty()) {
			topRightOverlayDescriptor = LSPImages.imageDescriptorOverlayFromSymbolTag(additionalTags.get(0));

			if (SymbolKind.Constructor.equals(symbolKind)) {
				// constructor base image has a built-in overlay in the top right corner, use bottom right instead
				bottomRightOverlayDescriptor = topRightOverlayDescriptor;
				topRightOverlayDescriptor = null;
			} else if (additionalTags.size() > 1) {
				bottomRightOverlayDescriptor = LSPImages.imageDescriptorOverlayFromSymbolTag(additionalTags.get(1));
			}
		}

		// array index: 0 = top left, 1 = top right, 2 = bottom left, 3 = bottom right, 4 = underlay
		// see IDecoration.TOP_LEFT ... IDecoration.BOTTOM_RIGHT, IDecoration.UNDERLAY
		ImageDescriptor @Nullable[] overlays = {
				visibilityImageDescriptor, topRightOverlayDescriptor,
				severityImageDescriptor, bottomRightOverlayDescriptor,
				deprecatedImageDescriptor};

		return getImageWithOverlays(symbolKind, overlays);
	}

	public static final void dispose() {
		Stream.concat(
				colorToImageCache.values().stream(),
				overlayImagesCache.values().stream()
					.flatMap(map -> map.values().stream()))
			.filter(Objects::nonNull)
			.forEach(Image::dispose);
		overlayImagesCache.values().stream()
			.forEach(Map::clear);
		overlayImagesCache.clear();
		colorToImageCache.clear();
	}
}
