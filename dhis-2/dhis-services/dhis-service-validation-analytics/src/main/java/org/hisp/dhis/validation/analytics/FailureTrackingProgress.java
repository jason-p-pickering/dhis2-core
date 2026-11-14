/*
 * Copyright (c) 2004-2026, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors 
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.validation.analytics;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hisp.dhis.scheduling.JobProgress;

/**
 * Progress handed to the validation analysis. It records any failed work item or stage, because the
 * analysis skips failing org unit chunks and would otherwise return partial results that look
 * complete. Tracking calls are not forwarded to the job's progress (the job tracks one work item
 * per period itself); cancellation is forwarded so a cancel takes effect inside a period.
 */
class FailureTrackingProgress implements JobProgress {

  private final JobProgress job;
  private final List<String> failures = new CopyOnWriteArrayList<>();

  FailureTrackingProgress(JobProgress job) {
    this.job = job;
  }

  boolean hasFailed() {
    return !failures.isEmpty();
  }

  /** The first recorded failure, or null if none. */
  String firstFailure() {
    return failures.isEmpty() ? null : failures.get(0);
  }

  @Override
  public boolean isCancelled() {
    return job.isCancelled();
  }

  @Override
  public boolean isSkipCurrentStage() {
    return job.isSkipCurrentStage();
  }

  @Override
  public void failedStage(String error, Object... args) {
    failures.add(String.valueOf(format(error, args)));
  }

  @Override
  public void failedWorkItem(String error, Object... args) {
    failures.add(String.valueOf(format(error, args)));
  }

  @Override
  public void startingProcess(String description, Object... args) {
    // not tracked
  }

  @Override
  public void completedProcess(String summary, Object... args) {
    // not tracked
  }

  @Override
  public void failedProcess(String error, Object... args) {
    failures.add(String.valueOf(format(error, args)));
  }

  @Override
  public void startingStage(String description, int workItems, FailurePolicy onFailure) {
    // not tracked
  }

  @Override
  public void completedStage(String summary, Object... args) {
    // not tracked
  }

  @Override
  public void startingWorkItem(String description, FailurePolicy onFailure) {
    // not tracked
  }

  @Override
  public void completedWorkItem(String summary, Object... args) {
    // not tracked
  }
}
