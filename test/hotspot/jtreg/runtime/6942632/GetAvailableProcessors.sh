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

OS=`uname -s`
case "$OS" in
  Windows* | CYGWIN* )
    ;;
  * )
    echo "Cannot run a Windows-specific test on $OS"
    exit 1
    ;;
esac

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

JAVAC="${TESTJAVA}/bin/javac"

SRCFILEBASE=GetAvailableProcessors
SRCFILE="${TESTSRC}/$SRCFILEBASE.java"
LOGFILE="${TESTCLASSES}/$SRCFILEBASE.output.log"
$JAVAC ${TESTJAVACOPTS} ${TESTTOOLVMOPTS} -d ${TESTCLASSES} $SRCFILE

status=$?
if [ ! $status -eq "0" ]; then
  echo "Compilation failed: $SRCFILE";
  exit 1
fi

# Write Runtime.availableProcessors to a log file
${TESTJAVA}/bin/java ${TESTVMOPTS} -cp ${TESTCLASSES} $SRCFILEBASE > $LOGFILE 2>&1
status=$?
if [ ! $status -eq "0" ]; then
  echo "Test FAILED: $SRCFILE";
  exit 1
fi

# Validate output from GetAvailableProcessors.java
grep -Po "Runtime\\.availableProcessors: \\d+" $LOGFILE
status=$?
if [ ! $status -eq "0" ]; then
  echo "TESTBUG: $SRCFILE did not output a processor count.";
  exit 1
fi

# Write SYSTEM_INFO.dwNumberOfProcessors to a log file
GETPROCINFONAME=GetSystemInfo
GETPROCINFOLOG="${TESTCLASSES}/$GETPROCINFONAME.output.log"
${TESTNATIVEPATH}/$GETPROCINFONAME > $GETPROCINFOLOG 2>&1

# Validate output from GetSystemInfo.exe
grep -Po "dwNumberOfProcessors: \\d+" $GETPROCINFOLOG
status=$?
if [ ! $status -eq "0" ]; then
  echo "TESTBUG: $GETPROCINFONAME did not output a processor count.";
  exit 1
fi

# Write each processor count to a file
JAVAPROCS="${TESTCLASSES}/processor_count_java.txt"
NATIVEPROCS="${TESTCLASSES}/processor_count_native.txt"
grep -Po "Runtime\\.availableProcessors: \\d+" $LOGFILE | sed -e 's/[a-zA-Z: \.]//g' > $JAVAPROCS 2>&1
grep -Po "dwNumberOfProcessors: \\d+" $GETPROCINFOLOG   | sed -e 's/[a-zA-Z: \.]//g' > $NATIVEPROCS 2>&1

# Ensure the processor counts are identical
runtimeAvailableProcessors=$(<$JAVAPROCS)
dwNumberOfProcessors=$(<$NATIVEPROCS)

echo "java.lang.Runtime.availableProcessors: $runtimeAvailableProcessors"
echo "SYSTEM_INFO.dwNumberOfProcessors:      $dwNumberOfProcessors"

if [ "$VAR1" != "$VAR2" ]; then
  echo "Test failed: Runtime.availableProcessors ($runtimeAvailableProcessors) != dwNumberOfProcessors ($dwNumberOfProcessors)"
  exit 1
else
  echo "Test passed."
fi
