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
package org.hisp.dhis.validation;

import static java.lang.String.format;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.hisp.dhis.scheduling.JobProgress;

/**
 * Forwards all progress to the given progress and remembers whether any work item, stage or process
 * failed. Validation analysis skips failing org unit chunks and otherwise returns partial results
 * that look complete; replacing persisted results from such a run would delete violations that were
 * never evaluated.
 */
class FailureTrackingProgress implements JobProgress {
  private final JobProgress delegate;

  private final List<String> failures = new CopyOnWriteArrayList<>();

  FailureTrackingProgress(JobProgress delegate) {
    this.delegate = delegate;
  }

  boolean hasFailed() {
    return !failures.isEmpty();
  }

  @CheckForNull
  String firstFailure() {
    return failures.isEmpty() ? null : failures.get(0);
  }

  private void record(String error, Object... args) {
    failures.add(String.valueOf(args.length == 0 ? error : format(error, args)));
  }

  @Override
  public boolean isCancelled() {
    return delegate.isCancelled();
  }

  @Override
  public boolean isSkipCurrentStage() {
    return delegate.isSkipCurrentStage();
  }

  @Override
  public void startingProcess(@CheckForNull String description, Object... args) {
    delegate.startingProcess(description, args);
  }

  @Override
  public void completedProcess(@CheckForNull String summary, Object... args) {
    delegate.completedProcess(summary, args);
  }

  @Override
  public void failedProcess(@CheckForNull String error, Object... args) {
    record(String.valueOf(error), args);
    delegate.failedProcess(error, args);
  }

  @Override
  public void startingStage(
      @Nonnull String description, int workItems, @Nonnull FailurePolicy onFailure) {
    delegate.startingStage(description, workItems, onFailure);
  }

  @Override
  public void completedStage(@CheckForNull String summary, Object... args) {
    delegate.completedStage(summary, args);
  }

  @Override
  public void failedStage(@Nonnull String error, Object... args) {
    record(error, args);
    delegate.failedStage(error, args);
  }

  @Override
  public void startingWorkItem(@Nonnull String description, @Nonnull FailurePolicy onFailure) {
    delegate.startingWorkItem(description, onFailure);
  }

  @Override
  public void completedWorkItem(@CheckForNull String summary, Object... args) {
    delegate.completedWorkItem(summary, args);
  }

  @Override
  public void failedWorkItem(@Nonnull String error, Object... args) {
    record(error, args);
    delegate.failedWorkItem(error, args);
  }
}
