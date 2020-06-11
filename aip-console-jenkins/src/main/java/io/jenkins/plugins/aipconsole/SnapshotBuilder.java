package io.jenkins.plugins.aipconsole;

import com.castsoftware.aip.console.tools.core.dto.VersionDto;
import com.castsoftware.aip.console.tools.core.dto.VersionStatus;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobRequestBuilder;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobStatus;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobType;
import com.castsoftware.aip.console.tools.core.exceptions.ApiCallException;
import com.castsoftware.aip.console.tools.core.exceptions.ApplicationServiceException;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;
import com.castsoftware.aip.console.tools.core.services.ApplicationService;
import com.castsoftware.aip.console.tools.core.services.JobsService;
import com.castsoftware.aip.console.tools.core.services.RestApiService;
import com.castsoftware.aip.console.tools.core.utils.Constants;
import com.google.inject.Guice;
import com.google.inject.Injector;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.Secret;
import io.jenkins.plugins.aipconsole.config.AipConsoleGlobalConfiguration;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_appCreateError;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_jobFailure;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_jobServiceException;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_info_pollJobMessage;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_success_analysisComplete;
import static io.jenkins.plugins.aipconsole.Messages.JobsSteps_changed;
import static io.jenkins.plugins.aipconsole.Messages.SnapshotBuilder_DescriptorImpl_displayName;

public class SnapshotBuilder extends Builder implements SimpleBuildStep {
    private static final DateFormat RELEASE_DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    @Inject
    private JobsService jobsService;

    @Inject
    private RestApiService apiService;

    @Inject
    private ApplicationService applicationService;

    @CheckForNull
    private String applicationName;
    @Nullable
    private String applicationGuid;
    @Nullable
    private String snapshotName;
    private boolean failureIgnored = false;
    private long timeout = Constants.DEFAULT_HTTP_TIMEOUT;

    @DataBoundConstructor
    public SnapshotBuilder(String applicationName) {
        this.applicationName = applicationName;
    }

    @CheckForNull
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(@CheckForNull String applicationName) {
        this.applicationName = applicationName;
    }

    @Nullable
    public String getApplicationGuid() {
        return applicationGuid;
    }

    public void setApplicationGuid(@Nullable String applicationGuid) {
        this.applicationGuid = applicationGuid;
    }

    @Nullable
    public String getSnapshotName() {
        return snapshotName;
    }

    public void setSnapshotName(@Nullable String snapshotName) {
        this.snapshotName = snapshotName;
    }

    public boolean isFailureIgnored() {
        return failureIgnored;
    }

