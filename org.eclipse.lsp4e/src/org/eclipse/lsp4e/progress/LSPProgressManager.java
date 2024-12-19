/*******************************************************************************
 * Copyright (c) 2022, 2023 Avaloq Evolution AG.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Rubén Porras Campo (Avaloq Evolution AG) - initial implementation
 *******************************************************************************/
package org.eclipse.lsp4e.progress;

import static org.eclipse.lsp4e.internal.NullSafetyHelper.castNullable;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.lsp4e.LanguageServerPlugin;
import org.eclipse.lsp4e.LanguageServersRegistry.LanguageServerDefinition;
import org.eclipse.lsp4e.ui.Messages;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressCancelParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressKind;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.services.LanguageServer;

public class LSPProgressManager {
	private final ConcurrentMap<String, BlockingQueue<ProgressParams>> progressMap;
	private final ConcurrentMap<IProgressMonitor, Integer> percentageMap;
	private @Nullable LanguageServer languageServer;
	private @Nullable LanguageServerDefinition languageServerDefinition;
	private final Set<String> done;
	private final Set<Job> jobs;

	public LSPProgressManager() {
		this.progressMap = new ConcurrentHashMap<>();
		this.percentageMap = new ConcurrentHashMap<>();
		this.done = new ConcurrentSkipListSet<>();
		this.jobs = new ConcurrentSkipListSet<>();
	}

	public void connect(final LanguageServer languageServer, LanguageServerDefinition languageServerDefinition) {
		this.languageServer = languageServer;
		this.languageServerDefinition = languageServerDefinition;
	}
	/**
	 * Creates the progress.
	 *
	 * @param params
	 *            the {@link WorkDoneProgressCreateParams} to be used to create the progress
	 * @return the completable future
	 */
	public CompletableFuture<Void> createProgress(final WorkDoneProgressCreateParams params) {
		final var queue = new LinkedBlockingDeque<ProgressParams>();

		String jobIdentifier = params.getToken().map(Function.identity(), Object::toString);
		BlockingQueue<ProgressParams> oldQueue = progressMap.put(jobIdentifier, queue);
		if (oldQueue != null) {
			LanguageServerPlugin.logInfo(
					"Old progress with identifier " + jobIdentifier + " discarded due to new create progress request"); //$NON-NLS-1$//$NON-NLS-2$
		}
		createJob(queue, jobIdentifier);
		return CompletableFuture.completedFuture(null);
	}

	private void createJob(final LinkedBlockingDeque<ProgressParams> queue, final String jobIdentifier) {
		final var languageServer = this.languageServer;
		final var languageServerDefinition = this.languageServerDefinition;

		final var jobName = languageServerDefinition == null //
				|| languageServerDefinition.label.isBlank() //
				? Messages.LSPProgressManager_BackgroundJobName
				: languageServerDefinition.label;
		Job job = Job.create(jobName, (ICoreRunnable) mon -> {
			final var monitor = mon == null ? new NullProgressMonitor() : mon;
			try {
				while (true) {
					if (monitor.isCanceled()) {
						progressMap.remove(jobIdentifier);
						percentageMap.remove(monitor);
						if (languageServer != null) {
							final var workDoneProgressCancelParams = new WorkDoneProgressCancelParams();
							workDoneProgressCancelParams.setToken(jobIdentifier);
							languageServer.cancelProgress(workDoneProgressCancelParams);
						}
						throw new OperationCanceledException();
					}
					ProgressParams nextProgressNotification = castNullable(queue.pollFirst(1, TimeUnit.SECONDS));
					if (nextProgressNotification != null ) {
						WorkDoneProgressNotification progressNotification = nextProgressNotification.getValue().getLeft();
						if (progressNotification != null) {
							WorkDoneProgressKind kind = progressNotification.getKind();
							if (kind == WorkDoneProgressKind.begin) {
								begin((WorkDoneProgressBegin) progressNotification, monitor);
							} else if (kind == WorkDoneProgressKind.report) {
								report((WorkDoneProgressReport) progressNotification, monitor);
							} else if (kind == WorkDoneProgressKind.end) {
								end((WorkDoneProgressEnd) progressNotification, monitor);
								progressMap.remove(jobIdentifier);
								percentageMap.remove(monitor);
								return;
							}
						}
					} else if (done.remove(jobIdentifier)) {
						monitor.done();
					}
				}
			} catch (InterruptedException e) {
				LanguageServerPlugin.logError(e);
				Thread.currentThread().interrupt();
			}
		});
		jobs.add(job);
		Job.getJobManager().addJobChangeListener(new JobChangeAdapter()  {
			@Override
			public void done(IJobChangeEvent event) {
				jobs.remove(event.getJob());
			}
		});
		job.schedule();
	}

	private void begin(final WorkDoneProgressBegin begin, final IProgressMonitor monitor) {
		Integer percentage = begin.getPercentage();
		if (percentage != null) {
			if (percentage == 0) {
				monitor.beginTask(begin.getTitle(), 100);
			} else {
				monitor.beginTask(begin.getTitle(), percentage);
			}
			percentageMap.put(monitor, 0);
		} else {
			monitor.beginTask(begin.getTitle(), IProgressMonitor.UNKNOWN);
		}

		String message = begin.getMessage();
		if (message != null && !message.isBlank()) {
			monitor.subTask(message);
		}
	}

	private void end(final WorkDoneProgressEnd end, final IProgressMonitor monitor) {
		monitor.subTask(end.getMessage());
		monitor.done();
	}

	private void report(final WorkDoneProgressReport report, final IProgressMonitor monitor) {
		final var reportMessage = report.getMessage();
		if (reportMessage != null && !reportMessage.isBlank()) {
			monitor.subTask(reportMessage);
		}

		final var progressPercentage = report.getPercentage();
		if (progressPercentage != null) {
			percentageMap.compute(monitor, (key, existingPercentage) -> {
				final int worked = (existingPercentage != null) ? Math.min(existingPercentage, progressPercentage) : 0;
				monitor.worked(progressPercentage - worked);
				return progressPercentage;
			});
		}
	}

	/**
	 * Notify progress.
	 *
	 * @param params
	 *            the {@link ProgressParams} used for the progress notification
	 */
	public void notifyProgress(final ProgressParams params) {
		String jobIdentifier = params.getToken().map(Function.identity(), Object::toString);
		BlockingQueue<ProgressParams> progress = progressMap.get(jobIdentifier);
		if (progress != null) { // may happen if the server does not wait on the return value of the future of createProgress
			progress.add(params);
		} else {
			WorkDoneProgressNotification progressNotification = params.getValue().getLeft();
			if (progressNotification != null && progressNotification.getKind() == WorkDoneProgressKind.end) {
				done.add(jobIdentifier);
			}
		}
	}

	/**
	 * Dispose the progress manager.
	 */
	public void dispose() {
		jobs.forEach(Job::cancel);
		percentageMap.clear();
		progressMap.clear();
		done.clear();
	}
}
