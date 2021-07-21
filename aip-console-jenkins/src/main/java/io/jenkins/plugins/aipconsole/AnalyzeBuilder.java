package io.jenkins.plugins.aipconsole;

import com.castsoftware.aip.console.tools.core.dto.ApiInfoDto;
import com.castsoftware.aip.console.tools.core.dto.ApplicationDto;
import com.castsoftware.aip.console.tools.core.dto.VersionDto;
import com.castsoftware.aip.console.tools.core.dto.VersionStatus;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobExecutionDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobRequestBuilder;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobStatus;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobType;
import com.castsoftware.aip.console.tools.core.dto.jobs.LogContentDto;
import com.castsoftware.aip.console.tools.core.exceptions.ApiCallException;
import com.castsoftware.aip.console.tools.core.exceptions.ApplicationServiceException;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;
import com.castsoftware.aip.console.tools.core.services.ApplicationService;
import com.castsoftware.aip.console.tools.core.services.JobsService;
import com.castsoftware.aip.console.tools.core.services.RestApiService;
import com.castsoftware.aip.console.tools.core.utils.Constants;
import com.google.inject.Guice;
import com.google.inject.Injector;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.Secret;
import io.jenkins.plugins.aipconsole.config.AipConsoleGlobalConfiguration;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.jenkins.plugins.aipconsole.Messages.AnalyzeBuilder_Analyze_error_appGuid;
import static io.jenkins.plugins.aipconsole.Messages.AnalyzeBuilder_Analyze_error_noVersionFound;
import static io.jenkins.plugins.aipconsole.Messages.AnalyzeBuilder_Analyze_error_noVersionFoundWithName;
import static io.jenkins.plugins.aipconsole.Messages.AnalyzeBuilder_DescriptorImpl_displayName;
import static io.jenkins.plugins.aipconsole.Messages.JobsSteps_changed;

public class AnalyzeBuilder extends BaseActionBuilder implements SimpleBuildStep {
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
    private String versionName;
    private boolean failureIgnored = false;
    private long timeout = Constants.DEFAULT_HTTP_TIMEOUT;
    private boolean withSnapshot = false;
    private boolean processImaging = false;