    public void setFailureIgnored(boolean failureIgnored) {
        this.failureIgnored = failureIgnored;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public SnapshotDescriptorImpl getDescriptor() {
        return (SnapshotDescriptorImpl) super.getDescriptor();
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream log = listener.getLogger();
        Result defaultResult = failureIgnored ? Result.UNSTABLE : Result.FAILURE;

        String errorMessage;
        if ((errorMessage = checkJobParameters()) != null) {
            listener.error(errorMessage);
            run.setResult(Result.NOT_BUILT);
            return;
        }

        // Check the services have been properly initialized
        if (!ObjectUtils.allNotNull(apiService, jobsService, applicationService)) {
            // Manually setup Guice Injector using Module (Didn't find any way to make this automatically)
            Injector injector = Guice.createInjector(new AipConsoleModule());
            // Guice can automatically inject those, but then findbugs, not seeing the change,
            // will fail the build considering they will provoke an NPE
            // So, to avoid this, set them explicitly (if they were not set)
            apiService = injector.getInstance(RestApiService.class);
            jobsService = injector.getInstance(JobsService.class);
            applicationService = injector.getInstance(ApplicationService.class);
        }

        String apiServerUrl = getDescriptor().getAipConsoleUrl();
        String apiKey = Secret.toString(getDescriptor().getAipConsoleSecret());
        String username = getDescriptor().getAipConsoleUsername();
        // Job level timeout different from default ? use it, else use the global config level timeout
        long actualTimeout = (timeout != Constants.DEFAULT_HTTP_TIMEOUT ? timeout : getDescriptor().getTimeout());

        try {
            // update timeout of HTTP Client if different from default
            if (actualTimeout != Constants.DEFAULT_HTTP_TIMEOUT) {
                apiService.setTimeout(actualTimeout, TimeUnit.SECONDS);
            }
            // Authentication (if username is null or empty, we'll authenticate with api key
            apiService.validateUrlAndKey(apiServerUrl, username, apiKey);
        } catch (ApiCallException e) {
            listener.error(Messages.GenericError_error_accessDenied(apiServerUrl));
            run.setResult(defaultResult);
            return;
        }

        try {
            applicationGuid = applicationService.getApplicationGuidFromName(applicationName);
        } catch (ApplicationServiceException e) {
            listener.error(AddVersionBuilder_AddVersion_error_appCreateError(applicationName));
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
            return;
        }

        try {
            Set<VersionDto> versions = applicationService.getApplicationVersion(applicationGuid);
            // Get the current version's guid
            VersionDto versionToAnalyze = versions.stream().filter(VersionDto::isCurrentVersion)
                    .findFirst()
                    .orElse(null);
            if (versionToAnalyze == null) {
                //FIXME change error message
                listener.error(AddVersionBuilder_AddVersion_error_jobServiceException());
                run.setResult(defaultResult);
                return;
            }
            if (versionToAnalyze.getStatus().ordinal() < VersionStatus.ANALYSIS_DONE.ordinal()) {
                listener.error("Current version was not analyzed yet. Cannot create Snapshot");
                run.setResult(defaultResult);
                return;
            }
            if (StringUtils.isBlank(snapshotName)) {
                snapshotName = RELEASE_DATE_FORMATTER.format(new Date());
            }
            JobRequestBuilder requestBuilder = JobRequestBuilder.newInstance(applicationGuid, null, JobType.ANALYZE)
                    .startStep(versionToAnalyze.getStatus() == VersionStatus.DELIVERED ? Constants.ACCEPTANCE_STEP_NAME : Constants.ANALYZE)
                    .versionGuid(versionToAnalyze.getGuid())
                    .versionName(versionToAnalyze.getName())
                    .snapshotName(snapshotName)
                    .releaseAndSnapshotDate(new Date());

            String jobGuid = jobsService.startJob(requestBuilder);

            log.println(AddVersionBuilder_AddVersion_info_pollJobMessage());
            JobState state = pollJob(jobGuid, log);
            if (state != JobState.COMPLETED) {
                listener.error(AddVersionBuilder_AddVersion_error_jobFailure(state.toString()));
                run.setResult(defaultResult);
            } else {
                log.println(AddVersionBuilder_AddVersion_success_analysisComplete());
                run.setResult(Result.SUCCESS);
            }
        } catch (ApplicationServiceException e) {
            // FIXME: change error messages
            listener.error(AddVersionBuilder_AddVersion_error_jobServiceException());
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
        } catch (JobServiceException e) {
            listener.error(AddVersionBuilder_AddVersion_error_jobServiceException());
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
        }
    }

    private JobState pollJob(String jobGuid, PrintStream log) throws JobServiceException {
        return jobsService.pollAndWaitForJobFinished(jobGuid,
                jobStatusWithSteps -> log.println(
                        jobStatusWithSteps.getAppName() + " - " +
                                JobsSteps_changed(JobStepTranslationHelper.getStepTranslation(jobStatusWithSteps.getProgressStep()))
                ),
                JobStatus::getState);
    }

    /**
     * Check some initial elements before running the Job
     *
     * @return The error message based on the issue that was found, null if no issue was found
     */
    private String checkJobParameters() {
        if (StringUtils.isBlank(applicationName)) {
            return Messages.GenericError_error_missingRequiredParameters();
        }
        String apiServerUrl = getDescriptor().getAipConsoleUrl();
        String apiKey = Secret.toString(getDescriptor().getAipConsoleSecret());

        if (StringUtils.isBlank(apiServerUrl)) {
            return Messages.GenericError_error_noServerUrl();
        }
        if (StringUtils.isBlank(apiKey)) {
            return Messages.GenericError_error_noApiKey();
        }

        return null;
    }

    @Symbol("aipSnapshot")
    @Extension
    public static final class SnapshotDescriptorImpl extends BuildStepDescriptor<Builder> {

        @Inject
        private AipConsoleGlobalConfiguration configuration;

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return SnapshotBuilder_DescriptorImpl_displayName();
        }

        public String getAipConsoleUrl() {
            return configuration.getAipConsoleUrl();
        }

        public Secret getAipConsoleSecret() {
            return configuration.getApiKey();
        }

        public String getAipConsoleUsername() {
            return configuration.getUsername();
        }

        public int getTimeout() {
            return configuration.getTimeout();
        }
    }
}
