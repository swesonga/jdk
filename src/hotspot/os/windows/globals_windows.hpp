/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

#ifndef OS_WINDOWS_GLOBALS_WINDOWS_HPP
#define OS_WINDOWS_GLOBALS_WINDOWS_HPP

//
// Declare Windows specific flags. They are not available on other platforms.
//
#define RUNTIME_OS_FLAGS(develop,                                         \
                         develop_pd,                                      \
                         product,                                         \
                         product_pd,                                      \
                         range,                                           \
                         constraint)                                      \
                                                                          \
product(bool, UseAllWindowsProcessorGroups, false,                        \
        "Use all processor groups on supported Windows versions")         \
                                                                          \
product(bool, AlwaysRunTopLevelExceptionFilter, false,                    \
        "Always execute the top level exception filter")                  \
                                                                          \
product(bool, EnableOSExceptionWrapperSEH, false,                         \
        "Use a __try __except in the os_exception_wrapper")               \
                                                                          \
product(bool, SetHandlersAfterDllLoad, false,                             \
        "Set the vectored exception handler after loading java.dll")      \
                                                                          \
product(int, SleepMillisBeforeCrash, 3000,                                \
        "Time to sleep (in ms) before crashing the JVM")                  \
                                                                          \
product(bool, UseLoadLibraryEx, false,                                    \
        "Load the LibraryToCrashOn using LoadLibraryEx")                  \
                                                                          \
product(int, LoadLibraryExFlags, 0,                                       \
        "Flags to pass to LoadLibraryEx for the LibraryToCrashOn")        \
                                                                          \
product(bool, WaitForUserInputBeforeCrash, false,                         \
        "Call ReadConsole before crashing the JVM")                       \
                                                                          \
product(bool, IncrementGlobalFlag, false,                                 \
        "Add 1 to the global_flag every time the VEH is executed")        \
                                                                          \
product(bool, CrashAtLocation8b, false,                                   \
        "Crash at location 8b in the code")                               \
                                                                          \
product(bool, CrashAtLocation1, false,                                    \
        "Crash at location 1 in the code")                                \
                                                                          \
product(bool, CrashAtLocation2, false,                                    \
        "Crash at location 2 in the code")                                \
                                                                          \
product(bool, CrashAtLocation3, false,                                    \
        "Crash at location 3 in the code")                                \
                                                                          \
product(bool, CrashAtLocation3a, false,                                   \
        "Crash at location 3a in the code")                               \
                                                                          \
product(bool, CrashAtLocation3b, false,                                   \
        "Crash at location 3b in the code")                               \
                                                                          \
product(bool, CrashAtLocation3c, false,                                   \
        "Crash at location 3c in the code")                               \
                                                                          \
product(bool, CrashAtLocation3d, false,                                   \
        "Crash at location 3d in the code")                               \
                                                                          \
product(bool, CrashAtLocation3e, false,                                   \
        "Crash at location 3e in the code")                               \
                                                                          \
product(bool, CrashAtLocation4, false,                                    \
        "Crash at location 4 in the code")                                \
                                                                          \
product(bool, CrashAtLocation5, false,                                    \
        "Crash at location 5 in the code")                                \
                                                                          \
product(bool, CrashAtLocation6, false,                                    \
        "Crash at location 6 in the code")                                \
                                                                          \
product(bool, CrashAtLocation7, false,                                    \
        "Crash at location 7 in the code")                                \
                                                                          \
product(bool, CrashAtLocation8, false,                                    \
        "Crash at location 8 in the code")                                \
                                                                          \
product(bool, CrashAtLocation9, false,                                    \
        "Crash at location 9 in the code")                                \
                                                                          \
product(bool, CrashAtLocation10, false,                                   \
        "Crash at location 10 in the code")                               \
                                                                          \
product(bool, CrashAtLocation11, false,                                   \
        "Crash at location 11 in the code")                               \
                                                                          \
product(bool, CrashAtLocation12, false,                                   \
        "Crash at location 12 in the code")                               \
                                                                          \
product(bool, CrashAtLocation13, false,                                   \
        "Crash at location 13 in the code")                               \
                                                                          \
product(bool, CrashAtLocation14, false,                                   \
        "Crash at location 14 in the code")                               \
                                                                          \
product(bool, CrashAtLocation15, false,                                   \
        "Crash at location 15 in the code")                               \
                                                                          \
product(bool, CrashAtLocation16, false,                                   \
        "Crash at location 16 in the code")                               \
                                                                          \
product(bool, CrashAtLocationA, false,                                    \
        "Crash at location A in the code")                                \
                                                                          \
product(bool, CrashAtLocationB, false,                                    \
        "Crash at location B in the code")                                \
                                                                          \
product(bool, CrashAtLocationC, false,                                    \
        "Crash at location C in the code")                                \
                                                                          \
product(bool, CrashAtLocationD, false,                                    \
        "Crash at location D in the code")                                \
                                                                          \
product(bool, CrashAtLocationE, false,                                    \
        "Crash at location E in the code")                                \
                                                                          \
product(bool, CrashAtLocationF, false,                                    \
        "Crash at location F in the code")                                \
                                                                          \
product(ccstr, LibraryToCrashOn, nullptr,                                 \
        "File being loaded before bug")                                   \
                                                                          \
product(bool, EnableAllLargePageSizesForWindows, false,                   \
        "Enable support for multiple large page sizes on "                \
        "Windows Server")                                                 \
                                                                          \
product(bool, UseOSErrorReporting, false,                                 \
        "Let VM fatal error propagate to the OS (ie. WER on Windows)")

// end of RUNTIME_OS_FLAGS

//
// Defines Windows-specific default values. The flags are available on all
// platforms, but they may have different default values on other platforms.
//
define_pd_global(size_t, PreTouchParallelChunkSize, 1 * G);
define_pd_global(bool, UseLargePages, false);
define_pd_global(bool, UseLargePagesIndividualAllocation, true);
define_pd_global(bool, UseThreadPriorities, true) ;

#endif // OS_WINDOWS_GLOBALS_WINDOWS_HPP