    @DataBoundConstructor
    public AnalyzeBuilder(@CheckForNull String applicationName) {
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
    public String getVersionName() {
        return versionName;
    }

    @DataBoundSetter
    public void setVersionName(@Nullable String versionName) {
        this.versionName = versionName;
    }

    public boolean isFailureIgnored() {
        return failureIgnored;
    }

    @DataBoundSetter
    public void setFailureIgnored(boolean failureIgnored) {
        this.failureIgnored = failureIgnored;
    }

    public boolean isWithSnapshot() {
        return withSnapshot;
    }

    @DataBoundSetter
    public void setWithSnapshot(boolean withSnapshot) {
        this.withSnapshot = withSnapshot;
    }

    public boolean isProcessImaging() {
        return processImaging;
    }

    @DataBoundSetter
    public void setProcessImaging(boolean processImaging) {
        this.processImaging = processImaging;
    }

    public long getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public AnalyzeDescriptorImpl getDescriptor() {
        return (AnalyzeDescriptorImpl) super.getDescriptor();
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

        String apiServerUrl = getAipConsoleUrl();
        String apiKey = Secret.toString(getApiKey());
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
        EnvVars vars = run.getEnvironment(listener);
        String expandedAppName = vars.expand(applicationName);
        String caipVersion = null;

        try {
            ApplicationDto app = applicationService.getApplicationFromName(expandedAppName);
            applicationGuid = app.getGuid();
            caipVersion = app.getCaipVersion();
        } catch (ApplicationServiceException e) {
            listener.error(AnalyzeBuilder_Analyze_error_appGuid());
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
            return;
        }
        String jobGuid = null;

        try {
            String resolvedVersionName = vars.expand(versionName);
            ApiInfoDto apiInfoDto = apiService.getAipConsoleApiInfo();
            Set<VersionDto> versions = applicationService.getApplicationVersion(applicationGuid);
            // Get the version name
            VersionDto versionToAnalyze;
            // Version with name provided
            if (StringUtils.isNotBlank(resolvedVersionName)) {
                versionToAnalyze = versions.stream().filter(v -> StringUtils.equalsAnyIgnoreCase(v.getName(), resolvedVersionName)).findFirst().orElse(null);
            } else {
                // Latest Delivered Version
                versionToAnalyze = versions
                        .stream()
                        .filter(v -> v.getStatus().ordinal() >= VersionStatus.DELIVERED.ordinal())
                        .max(Comparator.comparing(VersionDto::getVersionDate)).orElse(null);
            }
            if (versionToAnalyze == null) {
                String message = StringUtils.isNotBlank(resolvedVersionName) ?
                        AnalyzeBuilder_Analyze_error_noVersionFoundWithName(resolvedVersionName, expandedAppName) :
                        AnalyzeBuilder_Analyze_error_noVersionFound();
                listener.error(message);
                run.setResult(defaultResult);
                return;
            }

            JobRequestBuilder requestBuilder = JobRequestBuilder.newInstance(applicationGuid, null, JobType.ANALYZE, caipVersion)
                    .startStep(versionToAnalyze.getStatus() == VersionStatus.DELIVERED ? Constants.ACCEPTANCE_STEP_NAME : Constants.ANALYZE);


            if (withSnapshot) {
                requestBuilder.processImaging(processImaging)
                        .endStep(apiInfoDto.isLastStepConsolidateSnapshot() ?
                                Constants.CONSOLIDATE_SNAPSHOT :
                                Constants.UPLOAD_APP_SNAPSHOT)
                        .snapshotName(String.format("Snapshot-%s", new
                                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").format(new Date())))
                        .uploadApplication(true);
            } else {
                requestBuilder.endStep(Constants.ANALYZE);
            }
            requestBuilder.versionName(versionToAnalyze.getName())
                    .versionGuid(versionToAnalyze.getGuid())
                    .releaseAndSnapshotDate(new Date());


            jobGuid = jobsService.startJob(requestBuilder);

            log.println(Messages.AnalyzeBuilder_Analyze_info_pollJobMessage());
            JobState state = pollJob(jobGuid, log);
            if (state != JobState.COMPLETED) {
                listener.error(Messages.AnalyzeBuilder_Analyze_error_jobFailure(state.toString()));
                run.setResult(defaultResult);
            } else {
                log.println(Messages.AnalyzeBuilder_Analyze_success_analysisComplete());
                run.setResult(Result.SUCCESS);
            }
        } catch (ApplicationServiceException e) {
            listener.error(Messages.AnalyzeBuilder_Analyze_error_appServiceException());
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
        } catch (JobServiceException e) {
            // Should we check if the original cause is an InterruptedException and attempt to cancel the job ?
            if (e.getCause() != null && e.getCause() instanceof InterruptedException) {
                if (jobGuid != null) {
                    run.setResult(Result.ABORTED);
                    log.println("Attempting to cancel Analysis job on AIP Console, following cancellation of the build.");
                    try {
                        jobsService.cancelJob(jobGuid);
                        log.println("Job was successfully cancelled on AIP Console.");
                    } catch (JobServiceException jse) {
                        log.println("Could not cancel the job on AIP Console, please cancel it manually. Error was : " + e.getMessage());
                    }
                }
            } else {
                listener.error(Messages.AnalyzeBuilder_Analyze_error_jobServiceException());
                e.printStackTrace(listener.getLogger());
                run.setResult(defaultResult);
            }
        }
    }

    private JobState pollJob(String jobGuid, PrintStream log) throws JobServiceException {
        return jobsService.pollAndWaitForJobFinished(jobGuid,
                jobStatusWithSteps -> log.println(
                        jobStatusWithSteps.getAppName() + " - " +
                                JobsSteps_changed(JobStepTranslationHelper.getStepTranslation(jobStatusWithSteps.getCurrentStep()))
                ),
                getPollingCallback(log),
                JobExecutionDto::getState);
    }

    private Consumer<LogContentDto> getPollingCallback(PrintStream log) {
        return !getDescriptor().configuration.isVerbose() ? null :
                logContentDto -> {
                    logContentDto.getLines().forEach(logLine -> log.println(logLine.getContent()));
                };
    }


    /**
     * Check some initial elements before running the Job
     *
     * @return The error message based on the issue that was found, null if no issue was found
     */
    private String checkJobParameters() {
        if (StringUtils.isAnyBlank(applicationName)) {
            return Messages.GenericError_error_missingRequiredParameters();
        }
        String apiServerUrl = getAipConsoleUrl();
        String apiKey = Secret.toString(getApiKey());

        if (StringUtils.isBlank(apiServerUrl)) {
            return Messages.GenericError_error_noServerUrl();
        }
        if (StringUtils.isBlank(apiKey)) {
            return Messages.GenericError_error_noApiKey();
        }

        return null;
    }

    @Symbol("aipAnalyze")
    @Extension
    public static final class AnalyzeDescriptorImpl extends BaseActionBuilderDescriptor {

        @Inject
        private AipConsoleGlobalConfiguration configuration;

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return AnalyzeBuilder_DescriptorImpl_displayName();
        }

        @Override
        public String getAipConsoleUrl() {
            return configuration.getAipConsoleUrl();
        }

        @Override
        public Secret getAipConsoleSecret() {
            return configuration.getApiKey();
        }

        @Override
        public String getAipConsoleUsername() {
            return configuration.getUsername();
        }

        @Override
        public int getTimeout() {
            return configuration.getTimeout();
        }
    }
}
