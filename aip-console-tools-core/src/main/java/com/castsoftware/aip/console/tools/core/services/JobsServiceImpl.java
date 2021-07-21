package com.castsoftware.aip.console.tools.core.services;

import com.castsoftware.aip.console.tools.core.dto.ApiInfoDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.ChangeJobStateRequest;
import com.castsoftware.aip.console.tools.core.dto.jobs.CreateApplicationJobRequest;
import com.castsoftware.aip.console.tools.core.dto.jobs.CreateJobsRequest;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobExecutionDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobRequestBuilder;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobType;
import com.castsoftware.aip.console.tools.core.dto.jobs.LogContentDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.LogsDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.SuccessfulJobStartDto;
import com.castsoftware.aip.console.tools.core.exceptions.ApiCallException;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;
import com.castsoftware.aip.console.tools.core.utils.ApiEndpointHelper;
import com.castsoftware.aip.console.tools.core.utils.Constants;
import com.castsoftware.aip.console.tools.core.utils.LogUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

@Log
public class JobsServiceImpl implements JobsService {
    private static final long POLL_SLEEP_DURATION = TimeUnit.SECONDS.toMillis(10);

    private final RestApiService restApiService;

    private final long pollingSleepDuration;

    private ApiInfoDto apiInfoDto;

    public JobsServiceImpl(RestApiService restApiService) {
        this.restApiService = restApiService;
        this.pollingSleepDuration = POLL_SLEEP_DURATION;
    }

    public JobsServiceImpl(RestApiService restApiService, long pollingSleepDuration) {
        this.restApiService = restApiService;
        this.pollingSleepDuration = pollingSleepDuration;
    }

    @Override
    public String startCreateApplication(String applicationName, boolean inplaceMode, String caipVersion) throws JobServiceException {
        if (StringUtils.isBlank(applicationName)) {
            throw new JobServiceException("Application name is empty. Unable to create application");
        }
        return startCreateApplication(applicationName, null, inplaceMode, caipVersion);
    }

    @Override
    public String startCreateApplication(String applicationName, String nodeGuid, boolean inplaceMode, String caipVersion) throws JobServiceException {
        return startCreateApplication(applicationName, nodeGuid, null, inplaceMode, caipVersion,null);
    }

    @Override
    public String startCreateApplication(String applicationName, String nodeGuid, String domainName, boolean inplaceMode, String caipVersion, String cssServerGuid) throws JobServiceException {
        try {
            CreateApplicationJobRequest.CreateApplicationJobRequestBuilder requestBuilder =
                    CreateApplicationJobRequest.builder().appName(applicationName).inPlaceMode(inplaceMode).caipVersion(caipVersion).cssGuid(cssServerGuid);
            if (StringUtils.isNotBlank(domainName)) {
                requestBuilder.domainName(domainName);
            }
            SuccessfulJobStartDto jobStartDto = restApiService.postForEntity(ApiEndpointHelper.getCreateApplicationEndPoint(), requestBuilder.build(), SuccessfulJobStartDto.class);
            return jobStartDto.getJobGuid();
        } catch (ApiCallException e) {
            log.log(Level.SEVERE, "Unable to create new application '" + applicationName + "'", e);
            throw new JobServiceException("Creation of application failed", e);
        }
    }

    @Override
    public String startAddVersionJob(String appGuid, String applicationName, String caipVersion, String zipFileName, String versionName, Date versionReleaseDate, boolean cloneVersion)
            throws JobServiceException {
        return startAddVersionJob(appGuid, applicationName, caipVersion, zipFileName, versionName, versionReleaseDate, cloneVersion, false);
    }

