package io.jenkins.plugins.aipconsole;

import com.castsoftware.aip.console.tools.core.dto.ApiInfoDto;
import com.castsoftware.aip.console.tools.core.dto.ApplicationDto;
import com.castsoftware.aip.console.tools.core.dto.NodeDto;
import com.castsoftware.aip.console.tools.core.dto.VersionDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.FileCommandRequest;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobExecutionDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobRequestBuilder;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobStatus;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobType;
import com.castsoftware.aip.console.tools.core.dto.jobs.LogContentDto;
import com.castsoftware.aip.console.tools.core.exceptions.ApiCallException;
import com.castsoftware.aip.console.tools.core.exceptions.ApplicationServiceException;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;
import com.castsoftware.aip.console.tools.core.exceptions.PackagePathInvalidException;
import com.castsoftware.aip.console.tools.core.exceptions.UploadException;
import com.castsoftware.aip.console.tools.core.services.ApplicationService;
import com.castsoftware.aip.console.tools.core.services.JobsService;
import com.castsoftware.aip.console.tools.core.services.RestApiService;
import com.castsoftware.aip.console.tools.core.services.UploadService;
import com.castsoftware.aip.console.tools.core.utils.Constants;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_appCreateError;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_appNotFound;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_fileNotFound;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_jobFailure;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_jobServiceException;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_nodeNotFound;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_uploadFailed;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_info_appNotFoundAutoCreate;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_info_noVersionAvailable;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_info_pollJobMessage;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_info_startUpload;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_success_analysisComplete;
import static io.jenkins.plugins.aipconsole.Messages.CreateApplicationBuilder_CreateApplication_error_jobServiceException;
import static io.jenkins.plugins.aipconsole.Messages.DeliverBuilder_Deliver_info_startDeliverCloneJob;
import static io.jenkins.plugins.aipconsole.Messages.DeliverBuilder_DescriptorImpl_displayName;
import static io.jenkins.plugins.aipconsole.Messages.GenericError_error_accessDenied;
import static io.jenkins.plugins.aipconsole.Messages.GenericError_error_missingRequiredParameters;
import static io.jenkins.plugins.aipconsole.Messages.GenericError_error_noApiKey;
import static io.jenkins.plugins.aipconsole.Messages.GenericError_error_noServerUrl;
import static io.jenkins.plugins.aipconsole.Messages.JobsSteps_changed;

public class DeliverBuilder extends BaseActionBuilder implements SimpleBuildStep {
    public static final int BUFFER_SIZE = 10 * 1024 * 1024;
    @Inject
    private JobsService jobsService;

    @Inject
    private UploadService uploadService;

    @Inject
    private RestApiService apiService;

    @Inject
    private ApplicationService applicationService;

    private String applicationName;
    private String applicationGuid;
    private String filePath;
    private boolean autoCreate = false;
    private boolean cloneVersion = false;
    @Nullable
    private String versionName = "";
    private long timeout = Constants.DEFAULT_HTTP_TIMEOUT;
    private boolean failureIgnored = false;
    @Nullable
    private String nodeName = "";
    private boolean enableSecurityDataflow = false;
    private boolean backupApplicationEnabled = false;
    @Nullable
    private String backupName = "";
    @Nullable
    private String domainName;

    @Nullable
    private String exclusionPatterns = "";

    private boolean autoDiscover = true;
    private boolean setAsCurrent = false;

