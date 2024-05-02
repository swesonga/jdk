#
# Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

# @test
# @bug 6942632
# @requires os.family == "windows"
# @summary This test ensures that OpenJDK respects the process affinity
#          masks set when launched from the Windows command prompt using
#          "start /affinity HEXAFFINITY java.exe" when the
#          UseAllWindowsProcessorGroups flag is enabled.

get_runtime_available_processors()
{
  cmd_line= $1
  echo "Executing: $cmd_line"
  $cmd_line
  status=$?
  if [ ! $status -eq "0" ]; then
    echo "Test FAILED: $src_file";
    exit 1
  fi

  # Validate output from GetAvailableProcessors.java
  available_processors_regex="Runtime\\.availableProcessors: \\d+"
  grep -Po "$available_processors_regex" $log_file
  status=$?
  if [ ! $status -eq "0" ]; then
    echo "TESTBUG: $src_file did not output a processor count.";
    exit 1
  fi

  # Write the processor count to a file
  java_procs_log="processor_count_java.log"
  grep -Po "$available_processors_regex" $log_file | sed -e 's/[a-zA-Z: \.]//g' > $java_procs_log 2>&1

  # Read the processor count from the file
  runtime_available_processors=$(<$java_procs_log)
}

validate_available_processors()
{
  expected_processor_count=$1
  runtime_available_processors=$2

  echo "Expected processor count:    $expected_processor_count"
  echo "Runtime.availableProcessors: $runtime_available_processors"

  if [ "$runtime_available_processors" != "$expected_processor_count" ]; then
    echo "Test failed: Expected Runtime.availableProcessors to be $expected_processor_count but found $runtime_available_processors."
    exit 1
  fi
}

get_windows_version()
{
  winver_regex="^OS Version:\\s+([0-9]+\\).([0-9]+)\\.([0-9]+).+Build ([0-9]+)"

  systeminfo_log="test_systeminfo.log"
  systeminfo | grep -Po "$winver_regex" > $systeminfo_log 2>&1
  status=$?
  if [ ! $status -eq "0" ]; then
    echo "systeminfo did not output the OS version.";
    exit 1
  fi

  winver_log="test_winver.log"
  #sed -r "s/$winver_regex/\1,\2,\3/" $systeminfo_log > $winver_log 2>&1
  #windows_version_info_str=$(<$winver_log)
  #IFS=',' read -a windows_version_info <<< "$windows_version_info_str"
  #windows_major=$windows_version_info[0]
  #windows_minor=$windows_version_info[1]
  #windows_build=$windows_version_info[2]

  sed -r "s/$winver_regex/\1/" $systeminfo_log > $winver_log 2>&1
  windows_major=$(<$winver_log)

  sed -r "s/$winver_regex/\2/" $systeminfo_log > $winver_log 2>&1
  windows_minor=$(<$winver_log)

  sed -r "s/$winver_regex/\3/" $systeminfo_log > $winver_log 2>&1
  windows_build=$(<$winver_log)
}

get_schedules_all_processor_groups()
{
  get_windows_version
  schedules_all_processor_groups=false
  if [ $windows_major -gt 10 ]; then
    schedules_all_processor_groups=true
  else
    if [ $windows_major -eq 10 ]; then
      if [ $windows_minor -gt 0 ]; then
        schedules_all_processor_groups=true
      else
        if [ $is_windows_server ]; then
          if [ $windows_build -ge 20348 ]; then
            schedules_all_processor_groups=true
          fi
        else
          if [ $windows_build -ge 22000 ]; then
            schedules_all_processor_groups=true
          fi
        fi
      fi
    fi
  fi
}

system_root=$SystemRoot
if [ "${system_root}" = "" ]
then
  echo "SystemRoot environment variable not set. Checking SYSTEMROOT environment variable."
  system_root=$SYSTEMROOT
fi

if [ "${system_root}" = "" ]
then
  echo "The SystemRoot environment variable needs to be set. Test cannot execute."
  exit 1
fi

