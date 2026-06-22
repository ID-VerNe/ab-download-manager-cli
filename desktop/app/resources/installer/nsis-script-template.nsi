Unicode True
RequestExecutionLevel user
SetCompressor /SOLID lzma
!include "LogicLib.nsh"
!include "MUI2.nsh"


!define APP_PUBLISHER "{{ app_publisher }}"
!define APP_NAME "{{ app_name }}"
!define APP_DISPLAY_NAME "{{ app_display_name }}"
!define APP_DATA_DIR_NAME "{{ app_data_dir_name }}"
!define APP_VERSION "{{ app_version }}"
!define APP_VERSION_WITH_BUILD "{{ app_version_with_build }}"
!define APP_DISPLAY_VERSION "{{ app_display_version }}"
!define SOURCE_CODE_URL "{{ source_code_url }}"
!define PROJECT_WEBSITE "{{ project_website }}"
!define COPYRIGHT "{{ copyright }}"

!define INPUT_DIR "{{ input_dir }}"
!define LICENSE_FILE "{{ license_file }}"
!define MAIN_BINARY_NAME "${APP_NAME}"

!define SIDEBAR_IMAGE "{{ sidebar_image_file }}"
!define HEADER_IMAGE "{{ header_image_file }}"
!define ICON_FILE "{{ icon_file }}"

!define REG_UNINSTALL_KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}"
!define REG_RUN_KEY "Software\Microsoft\Windows\CurrentVersion\Run\${APP_NAME}"
!define REG_APP_KEY "Software\${APP_NAME}"

; icon for this installer!

Icon "${ICON_FILE}"
!define MUI_ICON "${ICON_FILE}"
!define MUI_UNICON "${ICON_FILE}"

!if "${SIDEBAR_IMAGE}" != ""
  !define MUI_WELCOMEFINISHPAGE_BITMAP "${SIDEBAR_IMAGE}"

  !define MUI_UNWELCOMEFINISHPAGE_BITMAP "${SIDEBAR_IMAGE}"
!endif

!if "${HEADER_IMAGE}" != ""
  !define MUI_HEADERIMAGE
  !define MUI_HEADERIMAGE_BITMAP  "${HEADER_IMAGE}"

  !define MUI_UNHEADERIMAGE
  !define MUI_UNHEADERIMAGE_BITMAP "${HEADER_IMAGE}"
!endif

VIProductVersion "${APP_VERSION_WITH_BUILD}"
VIAddVersionKey "ProductName" "${APP_DISPLAY_NAME}"
VIAddVersionKey "FileDescription" "${APP_DISPLAY_NAME}"
VIAddVersionKey "LegalCopyright" "${COPYRIGHT}"
VIAddVersionKey "FileVersion" "${APP_VERSION_WITH_BUILD}"
VIAddVersionKey "ProductVersion" "${APP_VERSION_WITH_BUILD}"

Name "${APP_DISPLAY_NAME}"
OutFile "{{ output_file }}"

InstallDir "$LOCALAPPDATA\${APP_NAME}"



!define INSTALL_DIR `$INSTDIR`
Function .onInit

    ; Call RestorePreviousInstallLocation

FunctionEnd

; configure instfiles page
!define MUI_FINISHPAGE_NOAUTOCLOSE
!define MUI_INSTFILESPAGE_NOAUTOCLOSE

; configure finish page
!define MUI_FINISHPAGE_LINK "Open project in GitHub"
!define MUI_FINISHPAGE_LINK_LOCATION "${SOURCE_CODE_URL}"
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION RunMainBinary

;Installation Pages
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_LICENSE "${LICENSE_FILE}"
!insertmacro MUI_PAGE_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH

;Uninstallation Pages
!insertmacro MUI_UNPAGE_WELCOME
!insertmacro MUI_UNPAGE_COMPONENTS
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_UNPAGE_FINISH

; set language
!insertmacro MUI_LANGUAGE "English"

; a macro clear files to cleanup installation folder
!macro clearFiles
    RmDir /r "${INSTALL_DIR}\app"
    RmDir /r "${INSTALL_DIR}\runtime"
    RmDir /r "${INSTALL_DIR}\cli"
    Delete "${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe"
    Delete "${INSTALL_DIR}\${MAIN_BINARY_NAME}.ico"
    Delete "${INSTALL_DIR}\uninstall.exe"
    RmDir "${INSTALL_DIR}"
