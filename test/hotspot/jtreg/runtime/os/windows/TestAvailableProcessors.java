/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6942632
 * @requires os.family == "windows"
 * @summary This test ensures that OpenJDK respects the process affinity
 *          masks set when launched from the Windows command prompt using
 *          "start /affinity HEXAFFINITY java.exe" when the
 *          UseAllWindowsProcessorGroups flag is enabled.
 * @requires vm.flagless
 * @library /test/lib
 * @run main/native TestAvailableProcessors
 */

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

public class TestAvailableProcessors {

    private static final String totalProcessorCountMessage = "Active processor count across all processor groups: ";
    private static final String processorCountPerGroupMessage = "Active processors per group: ";
    private static final String isWindowsServerMessage = "IsWindowsServer: ";

    private static final String runtimeAvailableProcessorsMessage = "Runtime.availableProcessors: ";
    private static final String osVersionMessage = "OS Version: ";

    private List<String> getAvailableProcessorsOutput(boolean productFlagEnabled) {
        String productFlag = productFlagEnabled ? "-XX:+UseAllWindowsProcessorGroups" : "-XX:-UseAllWindowsProcessorGroups";

        var processBuilder = new createLimitedTestJavaProcessBuilder(
            new String[] {productFlag, "GetAvailableProcessors"}
        );

        var output = new OutputAnalyzer(processBuilder.start());
        output.shouldHaveExitValue(0);
        output.shouldContain(runtimeAvailableProcessorsMessage);

        return output.stdoutAsLines();
    }

    private int getAvailableProcessors(boolean productFlagEnabled) {
        int runtimeAvailableProcs = 0;

        List<String> lines = getAvailableProcessorsOutput(productFlagEnabled);
        for (var line: lines) {
            if (line.startsWith(runtimeAvailableProcessorsMessage)) {
                String runtimeAvailableProcsStr = line.substring(runtimeAvailableProcessorsMessage.length());
                System.out.println("Found Runtime.availableProcessors: " + runtimeAvailableProcsStr);

                runtimeAvailableProcs = Integer.parseInt(runtimeAvailableProcsStr);
            }
        }

        return runtimeAvailableProcs;
    }

    private void verifyAvailableProcessorsWithDisabledProductFlag(int smallestProcessorGroup, int largestProcessorGroup) {
        boolean productFlagEnabled = false;
        int runtimeAvailableProcs = getAvailableProcessors(productFlagEnabled);

        if (runtimeAvailableProcs < smallestProcessorGroup) {
            String error = String.format("Runtime.availableProcessors ({%d}) must be at least the processor count in smallest processor group ({%d})", runtimeAvailableProcs, smallestProcessorGroup)
            throw new Exception(error);
        } else if (runtimeAvailableProcs > largestProcessorGroup) {
            String error = String.format("Runtime.availableProcessors ({%d}) cannot exceed the max processor group size for a single processor group ({%d}).", runtimeAvailableProcs, largestProcessorGroup)
            throw new Exception(error);
        }
    }

    private String getWindowsVersion() {
        String systeminfoPath = "systeminfo.exe";

        var processBuilder = new ProcessBuilder(systeminfoPath);
        OutputAnalyzer output = new OutputAnalyzer(processBuilder.start());
        output.shouldHaveExitValue(0);
        output.shouldContain(osVersionMessage);
        List<String> lines = output.stdoutAsLines();

        for (var line: lines) {
            if (line.startsWith(osVersionMessage)) {
                String osVersionStr = line.substring(osVersionMessage.length()).trim();
                System.out.println("Found OS version: " + osVersionStr);

                return osVersionStr;
            }
        }
    }

    private boolean getSchedulesAllProcessorGroups(boolean isWindowsServer) {
        String windowsVer = getWindowsVersion();
        int major = 0;
        int minor = 0;
        int build = 0;

        if (major > 10) {
            return true;
        }

        if (major == 10) {
            if (minor > 0) {
                return true;
            }

            if (isWindowsServer) {
                return build >= 20348;
            } else {
                return build >= 22000;
            }
        }

        return false;
    }

