/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include <windows.h>
#include "jni.h"
#include "jni_util.h"

/*
 * WLDP (Windows Lockdown Policy) support.
 *
 * WldpCanExecuteFile queries whether Windows Defender Application Control
 * (WDAC) policy permits execution of a given file.  The API lives in
 * wldp.dll which may not be present on older Windows versions, so we
 * load it dynamically.
 *
 * Enum values mirrored from wldp.h:
 *   WLDP_EXECUTION_POLICY_BLOCKED         = 0
 *   WLDP_EXECUTION_POLICY_ALLOWED         = 1
 *   WLDP_EXECUTION_POLICY_REQUIRE_SANDBOX = 2
 *
 *   WLDP_EXECUTION_EVALUATION_OPTION_NONE = 0
 */

/* WLDP_EXECUTION_POLICY values */
#define WLDP_EXECUTION_POLICY_BLOCKED          0
#define WLDP_EXECUTION_POLICY_ALLOWED          1
#define WLDP_EXECUTION_POLICY_REQUIRE_SANDBOX  2

/* WLDP_EXECUTION_EVALUATION_OPTIONS values */
#define WLDP_EXECUTION_EVALUATION_OPTION_NONE  0

/* WLDP_HOST_GUID_OTHER = {626CBEC3-E1FA-4227-9800-ED210274CF7C} */
static const GUID WLDP_HOST_GUID_OTHER = {
    0x626CBEC3, 0xE1FA, 0x4227,
    { 0x98, 0x00, 0xED, 0x21, 0x02, 0x74, 0xCF, 0x7C }
};

/* Function pointer type for WldpCanExecuteFile */
typedef HRESULT (WINAPI *PFN_WldpCanExecuteFile)(
    const GUID *host,
    int         options,
    HANDLE      fileHandle,
    const WCHAR *auditInfo,
    int        *result
);

/* Cached function pointer — resolved once, reused forever. */
static volatile PFN_WldpCanExecuteFile pfnWldpCanExecuteFile = NULL;
static volatile BOOL wldpInitialized = FALSE;

/*
 * Attempt to resolve WldpCanExecuteFile from wldp.dll.
 * If wldp.dll is absent or the function is not exported,
 * pfnWldpCanExecuteFile stays NULL (meaning WLDP is unavailable).
 */
static void initWldp(void) {
    if (wldpInitialized) return;

    HMODULE hWldp = LoadLibraryW(L"wldp.dll");
    if (hWldp != NULL) {
        pfnWldpCanExecuteFile = (PFN_WldpCanExecuteFile)
            GetProcAddress(hWldp, "WldpCanExecuteFile");
        /* Do NOT FreeLibrary — keep the DLL loaded for the process lifetime. */
    }
    wldpInitialized = TRUE;
}

/*
 * Class:     jdk_internal_loader_URLClassPath
 * Method:    wldpCanExecuteFile0
 * Signature: (Ljava/lang/String;)I
 *
 * Returns WLDP_EXECUTION_POLICY_ALLOWED (1) if the file passes WDAC policy,
 * WLDP_EXECUTION_POLICY_BLOCKED (0) if blocked,
 * WLDP_EXECUTION_POLICY_REQUIRE_SANDBOX (2) if sandbox needed,
 * or -1 if the WLDP API is unavailable (old Windows, no wldp.dll).
 * Returns -2 on error (e.g. cannot open file, HRESULT failure).
 */
JNIEXPORT jint JNICALL
Java_jdk_internal_loader_URLClassPath_wldpCanExecuteFile0(JNIEnv *env,
                                                          jclass cls,
                                                          jstring path) {
    if (!wldpInitialized) {
        initWldp();
    }
    if (pfnWldpCanExecuteFile == NULL) {
        /* WLDP API not available — signal caller to allow execution */
        return (jint)-1;
    }

    const jchar *pathChars = (*env)->GetStringChars(env, path, NULL);
    if (pathChars == NULL) {
        return (jint)-2;
    }

    /* Open the file for reading — WldpCanExecuteFile requires a HANDLE */
    HANDLE hFile = CreateFileW(
        (LPCWSTR)pathChars,
        GENERIC_READ,
        FILE_SHARE_READ | FILE_SHARE_DELETE,
        NULL,
        OPEN_EXISTING,
        FILE_ATTRIBUTE_NORMAL,
        NULL
    );

    (*env)->ReleaseStringChars(env, path, pathChars);

    if (hFile == INVALID_HANDLE_VALUE) {
        return (jint)-2;
    }

    int policy = WLDP_EXECUTION_POLICY_BLOCKED;
    HRESULT hr = pfnWldpCanExecuteFile(
        &WLDP_HOST_GUID_OTHER,
        WLDP_EXECUTION_EVALUATION_OPTION_NONE,
        hFile,
        L"Java classpath class file",     /* auditInfo for event log */
        &policy
    );

    CloseHandle(hFile);

    if (FAILED(hr)) {
        return (jint)-2;
    }

    return (jint)policy;
}