!macroend

Function RunMainBinary
   Exec "${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe"
FunctionEnd

!macro GetBestExecutableName result
    StrCpy ${result} "${MAIN_BINARY_NAME}.exe"
!macroend

; Function RestorePreviousInstallLocation
;     ReadRegStr $4 SHCTX "${REG_APP_KEY}" "InstallPath"
;     ${if} $4 != ""
;         StrCpy $INSTDIR $4
;     ${endif}
; FunctionEnd

; I should improve this.
!macro closeApp
    !insertmacro GetBestExecutableName $1
    DetailPrint "Stopping running instances of $1"
    ; Get installer's own PID so we can exclude it from taskkill
    System::Call 'kernel32::GetCurrentProcessId() i.r2'
    ; Kill all matching processes EXCEPT the installer itself
    ExecWait 'taskkill /F /FI "PID ne $2" /IM "$1"' $0
    ${If} $0 == "0"
        Sleep 500
        BringToFront
        DetailPrint "Existing app stopped successfully"
    ${Else}
        DetailPrint "No running instances found to stop"
    ${Endif}
!macroend

!macro CreateStartMenu
	createDirectory "$SMPROGRAMS\${APP_DISPLAY_NAME}"
	createShortCut "$SMPROGRAMS\${APP_DISPLAY_NAME}\${APP_DISPLAY_NAME}.lnk" "${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe"
!macroend

!macro RemoveStartMenu
	RmDir /r "$SMPROGRAMS\${APP_DISPLAY_NAME}"
!macroend

!macro RemoveUserData
	RMDir /r "$PROFILE\${APP_DATA_DIR_NAME}"
	RmDir /r "${INSTALL_DIR}\${APP_DATA_DIR_NAME}"
!macroend

!macro CreateDesktopShortcut
    CreateShortcut "$DESKTOP\${APP_DISPLAY_NAME}.lnk" "${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe"
!macroend

!macro RemoveDesktopShortCut
	Delete "$DESKTOP\${APP_DISPLAY_NAME}.lnk"
!macroend

Function .onInstSuccess
    ; Check if the installer is running in silent mode
    ${If} ${Silent}
        ; In silent mode, always run the app
        Call RunMainBinary
    ${Endif}
FunctionEnd

Section "${APP_DISPLAY_NAME}"
    SectionInstType RO

    DetailPrint "Closing app (if any)"
    !insertmacro closeApp
    DetailPrint "clearing old app (if any)"
    !insertmacro clearFiles
    DetailPrint "writing new data"
    SetOutPath "${INSTALL_DIR}"
    CreateDirectory "${INSTALL_DIR}"

    WriteUninstaller "${INSTALL_DIR}\uninstall.exe"

    File /nonfatal /r "${INPUT_DIR}\"

    ; Add CLI to User PATH (non-admin, instant effect)
    DetailPrint "Adding CLI to User PATH..."
    Push $0
    ReadRegStr $0 HKCU "Environment" "PATH"
    ${If} $0 != ""
        ${If} $0 != *"${INSTALL_DIR}\cli\bin"*
            StrCpy $0 "$0;${INSTALL_DIR}\cli\bin"
            WriteRegExpandStr HKCU "Environment" "PATH" "$0"
        ${EndIf}
    ${Else}
        WriteRegExpandStr HKCU "Environment" "PATH" "${INSTALL_DIR}\cli\bin"
    ${EndIf}
    Pop $0

    ; Registry information for add/remove programs
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "DisplayName" "${APP_DISPLAY_NAME}"
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "DisplayIcon" "$\"${INSTALL_DIR}\${MAIN_BINARY_NAME}.exe$\""
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "DisplayVersion" "${APP_VERSION}"
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "Publisher" "${APP_PUBLISHER}"
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "InstallLocation" "$\"${INSTALL_DIR}$\""
    WriteRegStr SHCTX "${REG_UNINSTALL_KEY}" "UninstallString" "$\"${INSTALL_DIR}\uninstall.exe$\""
    WriteRegDWORD SHCTX "${REG_UNINSTALL_KEY}" "NoModify" "1"
    WriteRegDWORD SHCTX "${REG_UNINSTALL_KEY}" "NoRepair" "1"

    ; Registry keys for app installation path and version
    WriteRegStr SHCTX "${REG_APP_KEY}" "InstallPath" "${INSTALL_DIR}"
    WriteRegStr SHCTX "${REG_APP_KEY}" "Version" "${APP_VERSION}"