    private void verifyAvailableProcessorsWithEnabledProductFlag(boolean isWindowsServer) {
        boolean productFlagEnabled = true;
        int runtimeAvailableProcs = getAvailableProcessors(productFlagEnabled);

        boolean schedulesAllProcessorGroups = getSchedulesAllProcessorGroups(isWindowsServer);

        if (schedulesAllProcessorGroups) {
            if (processorGroups > 1) {
                //
            } else {
                
            }
        } else {

        }
    }

    private void verifyAvailableProcessorsWithDisabledProductFlag(long affinity) {
        // launch cmd.exe
    }

    private void verifyAvailableProcessorsWithEnabledProductFlag(long affinity) {
        // launch cmd.exe
        // verify warning exists since at most 1 processor group will be used.
    }

    public static void main(String[] args) throws Exception {
        // Launch GetProcessorInfo.exe to gather processor counts
        Path nativeGetProcessorInfo = Paths.get(Utils.TEST_NATIVE_PATH)
            .resolve("GetProcessorInfo.exe")
            .toAbsolutePath();

        var processBuilder = new ProcessBuilder(nativeGetProcessorInfo.toString());
        OutputAnalyzer output = new OutputAnalyzer(processBuilder.start());
        output.shouldHaveExitValue(0);
        output.shouldContain(totalProcessorCountMessage);
        output.shouldContain(processorCountPerGroupMessage);
        output.shouldContain(isWindowsServerMessage);

        int totalProcessorCount = 0;
        int smallestProcessorGroup = Integer.MAX_VALUE;
        int largestProcessorGroup = 0;
        int processorGroups = 0;
        boolean isWindowsServer = false;

        List<String> lines = output.stdoutAsLines();

        for (var line: lines) {
            if (line.startsWith(totalProcessorCountMessage)) {
                String totalProcessorCountStr = line.substring(totalProcessorCountMessage.length());
                System.out.println("Found total processor count: " + totalProcessorCountStr);

                totalProcessorCount = Integer.parseInt(totalProcessorCountStr);
            } else if (line.startsWith(processorCountPerGroupMessage)) {
                String processorCountPerGroupStr = line.substring(processorCountPerGroupMessage.length());
                System.out.println("Found processor counts for groups: " + processorCountPerGroupStr);

                String[] processorCountsPerGroup = processorCountPerGroupStr.split(",");

                for (var processorCountStr: processorCountsPerGroup) {
                    System.out.println("Found a processor group of size " + processorCountStr);

                    int processorCount = Integer.parseInt(processorCountStr);
                    if (processorCount > largestProcessorGroup) {
                        largestProcessorGroup = processorCount;
                    } else if (processorCount < smallestProcessorGroup) {
                        smallestProcessorGroup = processorCount;
                    }

                    processorGroups++;
                }
            } else if (line.startsWith(isWindowsServerMessage)) {
                String isWindowsServerStr = line.substring(isWindowsServerMessage.length());
                System.out.println("Found IsWindowsServer: " + isWindowsServerStr);

                isWindowsServer = Integer.parseInt(isWindowsServerStr) > 0;
            }
        }

        // Specify affinity using the start command with the product flag disabled
        verifyAvailableProcessorsWithDisabledProductFlag(smallestProcessorGroup);

        // Specify affinity using the start command with the product flag enabled
        verifyAvailableProcessorsWithEnabledProductFlag(smallestProcessorGroup);

        // Launch java without the start command and with the product flag disabled
        verifyAvailableProcessorsWithDisabledProductFlag(smallestProcessorGroup, largestProcessorGroup);

        // Launch java without the start command and with the product flag enabled
        verifyAvailableProcessorsWithEnabledProductFlag(isWindowsServer);
    }
}