if [ "${TESTSRC}" = "" ]
then
  echo "TESTSRC not set. Test cannot execute."
  exit 1
fi

if [ "${TESTNATIVEPATH}" = "" ]
then
  echo "TESTNATIVEPATH not set. Test cannot execute."
  exit 1
fi

if [ -z "${TESTCLASSES}" ]; then
  echo "TESTCLASSES undefined: defaulting to ."
  TESTCLASSES=.
fi

echo "TESTCLASSES:    $TESTCLASSES"
echo "TESTJAVACOPTS:  $TESTJAVACOPTS"
echo "TESTTOOLVMOPTS: $TESTTOOLVMOPTS"

javac="${TESTJAVA}/bin/javac${EXE_SUFFIX}"

src_file_base=GetAvailableProcessors
src_file="${TESTSRC}/$src_file_base.java"
log_file="$src_file_base.output.log"
$javac ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${TESTCLASSES} $src_file

status=$?
if [ ! $status -eq "0" ]; then
  echo "Compilation failed: $src_file";
  exit 1
fi

# Write processor information from Windows APIs to a log file
get_proc_info_name="GetProcessorInfo${EXE_SUFFIX}"
get_proc_info_path="${TESTNATIVEPATH}/${get_proc_info_name}"
get_proc_info_log="$get_proc_info_name.output.log"
$get_proc_info_path > $get_proc_info_log 2>&1

status=$?
if [ ! $status -eq "0" ]; then
  echo "Could not launch $get_proc_info_path";
  exit 1
fi

# Validate output from GetProcessorInfo.exe
unsupported_os_regex="Unsupported OS\\."
grep -Po "$unsupported_os_regex" $get_proc_info_log
status=$?
if [ $status -eq "0" ]; then
  echo "Test skipped: Unsupported Windows version.";
  exit 0
fi

is_windows_server_regex="IsWindowsServer: (\\d)"
grep -Po "$is_windows_server_regex" $get_proc_info_log
status=$?
if [ ! $status -eq "0" ]; then
  echo "TESTBUG: $get_proc_info_path did not output a value for IsWindowsServer.";
  exit 1
fi

total_processors_regex="Active processor count across all processor groups: (\\d+,)+"
grep -Po "$total_processors_regex" $get_proc_info_log
status=$?
if [ ! $status -eq "0" ]; then
  echo "TESTBUG: $get_proc_info_path did not output a processor count across all processor groups.";
  exit 1
fi

processor_info_regex="Active processors per group: (\\d+,)+"
grep -Po "$processor_info_regex" $get_proc_info_log
status=$?
if [ ! $status -eq "0" ]; then
  echo "TESTBUG: $get_proc_info_path did not output a processor count.";
  exit 1
fi

# Write the per-group processor counts to a file
processor_info_log="processor_count_per_group.log"
grep -Po "$total_processors_regex" $get_proc_info_log | sed -e 's/[a-zA-Z: \.]//g' > $processor_info_log 2>&1
total_processors=$(<$processor_info_log)

grep -Po "$is_windows_server_regex" $get_proc_info_log | sed -e 's/[a-zA-Z: \.]//g' > $processor_info_log 2>&1
is_windows_server=$(<$processor_info_log)

grep -Po "$processor_info_regex" $get_proc_info_log   | sed -e 's/[a-zA-Z: \.]//g' > $processor_info_log 2>&1
group_processor_counts_str=$(<$processor_info_log)
IFS=, read -a group_processor_counts <<<"$group_processor_counts_str"

# Find the smallest processor group because on systems with different processor
# group sizes, "start /affinity" can still launch a process in a smaller
# processor group than the affinity provided via the /affinity parameter
let num_processors=64
for i in "${group_processor_counts[@]}"; do
  let group_processor_count=i
  echo "Active processors in group: $group_processor_count"
  if [ $group_processor_count -lt $num_processors ]; then
    num_processors=$group_processor_count
  fi
done