    @DataBoundConstructor
    public DeliverBuilder(String applicationName, String filePath) {
        this.applicationName = applicationName;
        this.filePath = filePath;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationGuid() {
        return applicationGuid;
    }

    @DataBoundSetter
    public void setApplicationGuid(String applicationGuid) {
        this.applicationGuid = applicationGuid;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public boolean isAutoCreate() {
        return autoCreate;
    }

    @DataBoundSetter
    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public boolean isCloneVersion() {
        return cloneVersion;
    }

    @DataBoundSetter
    public void setCloneVersion(boolean cloneVersion) {
        this.cloneVersion = cloneVersion;
    }

    public boolean isAutoDiscover() {
        return autoDiscover;
    }

    public boolean getAutoDiscover() {
        return isAutoDiscover();
    }

    @DataBoundSetter
    public void setAutoDiscover(boolean autoDiscover) {
        this.autoDiscover = autoDiscover;
    }

    public boolean isSetAsCurrent() {
        return setAsCurrent;
    }

    public boolean getSetAsCurrent() {
        return isSetAsCurrent();
    }

    @DataBoundSetter
    public void setSetAsCurrent(boolean setAsCurrent) {
        this.setAsCurrent = setAsCurrent;
    }

    @Nullable
    public String getExclusionPatterns() {
        return exclusionPatterns;
    }

    @DataBoundSetter
    public void setExclusionPatterns(@Nullable String exclusionPatterns) {
        this.exclusionPatterns = exclusionPatterns;
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

    public long getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Nullable
    public String getNodeName() {
        return nodeName;
    }

    @DataBoundSetter
    public void setNodeName(@Nullable String nodeName) {
        this.nodeName = nodeName;
    }

    public boolean isEnableSecurityDataflow() {
        return enableSecurityDataflow;
    }

    @DataBoundSetter
    public void setEnableSecurityDataflow(boolean enableSecurityDataflow) {
        this.enableSecurityDataflow = enableSecurityDataflow;
    }

    public boolean isBackupApplicationEnabled() {
        return backupApplicationEnabled;
    }

    @DataBoundSetter
    public void setBackupApplicationEnabled(boolean backupApplicationEnabled) {
        this.backupApplicationEnabled = backupApplicationEnabled;
    }

    @Nullable
    public String getBackupName() {
        return backupName;
    }

    @DataBoundSetter
    public void setBackupName(String backupName) {
        this.backupName = backupName;
    }

    @Nullable
    public String getDomainName() {
        return domainName;
    }

    @DataBoundSetter
    public void setDomainName(@Nullable String domainName) {
        this.domainName = domainName;
    }

    @Override
    public DeliverDescriptorImpl getDescriptor() {
        return (DeliverDescriptorImpl) super.getDescriptor();
    }


    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        PrintStream log = listener.getLogger();
        Result defaultResult = failureIgnored ? Result.UNSTABLE : Result.FAILURE;
        boolean applicationHasVersion = this.cloneVersion;
        boolean isUpload = false;

        String errorMessage;
        if ((errorMessage = checkJobParameters()) != null) {
            listener.error(errorMessage);
            run.setResult(Result.NOT_BUILT);
            return;
        }

        // Check the services have been properly initialized
        if (!ObjectUtils.allNotNull(apiService, uploadService, jobsService, applicationService)) {
            // Manually setup Guice Injector using Module (Didn't find any way to make this automatically)
            Injector injector = Guice.createInjector(new AipConsoleModule());
            // Guice can automatically inject those, but then findbugs, not seeing the change,
            // will fail the build considering they will provoke an NPE
            // So, to avoid this, set them explicitly (if they were not set)
            apiService = injector.getInstance(RestApiService.class);
            uploadService = injector.getInstance(UploadService.class);
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
            listener.error(GenericError_error_accessDenied(apiServerUrl));
            run.setResult(defaultResult);
            return;
        }

        EnvVars vars = run.getEnvironment(listener);
        String expandedAppName = vars.expand(applicationName);
        ApiInfoDto apiInfoDto = apiService.getAipConsoleApiInfo();
        boolean inplaceMode = false;
        try {
            ApplicationDto app = applicationService.getApplicationFromName(expandedAppName);
            inplaceMode = app == null ? false : app.isInPlaceMode();
            applicationGuid = app == null ? null : app.getGuid();
        } catch (ApplicationServiceException e) {
            listener.error(AddVersionBuilder_AddVersion_error_appCreateError(expandedAppName));
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
            return;
        }

        String resolvedFilePath = vars.expand(filePath);

        // inplace mode only allows the folders
        if (inplaceMode && Files.isRegularFile(Paths.get(resolvedFilePath))) {
            listener.error("The application is created in \"Inplace\" mode, only folder path is allowed to deliver in this mode.");
            run.setResult(Result.NOT_BUILT);
            return;
        }
        
        String fileExt = com.castsoftware.aip.console.tools.core.utils.FilenameUtils.getFileExtension(resolvedFilePath);
        FilePath workspaceFile = null;
        // Local file
        if (StringUtils.equalsAnyIgnoreCase(fileExt, "zip", "tgz", "tar.gz")) {
            workspaceFile = workspace.child(resolvedFilePath);
            isUpload = true;
            if (!workspaceFile.exists()) {
                listener.error(AddVersionBuilder_AddVersion_error_fileNotFound(resolvedFilePath));
                run.setResult(Result.NOT_BUILT);
                return;
            }
        }

        String fileName = UUID.randomUUID().toString();

        try {

            // Get the GUID from AIP Console
            if (StringUtils.isBlank(applicationGuid)) {
                if (!autoCreate) {
                    listener.error(AddVersionBuilder_AddVersion_error_appNotFound(expandedAppName));
                    run.setResult(defaultResult);
                    return;
                }
                // Is there a node name
                String nodeGuid = null;
                if (StringUtils.isNotBlank(nodeName)) {
                    try {
                        nodeGuid = apiService.getForEntity("/api/nodes",
                                new TypeReference<List<NodeDto>>() {
                                }).stream()
                                .filter(n -> StringUtils.equalsIgnoreCase(n.getName(), nodeName))
                                .map(NodeDto::getGuid)
                                .findFirst()
                                .orElse(null);

                        if (StringUtils.isBlank(nodeGuid)) {
                            listener.error(AddVersionBuilder_AddVersion_error_nodeNotFound(nodeName));
                            run.setResult(defaultResult);
                            return;
                        }
                    } catch (ApiCallException e) {
                        listener.error("Unable to retrieve the node guid from the given name");
                        e.printStackTrace(log);
                        run.setResult(defaultResult);
                        return;
                    }
                }

                // Parse domain name with potential variables
                // check existence of domain first ?
                String expandedDomainName = vars.expand(domainName);
                log.println(AddVersionBuilder_AddVersion_info_appNotFoundAutoCreate(expandedAppName));
                String jobGuid = jobsService.startCreateApplication(expandedAppName, nodeGuid, expandedDomainName, inplaceMode, null,null);
                applicationGuid = jobsService.pollAndWaitForJobFinished(jobGuid,
                        jobStatusWithSteps -> log.println(JobsSteps_changed(JobStepTranslationHelper.getStepTranslation(jobStatusWithSteps.getCurrentStep()))),
                        getPollingCallback(log),
                        s -> s.getState() == JobState.COMPLETED ? s.getAppGuid() : null);
                if (StringUtils.isBlank(applicationGuid)) {
                    listener.error(CreateApplicationBuilder_CreateApplication_error_jobServiceException(expandedAppName, apiServerUrl));
                    run.setResult(defaultResult);
                    return;
                }
                // Don't clone version if we just created the application
                applicationHasVersion = false;
            }

            // If user asks for a "rescan" (i.e. clone previous version config)
            // check that there are versions on the application before launching the clone job
            if (applicationHasVersion) {
                applicationHasVersion = applicationService.applicationHasVersion(applicationGuid);
            }

            if (!isUpload) {
                // Rename the file to applicationName-versionName.ext
                log.println(AddVersionBuilder_AddVersion_info_startUpload(FilenameUtils.getName(resolvedFilePath)));

                //call api to check if the folder exists
                try {
                    FileCommandRequest fileCommandRequest = FileCommandRequest.builder().command("LS").path("SOURCES:" + Paths.get(resolvedFilePath).toString()).build();
                    apiService.postForEntity("/api/server-folders", fileCommandRequest, String.class);
                } catch (ApiCallException e) {
                    listener.error("Unable to find the file " + resolvedFilePath + " in the source.folder.location");
                    e.printStackTrace(log);
                    run.setResult(defaultResult);
                    return;
                }
                fileName = Paths.get(resolvedFilePath).toString();
                if (apiInfoDto.isSourcePathPrefixRequired()) {
                    fileName = "sources:" + fileName;
                }
            } else {
                fileName = String.format("%s.%s", fileName, fileExt);

                // if it already exists, delete it (might be a remnant of a previous execution)
                // move source file to another file name, to avoid conflicts when uploading the same zip file for multiple applications
                try (InputStream workspaceFileStream = workspaceFile.read();
                     InputStream bufferedStream = new BufferedInputStream(workspaceFileStream, BUFFER_SIZE)) {
                    log.println("Uploading file " + workspaceFile.getName());
                    if (!uploadService.uploadInputStream(applicationGuid, fileName, workspaceFile.length(), bufferedStream)) {
                        throw new UploadException("Uploading was not completed successfully.");
                    }
                    if (apiInfoDto.isExtractionRequired()) {
                        // If we have already extracted the content, the source path will be application main sources
                        fileName = applicationName + "/main_sources";
                        if (apiInfoDto.isSourcePathPrefixRequired()) {
                            fileName = "upload:" + fileName;
                        }
                    } else {
                        fileName = "upload:" + applicationName + "/" + fileName;
                    }
                }
            }
        } catch (ApplicationServiceException e) {
            listener.error(AddVersionBuilder_AddVersion_error_appCreateError(expandedAppName));
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
            return;
        } catch (UploadException e) {
            listener.error(AddVersionBuilder_AddVersion_error_uploadFailed());
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
            return;
        } catch (JobServiceException e) {
            listener.error(CreateApplicationBuilder_CreateApplication_error_jobServiceException(expandedAppName, apiServerUrl));
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
            return;
        }
        String jobGuid = null;
        try {
            // Create a value for versionName
            String resolvedVersionName = vars.expand(versionName);

            if (StringUtils.isBlank(resolvedVersionName)) {
                DateFormat formatVersionName = new SimpleDateFormat("yyMMdd.HHmmss");
                resolvedVersionName = String.format("v%s", formatVersionName.format(new Date()));
            }

            if (this.cloneVersion) {
                if (applicationHasVersion) {
                    log.println(DeliverBuilder_Deliver_info_startDeliverCloneJob(expandedAppName));
                } else {
                    log.println(AddVersionBuilder_AddVersion_info_noVersionAvailable(expandedAppName));
                }
            } else {
                log.println(DeliverBuilder_Deliver_info_startDeliverCloneJob(expandedAppName));
            }
            ApplicationDto app = applicationService.getApplicationFromName(expandedAppName);
            JobRequestBuilder requestBuilder = JobRequestBuilder.newInstance(applicationGuid, fileName, applicationHasVersion ? JobType.CLONE_VERSION : JobType.ADD_VERSION, app.getCaipVersion());
            requestBuilder.releaseAndSnapshotDate(new Date())
                    .endStep(Constants.DELIVER_VERSION)
                    .versionName(resolvedVersionName)
                    .securityObjective(enableSecurityDataflow)
                    .backupApplication(backupApplicationEnabled)
                    .backupName(backupName)
                    .autoDiscover(autoDiscover);

            if (inplaceMode || isSetAsCurrent()) {
                requestBuilder.endStep(Constants.SET_CURRENT_STEP_NAME);
            }

            log.println("Exclusion patterns : " + exclusionPatterns);
            requestBuilder.deliveryConfigGuid(applicationService.createDeliveryConfiguration(applicationGuid, fileName, exclusionPatterns, applicationHasVersion));

            log.println("Job request : " + requestBuilder.buildJobRequest().toString());
            jobGuid = jobsService.startAddVersionJob(requestBuilder);

            log.println(AddVersionBuilder_AddVersion_info_pollJobMessage());
            JobState state = pollJob(jobGuid, log);
            if (state != JobState.COMPLETED) {
                listener.error(AddVersionBuilder_AddVersion_error_jobFailure(state.toString()));
                run.setResult(defaultResult);
            } else {
                log.println(AddVersionBuilder_AddVersion_success_analysisComplete());
                downloadDeliveryReport(workspace, applicationGuid, resolvedVersionName, listener);
                run.setResult(Result.SUCCESS);
            }
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
                listener.error(AddVersionBuilder_AddVersion_error_jobServiceException());
                e.printStackTrace(listener.getLogger());
                run.setResult(defaultResult);
            }
        } catch (ApiCallException | ApplicationServiceException e) {
            listener.error(AddVersionBuilder_AddVersion_error_jobServiceException());
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
        } catch (PackagePathInvalidException e) {
            listener.error(AddVersionBuilder_AddVersion_error_jobServiceException());
            e.printStackTrace(listener.getLogger());
            run.setResult(defaultResult);
        }
    }

    /**
     * Download the DMT report to the jenkins workspace
     *
     * @param workspace
     * @param versionName
     */
    private void downloadDeliveryReport(FilePath workspace, String appGuid, String versionName, TaskListener taskListener) throws ApplicationServiceException, ApiCallException {
        PrintStream log = taskListener.getLogger();
        log.println("Downloading delivery report...");
        String versionGuid = applicationService.getApplicationVersion(appGuid).stream().filter(v -> v.getName().equalsIgnoreCase(versionName))
                .map(VersionDto::getGuid).findFirst().orElseThrow(() -> new ApiCallException(404, "version not found"));
        log.println("Version guid " + versionGuid);

        String reportFile = versionName + "-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + ".xml";
        try {
            String content = apiService.getForEntity("/api/applications/" + appGuid + "/versions/" + versionGuid + "/dmt-report/download", String.class);
            workspace.child(reportFile).write(content, StandardCharsets.UTF_8.toString());
            log.println("Version delivery report saved in workspace " + reportFile);
        } catch (IOException | InterruptedException e) {
            taskListener.error("Failed to download the delivery report", e.getMessage());
        }
    }

    /**
     * Check some initial elements before running the Job
     *
     * @return The error message based on the issue that was found, null if no issue was found
     */
    private String checkJobParameters() {
        if (StringUtils.isAnyBlank(applicationName, filePath)) {
            return GenericError_error_missingRequiredParameters();
        }
        String apiServerUrl = getAipConsoleUrl();
        String apiKey = Secret.toString(getApiKey());

        if (StringUtils.isBlank(apiServerUrl)) {
            return GenericError_error_noServerUrl();
        }
        if (StringUtils.isBlank(apiKey)) {
            return GenericError_error_noApiKey();
        }

        return null;
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

    @Symbol("aipDeliver")
    @Extension
    public static final class DeliverDescriptorImpl extends BaseActionBuilderDescriptor {

        @Inject
        private AipConsoleGlobalConfiguration configuration;

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return DeliverBuilder_DescriptorImpl_displayName();
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
