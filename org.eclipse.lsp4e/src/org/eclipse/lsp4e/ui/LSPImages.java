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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.DecorationOverlayIcon;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.LanguageServerPlugin;
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
	 * <p>Cache for symbol images with various overlays and / or an underlay.</p>
	 * <ul>
	 * <li>Key: a combined key based on the element's kind (e.g. class or method)
	 *      and a set of overlay image descriptors for the icon corners and the underlay,</li>
	 * <li>Value: the base image with optional overlays and an optional underlay combined in one image.</li>
	 * </ul>
	 *
	 * See {@link #getImageWithOverlays(String, ImageDescriptor, ImageDescriptor, ImageDescriptor, ImageDescriptor, ImageDescriptor)}
	 */
	private static final Map<ImageWithOverlaysKey, Image> overlayImagesCache = new HashMap<>();

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
	public static final String IMG_METHOD_VIS_FILE = "IMG_METH_FILE"; //$NON-NLS-1$
	public static final String IMG_METHOD_VIS_INTERNAL = "IMG_METH_INTERNAL"; //$NON-NLS-1$
	public static final String IMG_METHOD_VIS_PRIVATE = "IMG_METH_PRIVATE"; //$NON-NLS-1$
	public static final String IMG_METHOD_VIS_PACKAGE = "IMG_METH_PACKAGE"; //$NON-NLS-1$
	public static final String IMG_METHOD_VIS_PROTECTED = "IMG_METH_PROTECTED"; //$NON-NLS-1$
	public static final String IMG_METHOD_VIS_PUBLIC = "IMG_METH_PUBLIC"; //$NON-NLS-1$
	public static final String IMG_PROPERTY = "IMG_PROPERTY"; //$NON-NLS-1$
	public static final String IMG_FIELD = "IMG_FIELD"; //$NON-NLS-1$
	public static final String IMG_FIELD_VIS_FILE = "IMG_FIELD_FILE"; //$NON-NLS-1$
	public static final String IMG_FIELD_VIS_INTERNAL = "IMG_FIELD_INTERNAL"; //$NON-NLS-1$
	public static final String IMG_FIELD_VIS_PRIVATE = "IMG_FIELD_PRIVATE"; //$NON-NLS-1$
	public static final String IMG_FIELD_VIS_PACKAGE = "IMG_FIELD_PACKAGE"; //$NON-NLS-1$
	public static final String IMG_FIELD_VIS_PROTECTED = "IMG_FIELD_PROTECTED"; //$NON-NLS-1$
	public static final String IMG_FIELD_VIS_PUBLIC = "IMG_FIELD_PUBLIC"; //$NON-NLS-1$
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
	public static final String IMG_NUMBER = "IMG_NUMBER"; //$NON-NLS-1$
	public static final String IMG_BOOLEAN = "IMG_BOOLEAN"; //$NON-NLS-1$
	public static final String IMG_ARRAY = "IMG_ARRAY"; //$NON-NLS-1$
	public static final String IMG_NULL = "IMG_NULL"; //$NON-NLS-1$
	public static final String IMG_UNIT = "IMG_UNIT"; //$NON-NLS-1$
	public static final String IMG_VALUE = "IMG_VALUE"; //$NON-NLS-1$
	public static final String IMG_KEYWORD = "IMG_KEYWORD"; //$NON-NLS-1$
	public static final String IMG_SNIPPET = "IMG_SNIPPET"; //$NON-NLS-1$
	public static final String IMG_REFERENCE = "IMG_REFERENCE"; //$NON-NLS-1$
	public static final String IMG_TERMINATE_CO = "IMG_TERMINATE_CO"; //$NON-NLS-1$
	public static final String IMG_EVENT = "IMG_EVENT"; //$NON-NLS-1$
	public static final String IMG_KEY = "IMG_KEY"; //$NON-NLS-1$
	public static final String IMG_OPERATOR = "IMG_OPERATOR"; //$NON-NLS-1$

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
		declareRegistryImage(IMG_TYPE_PARAMETER, OBJECT + "type_parameter.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_METHOD, OBJECT + "method.svg"); //$NON-NLS-1$

		declareRegistryImage(IMG_METHOD_VIS_FILE, OBJECT + "method_file_vis_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_METHOD_VIS_INTERNAL, OBJECT + "method_internal_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_METHOD_VIS_PRIVATE, OBJECT + "method_private_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_METHOD_VIS_PACKAGE, OBJECT + "method_package_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_METHOD_VIS_PROTECTED, OBJECT + "method_protected_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_METHOD_VIS_PUBLIC, OBJECT + "method_public_obj.svg"); //$NON-NLS-1$

		declareRegistryImage(IMG_PROPERTY, OBJECT + "property.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_FIELD, OBJECT + "field.svg"); //$NON-NLS-1$

		declareRegistryImage(IMG_FIELD_VIS_FILE, OBJECT + "field_file_vis_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_FIELD_VIS_INTERNAL, OBJECT + "field_internal_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_FIELD_VIS_PRIVATE, OBJECT + "field_private_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_FIELD_VIS_PACKAGE, OBJECT + "field_package_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_FIELD_VIS_PROTECTED, OBJECT + "field_protected_obj.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_FIELD_VIS_PUBLIC, OBJECT + "field_public_obj.svg"); //$NON-NLS-1$

		declareRegistryImage(IMG_CONSTRUCTOR, OBJECT + "constructor.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_ENUM, OBJECT + "enum.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_ENUM_MEMBER, OBJECT + "enum_member.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_STRUCT, OBJECT + "struct.png"); //$NON-NLS-1$
		declareRegistryImage(IMG_INTERACE, OBJECT + "interface.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_FUNCTION, OBJECT + "function.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_VARIABLE, OBJECT + "variable.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_CONSTANT, OBJECT + "constant.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OBJECT, OBJECT + "object.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_NUMBER, OBJECT + "number.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_BOOLEAN, OBJECT + "boolean.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_ARRAY, OBJECT + "array.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_NULL, OBJECT + "null.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_KEY, OBJECT + "key.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_EVENT, OBJECT + "event.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OPERATOR, OBJECT + "operator.svg"); //$NON-NLS-1$

		declareRegistryImage(IMG_TEXT, OBJECT + "text.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_UNIT, OBJECT + "unit.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_VALUE, OBJECT + "value.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_KEYWORD, OBJECT + "keyword.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_SNIPPET, OBJECT + "snippet.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_REFERENCE, OBJECT + "reference.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_SUPERTYPE, ACTION + "super_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_SUBTYPE, ACTION + "sub_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_TERMINATE_CO, OBJECT + "terminate_co.svg"); //$NON-NLS-1$

		declareRegistryImage(IMG_OVR_CONSTRUCTOR, OVERLAY + "constr_ovr.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_DEPRECATED, OVERLAY + "deprecated.svg"); //$NON-NLS-1$

		declareRegistryImage(IMG_OVR_PRIVATE, OVERLAY + "vis_private_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_PACKAGE, OVERLAY + "vis_package_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_PROTECTED, OVERLAY + "vis_protected_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_PUBLIC, OVERLAY + "vis_public_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_INTERNAL, OVERLAY + "vis_internal_co.svg"); //$NON-NLS-1$
		declareRegistryImage(IMG_OVR_FILE_VIS, OVERLAY + "vis_file_co.svg"); //$NON-NLS-1$

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

	private record ImageWithOverlaysKey(String baseImageKey,
			@Nullable ImageDescriptor overlayTopLeftDescriptor, @Nullable ImageDescriptor overlayTopRightDescriptor,
			@Nullable ImageDescriptor overlayBottomLeftDescriptor, @Nullable ImageDescriptor overlayBottomRightDescriptor,
			@Nullable ImageDescriptor underlayDescriptor) {}

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

	/**
	 * Returns an image representing the given symbol kind. Does not consider the document symbol's visibility
	 * (given by {@link SymbolTag}s).
	 *
	 * @param kind a document symbol's kind
	 * @return an image representing the given symbol kind or <code>null</code>
	 *
	 * @see #getImageDescriptor(String)
	 * @see AbstractLsp4eLabelProvider#getImage(Object)
	 * @see AbstractLsp4eLabelProvider#getImageFor(SymbolKind, java.util.List)
	 * @see AbstractLsp4eLabelProvider#getImageFor(SymbolKind, java.util.List, int)
	 */
	public static @Nullable Image imageFromSymbolKind(@Nullable SymbolKind kind) {
		if (kind == null) {
			return EMPTY_IMAGE;
		}

		String imgKey = imageKeyFromSymbolKind(kind);
		if (ISharedImages.IMG_OBJ_FILE.equals(imgKey)) {
			return getSharedImage(imgKey);
		}

		return getImage(imgKey);
	}

	public static @Nullable ImageDescriptor imageDescriptorFromSymbolKind(SymbolKind kind) {
		String imgKey = imageKeyFromSymbolKind(kind);
		if (ISharedImages.IMG_OBJ_FILE.equals(imgKey)) {
			return getSharedImageDescriptor(imgKey);
		}

		return getImageDescriptor(imgKey);
	}

	/**
	 * Returns an image identifier (key) that can be used with the image registry or in {@link #getImage(String)} and
	 * {@link #getImageDescriptor(String)} to retrieve the image representing the given document symbol kind.
	 * Does not consider any visibility details given in a document symbol's {@link SymbolTag}s.
	 *
	 * @param kind a document symbol's kind
	 * @return an image identifier (key) representing the given symbol kind's corresponding image
	 *
	 * @see #getImage(String)
	 * @see #getImageDescriptor(String)
	 * @see #getImageWithOverlays(String, ImageDescriptor, ImageDescriptor, ImageDescriptor, ImageDescriptor, ImageDescriptor)
	 * @see AbstractLsp4eLabelProvider#getImageKeyFromSymbolKindWithVisibility(SymbolKind, java.util.List)
	 */
	public static String imageKeyFromSymbolKind(SymbolKind kind) {
		return switch (kind) {
		case Array -> IMG_ARRAY;
		case Boolean -> IMG_BOOLEAN;
		case Class -> IMG_CLASS;
		case Constant -> IMG_CONSTANT;
		case Constructor -> IMG_CONSTRUCTOR;
		case Enum -> IMG_ENUM;
		case EnumMember -> IMG_ENUM_MEMBER;
		case Struct -> IMG_STRUCT;
		case Field -> IMG_FIELD;
		case File -> ISharedImages.IMG_OBJ_FILE;
		case Function -> IMG_FUNCTION;
		case Interface -> IMG_INTERACE;
		case Method -> IMG_METHOD;
		case Module -> IMG_MODULE;
		case Namespace -> IMG_NAMESPACE;
		case Number -> IMG_NUMBER;
		case Object -> IMG_OBJECT;
		case Package -> IMG_PACKAGE;
		case Property -> IMG_PROPERTY;
		case String -> IMG_TEXT;
		case TypeParameter -> IMG_TYPE_PARAMETER;
		case Variable -> IMG_VARIABLE;
		case Null -> IMG_NULL;
		case Event -> IMG_EVENT;
		case Key -> IMG_KEY;
		case Operator -> IMG_OPERATOR;
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
		case Event -> getImage(IMG_EVENT);
		case Operator -> getImage(IMG_OPERATOR);
		case null -> null;
		};
	}

	/**
	 * Returns an overlay icon ({@link Image}) representing the given symbol tag,
	 * e.g. a document symbol's visibility "private" or the symbol being "static" or "final".
	 *
	 * @param symbolTag a document symbol's tag to be represented as an overlay icon
	 * @return the overlay icon corresponding to the given tag or <code>null</code> if the image cannot be found
	 *
	 * @see #imageDescriptorOverlayFromSymbolTag(SymbolTag)
	 */
	public static @Nullable Image imageOverlayFromSymbolTag(SymbolTag symbolTag) {
		String imgKey = imageOverlayKeyFromSymbolTag(symbolTag);
		return getImage(imgKey);
	}

	public static @Nullable ImageDescriptor imageDescriptorOverlayFromSymbolTag(SymbolTag symbolTag) {
		String imgKey = imageOverlayKeyFromSymbolTag(symbolTag);
		return getImageDescriptor(imgKey);
	}

	private static String imageOverlayKeyFromSymbolTag(SymbolTag symbolTag) {
		return switch (symbolTag) {
		case Deprecated -> IMG_OVR_DEPRECATED;
		case Private -> IMG_OVR_PRIVATE;
		case Package -> IMG_OVR_PACKAGE;
		case Protected -> IMG_OVR_PROTECTED;
		case Public -> IMG_OVR_PUBLIC;
		case Internal -> IMG_OVR_INTERNAL;
		case File -> IMG_OVR_FILE_VIS;
		case Static -> IMG_OVR_STATIC;
		case Abstract -> IMG_OVR_ABSTRACT;
		case Final -> IMG_OVR_FINAL;
		case Sealed -> IMG_OVR_SEALED;
		case Transient -> IMG_OVR_TRANSIENT;
		case Volatile -> IMG_OVR_VOLATILE;
		case Synchronized -> IMG_OVR_SYNC;
		case Virtual -> IMG_OVR_VIRTUAL;
		case Nullable -> IMG_OVR_NULLABLE;
		case NonNull -> IMG_OVR_NON_NULL;
		case Declaration -> IMG_OVR_DECLARATION;
		case Definition -> IMG_OVR_DEFINITION;
		case ReadOnly -> IMG_OVR_READ_ONLY;
		case Overrides -> IMG_OVR_OVERRIDE;
		case Implements -> IMG_OVR_IMPLEMENT;
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

	/**
	 * Returns a new or cached image built from the given arguments.
	 * The image is a combination of a base image with optional overlay and underlay icons.
	 *
	 * @param baseImageKey the image identifier given by e.g. {@link #imageKeyFromSymbolKind(SymbolKind)}
	 * @param topLeftOverlayDescriptor the overlay icon descriptor for the upper left corner of the base icon
	 * @param topRightOverlayDescriptor the overlay icon descriptor for the upper right corner of the base icon
	 * @param bottomLeftOverlayDescriptor the overlay icon descriptor for the lower left corner of the base icon
	 * @param bottomRightOverlayDescriptor the overlay icon descriptor for the lower right corner of the base icon
	 * @param underlayImageDescriptor the underlay icon descriptor for being drawn behind the the base icon
	 * @return returns a new or cached image built from the given arguments.
	 *
	 * @see #imageKeyFromSymbolKind(SymbolKind)
	 * @see AbstractLsp4eLabelProvider#getImageKeyFromSymbolKindWithVisibility(SymbolKind, java.util.List)
	 */
	public static @Nullable Image getImageWithOverlays(String baseImageKey,
			@Nullable ImageDescriptor topLeftOverlayDescriptor, @Nullable ImageDescriptor topRightOverlayDescriptor,
			@Nullable ImageDescriptor bottomLeftOverlayDescriptor, @Nullable ImageDescriptor bottomRightOverlayDescriptor,
			@Nullable ImageDescriptor underlayImageDescriptor) {

		// array index: 0 = top left, 1 = top right, 2 = bottom left, 3 = bottom right, 4 = underlay
		// see IDecoration.TOP_LEFT ... IDecoration.BOTTOM_RIGHT, IDecoration.UNDERLAY
		@Nullable ImageDescriptor[] overlays = {
				topLeftOverlayDescriptor, topRightOverlayDescriptor,
				bottomLeftOverlayDescriptor, bottomRightOverlayDescriptor,
				underlayImageDescriptor};

		long numOverlays = Arrays.stream(overlays)
				.filter(Objects::nonNull)
				.count();
		if (numOverlays == 0L) {
			return getImage(baseImageKey);
		}

		ImageWithOverlaysKey key = new ImageWithOverlaysKey(baseImageKey,
				topLeftOverlayDescriptor, topRightOverlayDescriptor,
				bottomLeftOverlayDescriptor, bottomRightOverlayDescriptor, underlayImageDescriptor);

		if (overlayImagesCache.containsKey(key)) {
			return overlayImagesCache.get(key);
		}

		final Image baseImage = getImage(baseImageKey);
		if (baseImage == null) {
			// Do not create cache entries for non existing base images
			return null;
		}

		Image imageWithOverlays = new DecorationOverlayIcon(baseImage, overlays).createImage();
		overlayImagesCache.put(key, imageWithOverlays);

		return imageWithOverlays;
	}

	public static final void dispose() {
		Stream.concat(
				colorToImageCache.values().stream(),
				overlayImagesCache.values().stream())
			.filter(Objects::nonNull)
			.forEach(Image::dispose);
		overlayImagesCache.clear();
		colorToImageCache.clear();
	}
}
