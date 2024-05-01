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
 * @summary This test verifies that OpenJDK can use all available
 *          processors on Windows 11/Windows Server 2022 and later.
 *          It also verifies that OpenJDK respects the process affinity
 *          masks set when launched from the Windows command prompt using
 *          "start /affinity HEXAFFINITY java.exe".
 * @requires vm.flagless
 * @library /test/lib
 * @run main/native TestAvailableProcessors
 */

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestAvailableProcessors {

    private static final String totalProcessorCountMessage = "Active processor count across all processor groups: ";
    private static final String processorCountPerGroupMessage = "Active processors per group: ";
    private static final String isWindowsServerMessage = "IsWindowsServer: ";

    private static final String runtimeAvailableProcessorsMessage = "Runtime.availableProcessors: ";
    private static final String osVersionMessage = "OS Version: ";
    private static final String unsupportedPlatformMessage = "The UseAllWindowsProcessorGroups flag is not supported on this Windows version and will be ignored.";

    private static OutputAnalyzer getAvailableProcessorsOutput(boolean productFlagEnabled) throws IOException {
        String productFlag = productFlagEnabled ? "-XX:+UseAllWindowsProcessorGroups" : "-XX:-UseAllWindowsProcessorGroups";

        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(
            new String[] {productFlag, "GetAvailableProcessors"}
        );

        var output = new OutputAnalyzer(processBuilder.start());
        output.shouldHaveExitValue(0);
        output.shouldContain(runtimeAvailableProcessorsMessage);

        return output;
    }

    private static int getAvailableProcessors(List<String> output) {
        int runtimeAvailableProcs = 0;

        for (var line: output) {
            if (line.startsWith(runtimeAvailableProcessorsMessage)) {
                String runtimeAvailableProcsStr = line.substring(runtimeAvailableProcessorsMessage.length());
                System.out.println("Found Runtime.availableProcessors: " + runtimeAvailableProcsStr);

                runtimeAvailableProcs = Integer.parseInt(runtimeAvailableProcsStr);
            }
        }

        return runtimeAvailableProcs;
    }

    private static int getAvailableProcessors(boolean productFlagEnabled) throws IOException {
        OutputAnalyzer output = getAvailableProcessorsOutput(productFlagEnabled);
        return getAvailableProcessors(output.stdoutAsLines());
    }

    private static void verifyRuntimeAvailableProcessorsRange(int runtimeAvailableProcs, int smallestProcessorGroup, int largestProcessorGroup) {
        if (runtimeAvailableProcs < smallestProcessorGroup) {
            String error = String.format("Runtime.availableProcessors (%d) must be at least the processor count in smallest processor group (%d)", runtimeAvailableProcs, smallestProcessorGroup);
            throw new RuntimeException(error);
        } else if (runtimeAvailableProcs > largestProcessorGroup) {
            String error = String.format("Runtime.availableProcessors (%d) cannot exceed the max processor group size for a single processor group (%d).", runtimeAvailableProcs, largestProcessorGroup);
            throw new RuntimeException(error);
        }
    }

    private static void verifyAvailableProcessorsWithDisabledProductFlag(int smallestProcessorGroup, int largestProcessorGroup) throws IOException {
        boolean productFlagEnabled = false;
        int runtimeAvailableProcs = getAvailableProcessors(productFlagEnabled);

        verifyRuntimeAvailableProcessorsRange(runtimeAvailableProcs, smallestProcessorGroup, largestProcessorGroup);
    }

    private static String getWindowsVersion() throws IOException {
        String systeminfoPath = "systeminfo.exe";

        var processBuilder = new ProcessBuilder(systeminfoPath);
        OutputAnalyzer output = new OutputAnalyzer(processBuilder.start());
        output.shouldHaveExitValue(0);
        output.shouldContain(osVersionMessage);
        List<String> lines = output.stdoutAsLines();

        String osVersion = null;
        for (var line: lines) {
            if (line.startsWith(osVersionMessage)) {
                osVersion = line.substring(osVersionMessage.length()).trim();
                break;
            }
        }

        System.out.println("Found OS version: " + osVersion);
        return osVersion;
    }

    private static boolean getSchedulesAllProcessorGroups(boolean isWindowsServer) throws IOException {
        String windowsVer = getWindowsVersion();
        String[] parts = windowsVer.split(" ");
        String[] versionParts = parts[0].split("\\.");

        if (versionParts.length != 3) {
            throw new RuntimeException("Unexpected Windows version format.");
        }

        int major = Integer.parseInt(versionParts[0]);
        int minor = Integer.parseInt(versionParts[1]);
        int build = Integer.parseInt(versionParts[2]);

        if (major > 10) {
            return true;
        }

        if (major < 10) {
            return false;
        }

        if (minor > 0) {
            return true;
        }

        if (isWindowsServer) {
            return build >= 20348;
        } else {
            return build >= 22000;
        }
    }

    private static void verifyAvailableProcessorsWithEnabledProductFlag(boolean schedulesAllProcessorGroups, int totalProcessorCount, int smallestProcessorGroup, int largestProcessorGroup) throws IOException {
        boolean productFlagEnabled = true;

        OutputAnalyzer output = getAvailableProcessorsOutput(productFlagEnabled);
        int runtimeAvailableProcs = getAvailableProcessors(output.stdoutAsLines());

        if (schedulesAllProcessorGroups) {
            if (runtimeAvailableProcs != totalProcessorCount) {
                String error = String.format("Runtime.availableProcessors (%d) is not equal to the expected total processor count (%d)", runtimeAvailableProcs, totalProcessorCount);
                throw new RuntimeException(error);
            }
        } else {
            output.shouldContain(unsupportedPlatformMessage);
            verifyRuntimeAvailableProcessorsRange(runtimeAvailableProcs, smallestProcessorGroup, largestProcessorGroup);
        }
    }

    private static void verifyAvailableProcessorsFromStartAffinity(boolean productFlagEnabled, int processors) throws IOException {
        long affinity = getAffinityForProcessorCount(processors);

        String productFlag = productFlagEnabled ? "-XX:+UseAllWindowsProcessorGroups" : "-XX:-UseAllWindowsProcessorGroups";

        ProcessBuilder processBuilder = ProcessTools.createLimitedTestJavaProcessBuilder(
            new String[] {productFlag, "GetAvailableProcessors"}
        );

        String javaCommandLine = ProcessTools.getCommandLine(processBuilder);

        List<String> args = new ArrayList<String>();
        args.add("cmd.exe");
        args.add("/c");
        args.add("start /wait /b /affinity " + String.format("%016X", affinity) + " " + javaCommandLine);
        processBuilder = new ProcessBuilder(args);

        System.out.println("Using command line: " + ProcessTools.getCommandLine(processBuilder));

        var output = new OutputAnalyzer(processBuilder.start());
        output.shouldHaveExitValue(0);
        output.shouldContain(runtimeAvailableProcessorsMessage);

        int runtimeAvailableProcs = getAvailableProcessors(productFlagEnabled);

        if (runtimeAvailableProcs != processors) {
            String error = String.format("Runtime.availableProcessors (%d) is not equal to the expected processor count (%d)", runtimeAvailableProcs, processors);
            throw new RuntimeException(error);
        }
    }

    public static void main(String[] args) throws IOException {
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
                    }

                    if (processorCount < smallestProcessorGroup) {
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

        // Test all valid affinities using the start command
        for (int processors = smallestProcessorGroup; processors >= 1; processors--) {
            verifyAvailableProcessorsFromStartAffinity(true, processors);
            verifyAvailableProcessorsFromStartAffinity(false, processors);
        }

        // Launch java without the start command and with the product flag disabled
        verifyAvailableProcessorsWithDisabledProductFlag(smallestProcessorGroup, largestProcessorGroup);

        // Launch java without the start command and with the product flag enabled
        boolean schedulesAllProcessorGroups = getSchedulesAllProcessorGroups(isWindowsServer);
        verifyAvailableProcessorsWithEnabledProductFlag(schedulesAllProcessorGroups, totalProcessorCount, smallestProcessorGroup, largestProcessorGroup);
    }

    private static long getAffinityForProcessorCount(int processorCount) {
        if (processorCount < 0 || processorCount > 64) {
            throw new IllegalArgumentException("processorCount: " + processorCount);
        }

        if (processorCount == 64) {
            return 0xffffffffffffffffL;
        }

        return (1L << processorCount) - 1;
    }
}
