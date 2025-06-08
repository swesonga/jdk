/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SERIAL_GCPAUSETIMEINFO_HPP
#define SHARE_GC_SERIAL_GCPAUSETIMEINFO_HPP

#include "memory/allocation.hpp"

class GcPauseTimeInfo: public CHeapObj<mtGC> {
private:
  double _start_time;
  double _duration;
  double _end_of_last_pause;

public:
  GcPauseTimeInfo(double pause_start_time,
                  double pause_duration,
                  double end_of_last_pause)
      : _start_time(pause_start_time),
        _duration(pause_duration),
        _end_of_last_pause(end_of_last_pause) {
    assert(pause_start_time >= 0, "pause_start_time must not be negative");
    assert(pause_duration >= 0, "pause_duration must not be negative");
    assert(end_of_last_pause >= 0, "end_of_last_pause must not be negative");
  }

  double end_of_last_pause() const {
    return _end_of_last_pause;
  }

  double set_end_of_last_pause(double end_of_last_pause) {
    return _end_of_last_pause = end_of_last_pause;
  }

  double start_time() const {
    return _start_time;
  }

  double set_start_time(double start_time) {
    return _start_time = start_time;
  }

  double duration() const {
    return _duration;
  }

  double set_duration(double duration) {
    return _duration = duration;
  }

  double preceding_nongc_duration() const {
    return _start_time - _end_of_last_pause;
  }

  double total_duration() const {
    return preceding_nongc_duration() + _duration;
  }

  double pause_end_time() const {
    return _start_time + _duration;
  }
};

#endif // SHARE_GC_SERIAL_GCPAUSETIMEINFO_HPP