SectionEnd

Section "Start Menu"
    !insertmacro CreateStartMenu
SectionEnd

Section "Desktop Shortcut"
    !insertmacro CreateDesktopShortcut
SectionEnd

Section /o "un.Remove User Data"
    !insertmacro RemoveUserData
SectionEnd

Section "Uninstall"
    SectionInstType RO

    !insertmacro closeApp
    !insertmacro clearFiles

    !insertmacro RemoveStartMenu
    !insertmacro RemoveDesktopShortCut

    DeleteRegKey SHCTX "${REG_UNINSTALL_KEY}"
    DeleteRegKey SHCTX "${REG_APP_KEY}"

    ; Remove CLI from PATH on uninstall (machine-wide)
    DetailPrint "Removing CLI from PATH..."
    Push $0
    ReadRegStr $0 SHCTX "SYSTEM\CurrentControlSet\Control\Session Manager\Environment" "PATH"
    ${If} $0 != ""
        Push $0
        Push "${INSTALL_DIR}\cli\bin"
        Call un.RemoveFromPath
        Pop $0
        WriteRegExpandStr SHCTX "SYSTEM\CurrentControlSet\Control\Session Manager\Environment" "PATH" "$0"
    ${EndIf}
    Pop $0

    ; remove auto start on boot registry
    DeleteRegValue SHCTX "${REG_RUN_KEY}" "${APP_NAME}"
SectionEnd

; Helper function: removes a single path entry from a semicolon-delimited PATH string
; Usage: Push pathString, Push entryToRemove, Call RemoveFromPath, Pop result
Function un.RemoveFromPath
    Exch $0 ; entry to remove
    Exch
    Exch $1 ; original path string
    Push $2
    Push $3
    Push $4

    StrCpy $2 "" ; result buffer
    StrCpy $4 "0" ; first-item flag

    ; Split by semicolon and rebuild without the target entry
    loop:
        StrCpy $3 $1 1 0
        ${If} $3 == ""
            Goto done
        ${EndIf}
        ${If} $3 == ";"
            StrCpy $1 $1 "" 1 ; consume the semicolon
            Goto loop
        ${EndIf}

        ; Extract item up to next semicolon or end
        Push $1
        Push ";"
        Call un.StrStr
        Pop $3
        ${If} $3 == ""
            ; Last item
            ${If} $1 != $0
                ${If} $4 == "0"
                    StrCpy $2 "$1"
                    StrCpy $4 "1"
                ${Else}
                    StrCpy $2 "$2;$1"
                ${EndIf}
            ${EndIf}
            StrCpy $1 ""
        ${Else}
            ; There is a semicolon after this item
            StrLen $3 $3
            StrLen $1 $1
            IntOp $3 $1 - $3 ; length of item before semicolon
            StrCpy $3 $1 $3
            StrCpy $1 $1 "" $3 ; remaining after item+semicolon
            StrCpy $1 $1 "" 1 ; skip the semicolon

            ${If} $3 != $0
                ${If} $4 == "0"
                    StrCpy $2 "$3"
                    StrCpy $4 "1"
                ${Else}
                    StrCpy $2 "$2;$3"
                ${EndIf}
            ${EndIf}
        ${EndIf}
    Goto loop

    done:
    Pop $4
    Pop $3
    Pop $2
    Pop $1
    Exch $0
FunctionEnd

; Helper: find substring in string
; Usage: Push haystack, Push needle, Call un.StrStr, Pop result (empty if not found)
Function un.StrStr
    Exch $0 ; needle
    Exch
    Exch $1 ; haystack
    Push $2
    Push $3
    Push $4

    StrLen $3 $0
    ${If} $3 == 0
        StrCpy $2 ""
        Goto strstr_done
    ${EndIf}

    StrCpy $2 $1
    strstr_loop:
        StrCpy $4 $2 $3
        ${If} $4 == $0
            Goto strstr_done
        ${EndIf}
        StrLen $4 $2
        ${If} $4 == 0
            StrCpy $2 ""
            Goto strstr_done
        ${EndIf}
        StrCpy $2 $2 "" 1
    Goto strstr_loop

    strstr_done:
    Pop $4
    Pop $3
    Pop $2
    Pop $1
    Exch $0 ; result (empty if not found, or remaining string starting at match)
FunctionEnd