    @Override
    public String startAddVersionJob(String appGuid, String applicationName, String caipVersion, String sourcePath, String versionName, Date versionReleaseDate, boolean cloneVersion, boolean enableSecurityDataflow)
            throws JobServiceException {
        if (StringUtils.isBlank(appGuid)) {
            throw new JobServiceException("No application GUID provided");
        }
        if (StringUtils.isBlank(sourcePath)) {
            throw new JobServiceException("No Archive File name provided to create the new version");
        }
        if (versionReleaseDate == null) {
            throw new JobServiceException("No release date provided.");
        }
        if (StringUtils.isBlank(versionName)) {
            DateFormat formatVersionName = new SimpleDateFormat("yyMMdd.HHmmss");
            versionName = "v" + formatVersionName.format(versionReleaseDate);
        }
        JobRequestBuilder builder = JobRequestBuilder.newInstance(appGuid, sourcePath, cloneVersion ? JobType.CLONE_VERSION : JobType.ADD_VERSION, caipVersion)
                .versionName(versionName)
                .releaseAndSnapshotDate(versionReleaseDate)
                .securityObjective(enableSecurityDataflow);

        return startAddVersionJob(builder);
    }

    @Override
    public String startAddVersionJob(JobRequestBuilder builder) throws JobServiceException {

        ApiInfoDto apiInfoDto = getApiInfoDto();
        if (apiInfoDto.isExtractionRequired()) {
            builder.startStep(Constants.CODE_SCANNER_STEP_NAME);
        } else {
            builder.startStep(Constants.EXTRACT_STEP_NAME);
        }
        return startJob(builder);
    }

    @Override
    public String startJob(JobRequestBuilder jobRequestBuilder) throws JobServiceException {
        CreateJobsRequest jobRequest = jobRequestBuilder.buildJobRequest();
        ApiInfoDto apiInfoDto = getApiInfoDto();

        try {
            SuccessfulJobStartDto dto = restApiService.postForEntity(ApiEndpointHelper.getJobsEndpoint(jobRequest), jobRequest, SuccessfulJobStartDto.class);

            if (dto == null || StringUtils.isBlank(dto.getJobGuid())) {
                throw new JobServiceException("No response from AIP Console when start the job");
            }

            // BACKWARDS COMPATIBILITY with 1.9
            // Job is started in suspended state, and must be resumed
            if (apiInfoDto.isJobToBeResumed()) {
                String jobDetailsEndpoint = ApiEndpointHelper.getJobDetailsEndpoint(dto.getJobGuid());
                JobExecutionDto jobStatusWithSteps = restApiService.getForEntity(jobDetailsEndpoint, JobExecutionDto.class);
                if (jobStatusWithSteps.getState() == JobState.STARTING) {
                    log.finest("Resuming suspended job");
                    ChangeJobStateRequest resumeRequest = new ChangeJobStateRequest();
                    resumeRequest.setState(JobState.STARTED);
                    restApiService.putForEntity(jobDetailsEndpoint, resumeRequest, String.class);
                }
            }
            log.info("Successfully started Job");
            return dto.getJobGuid();
        } catch (ApiCallException e) {
            log.log(Level.SEVERE, "Error starting Job with type " + jobRequest.getJobType(), e);
            throw new JobServiceException(e);
        }
    }

    @Override
    public JobState pollAndWaitForJobFinished(String jobGuid) throws JobServiceException {
        return pollAndWaitForJobFinished(jobGuid, JobExecutionDto::getState, true);
    }

    @Override
    public <R> R pollAndWaitForJobFinished(String jobGuid, Function<JobExecutionDto, R> callback, boolean logOutput) throws JobServiceException {
        Consumer<LogContentDto> pollingCallback = !logOutput ? null : jobContentDto -> printLog(jobContentDto);
        return pollAndWaitForJobFinished(jobGuid,
                jobStep -> log.info("Current step is : " + jobStep.getCurrentStep()),
                pollingCallback,
                callback);
    }

