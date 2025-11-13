/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mickael Istria (Red Hat Inc.) - initial implementation
 *  Angelo Zerr <angelo.zerr@gmail.com> - Bug 525400 - [rename] improve rename support with ltk UI
 *  Jan Koehnlein (TypeFox) add rename empty message
 *******************************************************************************/
package org.eclipse.lsp4e.ui;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.osgi.util.NLS;

@NonNullByDefault({})
public final class Messages extends NLS {

	public static String definitionHyperlinkLabel;
	public static String declarationHyperlinkLabel;
	public static String typeDefinitionHyperlinkLabel;
	public static String implementationHyperlinkLabel;
	public static String PreferencesPage_Intro;
	public static String PreferencesPage_staticServers;
	public static String PreferencesPage_manualServers;
	public static String PreferencesPage_LaunchConfiguration;
	public static String PreferencesPage_LaunchMode;
	public static String PreferencesPage_Add;
	public static String PreferencesPage_Remove;
	public static String PreferencesPage_contentType;
	public static String PreferencesPage_languageServer;
	public static String PreferencesPage_Enabled;
	public static String PreferencesPage_enablementCondition;
	public static String PreferencePage_enablementCondition_true;
	public static String PreferencePage_enablementCondition_false;
	public static String PreferencePage_enablementCondition_enableAll;
	public static String PreferencePage_enablementCondition_disableAll;
	public static String PreferencesPage_logging_toFile_title;
	public static String PreferencesPage_logging_toFile_description;
	public static String PreferencesPage_logging_toConsole_title;
	public static String PreferencesPage_logging_toConsole_description;
	public static String preferencesPage_logging_info;
	public static String preferencesPage_logging_fileLogsLocation;
	public static String PreferencesPage_restartWarning_title;
	public static String PreferencesPage_restartWarning_message;
	public static String PreferencesPage_restartWarning_restart;
	public static String PreferencesPage_enableOnTypeFormatting;
	public static String NewContentTypeLSPLaunchDialog_associateContentType;
	public static String NewContentTypeLSPLaunchDialog_withLSPLaunch;
	public static String codeActions_description;
	public static String codeActions_label;
	public static String codeActions_emptyMenu;
	public static String codeLens_emptyMenu;
	public static String updateCodeActions_menu;
	public static String initializeLanguageServer_job;
	public static String computing;
	public static String notImplemented;
	public static String LSPFormatFilesHandler_FormattingFile;
	public static String LSPFormatFilesHandler_FormattingSelectedFiles;
	public static String LSPFormatHandler_DiscardedFormat;
	public static String LSPFormatHandler_DiscardedFormatResponse;
	public static String LSPFormatHandler_ReadOnlyEditor_title;
	public static String LSPFormatHandler_ReadOnlyEditor_inputReadonly;
	public static String LSPFormatHandler_ReadOnlyEditor_fileReadonly;
	public static String LSPFormatHandler_ReadOnlyEditor_makingWritableFailed;
	public static String LSPSymbolInWorkspaceDialog_DialogLabel;
	public static String LSPSymbolInWorkspaceDialog_DialogTitle;
	public static String updateCodelensMenu_job;
	public static String outline_computingSymbols;
	public static String rename_title;
	public static String rename_label;
	public static String rename_processor_name;
	public static String rename_processor_required;
	public static String serverEdit;
	public static String rename_empty_message;
	public static String rename_invalidated;
	public static String completionError;
	public static String completionIncomplete;
	public static String continueIncomplete;
	public static String linkWithEditor_label;
	public static String linkWithEditor_description;
	public static String linkWithEditor_tooltip;
	public static String LSSearchQuery_label;
	public static String LSSearchQuery_singularReference;
	public static String LSSearchQuery_pluralReferences;
	public static String enableDisableLSJob;
	public static String edit_CreateFile;
	public static String workspaceSymbols;
	public static String symbolsInFile;
	public static String typeHierarchy;
	public static String typeHierarchy_show_supertypes;
	public static String typeHierarchy_show_subtypes;
	public static String DocumentContentSynchronizer_OnSaveActionTimeout;
	public static String DocumentContentSynchronizer_TimeoutMessage;
	public static String DocumentContentSynchronizer_TimeoutThresholdMessage;
	public static String CreateFile_confirm_title;
	public static String CreateFile_confirm_message;
	public static String LSPProgressManager_BackgroundJobName;
	public static String LSConsoleName;
	public static String CH_no_call_hierarchy;
	public static String CH_finding_callers;
	public static String TH_no_type_hierarchy;
	public static String TH_diplay_hint;
	public static String TH_cannot_find_file;
	public static String occurrences;

	static {
		NLS.initializeMessages("org.eclipse.lsp4e.ui.messages", Messages.class); //$NON-NLS-1$
	}

	private Messages() {
	}
}