if [ $num_processors -le 0 ]; then
  echo "Test failed: $get_proc_info_name did not output a valid processor count.";
  exit 1
fi

if [ $num_processors -gt 64 ]; then
  echo "Test failed: $get_proc_info_name returned an invalid processor count.";
  exit 1
fi

if [ $num_processors -lt 64 ]; then
  let affinity=$((1 << num_processors))-1
  affinity=$(printf "%x" "$affinity")
else
  affinity=0xffffffffffffffff
fi

# Write Runtime.availableProcessors to a log file
java_cmd_line="${TESTJAVA}/bin/java -XX:+UseAllWindowsProcessorGroups ${TESTVMOPTS} -cp ${TESTCLASSES} $src_file_base"

OS=`uname -s`
case "$OS" in
  CYGWIN* )
    cmd_exe_start_args="/c start /wait /b /affinity"
    ;;
  * )
    cmd_exe_start_args="//c start //wait //b //affinity"
    ;;
esac

echo "Scenario 1: specifying affinity using the start command with the product flag disabled"
flags="-XX:-UseAllWindowsProcessorGroups"
java_cmd_line="${TESTJAVA}/bin/java $flags ${TESTVMOPTS} -cp ${TESTCLASSES} $src_file_base"
cmd_line="$system_root/System32/cmd.exe $cmd_exe_start_args $affinity $java_cmd_line > $log_file"

get_runtime_available_processors $cmd_line
validate_available_processors $num_processors $runtime_available_processors

echo "Scenario 2: specifying affinity using the start command with the product flag enabled"
flags="-XX:+UseAllWindowsProcessorGroups"
java_cmd_line="${TESTJAVA}/bin/java $flags ${TESTVMOPTS} -cp ${TESTCLASSES} $src_file_base"
cmd_line="$system_root/System32/cmd.exe $cmd_exe_start_args $affinity $java_cmd_line > $log_file"

get_runtime_available_processors $command_line
validate_available_processors $num_processors $runtime_available_processors

echo "Scenario 3: launching java without the start command and with the product flag disabled"
flags="-XX:-UseAllWindowsProcessorGroups"
java_cmd_line="${TESTJAVA}/bin/java $flags ${TESTVMOPTS} -cp ${TESTCLASSES} $src_file_base"

get_runtime_available_processors $java_cmd_line

# Since an affinity has not been specified, Runtime.availableProcessors can be different
# on machines that do not have the same processor count in every processor group.
if [ $java_runtime_processors -gt 64 ]; then
  echo "Test failed: Runtime.availableProcessors ($java_runtime_processors) cannot exceed the max processor group for a single processor group (64)."
  exit 1
fi

if [ $java_runtime_processors -lt $num_processors ]; then
  echo "Test failed: Runtime.availableProcessors ($java_runtime_processors) must be at least the processor count in smallest processor group ($num_processors)"
  exit 1
fi

echo "Scenario 4: launching java without the start command and with the product flag enabled"
flags="-XX:+UseAllWindowsProcessorGroups"
java_cmd_line="${TESTJAVA}/bin/java $flags ${TESTVMOPTS} -cp ${TESTCLASSES} $SRCFILEBASE"

get_runtime_available_processors $java_cmd_line
get_schedules_all_processor_groups

if [ $schedules_all_processor_groups ]; then
  if [ $java_runtime_processors -gt 64 ]; then
    echo "Test failed: Runtime.availableProcessors ($java_runtime_processors) cannot exceed the max processor group for a single processor group (64) on this Windows version."
    exit 1
  fi

  if [ $java_runtime_processors -lt $num_processors ]; then
    echo "Test failed: Runtime.availableProcessors ($java_runtime_processors) must be at least the processor count in smallest processor group ($num_processors)"
    exit 1
  fi
else
  if [ "$java_runtime_processors" != "$total_processors" ]; then
    echo "Test failed: Runtime.availableProcessors ($java_runtime_processors) != number of available processors ($total_processors)"
    exit 1
  fi
fi

echo "Test passed."