    @Override
    public <R> R pollAndWaitForJobFinished(String jobGuid, Consumer<JobExecutionDto> stepChangedCallback, Consumer<LogContentDto> pollingCallback, Function<JobExecutionDto, R> completionCallback) throws JobServiceException {
        assert StringUtils.isNotBlank(jobGuid);

        String jobDetailsEndpoint = ApiEndpointHelper.getJobDetailsEndpoint(jobGuid);
        String previousStep = "";
        log.fine("Checking status of Job with GUID " + jobGuid);
        int retryCount = 0;
        try {
            JobExecutionDto jobStatus;
            String logName = null;
            int startOffset = 0;
            while (true) {
                Thread.sleep(pollingSleepDuration);
                // Force login to keep session alive (jobs endpoint doesn't refresh session status)
                restApiService.login();

                // Sometimes it takes more than 10 secs till the jobstatus is ready
                jobStatus = getJobStatus(jobDetailsEndpoint);
                if (jobStatus == null) {
                    if (retryCount < 20) {
                        ++retryCount;
                        continue;
                    } else {
                        throw new ApiCallException(500, "Failed to get job status after retrying 20 times");
                    }

                } else {
                    retryCount = 0;
                }

                String currentStep = jobStatus.getCurrentStep();

                if (currentStep != null && !currentStep.equalsIgnoreCase(previousStep)) {
                    previousStep = currentStep;
                    if (stepChangedCallback != null) {
                        stepChangedCallback.accept(jobStatus);
                    }
                    logName = getLogName(jobGuid, currentStep);
                    startOffset = 0;
                }

                if (pollingCallback != null && !StringUtils.isAnyBlank(logName, currentStep)) {
                    LogContentDto logContent = getLogContent(jobGuid, currentStep, logName, startOffset);
                    if (logContent != null && !logContent.getLines().isEmpty()) {
                        pollingCallback.accept(logContent);
                        startOffset = startOffset + logContent.getNbLines();
                    }
                }

                if (jobStatus.getState() != JobState.STARTED && jobStatus.getState() != JobState.STARTING) {
                    break;
                }
            }
            return completionCallback.apply(jobStatus);
        } catch (InterruptedException | ApiCallException e) {
            log.log(Level.SEVERE, "Error occurred while polling the job status", e);
            throw new JobServiceException(e);
        }
    }

    @Override
    public void cancelJob(String jobGuid) throws JobServiceException {
        try {
            ChangeJobStateRequest cancel = new ChangeJobStateRequest();
            cancel.setState(JobState.CANCELED);
            restApiService.postForEntity(ApiEndpointHelper.getJobDetailsEndpoint(jobGuid) + "/cancel", cancel, Void.class);
        } catch (ApiCallException e) {
            throw new JobServiceException(e);
        }
    }

    private LogContentDto getLogContent(String jobGuid, String currentStep, String logName, int startOffset) {
        try {
            return restApiService.getForEntity("/api/jobs/" + jobGuid + "/steps/" + currentStep + "/logs/" + logName + "?nbLines=3000&startOffset=" + startOffset, LogContentDto.class);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error to get the log content", e);
            return null;
        }
    }

    private String getLogName(String jobGuid, String step) throws ApiCallException {
        try {
            Set<LogsDto> logs = restApiService.getForEntity("/api/jobs/" + jobGuid + "/steps/" + step + "/logs", new TypeReference<Set<LogsDto>>() {
            });
            return logs.stream().filter(l -> l.getLogType().equalsIgnoreCase("MAIN_LOG")).findFirst().map(LogsDto::getLogName).orElse(null);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error to get the log name");
            return null;
        }
    }

    private JobExecutionDto getJobStatus(String jobDetailsEndpoint) {
        try {
            return restApiService.getForEntity(jobDetailsEndpoint, JobExecutionDto.class);
        } catch (ApiCallException e) {
            log.log(Level.SEVERE, "Error to get job status " + jobDetailsEndpoint);
            return null;
        }
    }

    private void printLog(LogContentDto logContent) {
        logContent.getLines().forEach(logLine -> log.info(LogUtils.replaceAllSensitiveInformation(logLine.getContent())));
    }

    private synchronized ApiInfoDto getApiInfoDto() {
        if (apiInfoDto == null) {
            apiInfoDto = restApiService.getAipConsoleApiInfo();
        }
        return apiInfoDto;
    }
}