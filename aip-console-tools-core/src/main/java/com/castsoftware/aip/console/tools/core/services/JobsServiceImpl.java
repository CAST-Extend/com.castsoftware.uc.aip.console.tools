package com.castsoftware.aip.console.tools.core.services;

import com.castsoftware.aip.console.tools.core.dto.ApiInfoDto;
import com.castsoftware.aip.console.tools.core.dto.SemVer;
import com.castsoftware.aip.console.tools.core.dto.jobs.ChangeJobStateRequest;
import com.castsoftware.aip.console.tools.core.dto.jobs.CreateJobsRequest;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobRequestBuilder;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobStatus;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobStatusWithSteps;
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
import java.util.HashMap;
import java.util.Map;
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
    public String startCreateApplication(String applicationName) throws JobServiceException {
        if (StringUtils.isBlank(applicationName)) {
            throw new JobServiceException("Application name is empty. Unable to create application");
        }
        return startCreateApplication(applicationName, null);
    }

    @Override
    public String startCreateApplication(String applicationName, String nodeGuid) throws JobServiceException {
        return startCreateApplication(applicationName, nodeGuid, null);
    }

    @Override
    public String startCreateApplication(String applicationName, String nodeGuid, String domainName) throws JobServiceException {
        Map<String, String> jobParams = new HashMap<>();
        jobParams.put(Constants.PARAM_APP_NAME, applicationName);
        if (StringUtils.isNotBlank(nodeGuid)) {
            jobParams.put(Constants.PARAM_NODE_GUID, nodeGuid);
        }
        if (StringUtils.isNotBlank(domainName)) {
            jobParams.put("domainName", domainName);
        }

        try {
            String jobsEndpoint = ApiEndpointHelper.getJobsEndpoint();
            CreateJobsRequest request = new CreateJobsRequest();
            request.setJobType(JobType.DECLARE_APPLICATION);
            request.setJobParameters(jobParams);
            SuccessfulJobStartDto jobStartDto = restApiService.postForEntity(jobsEndpoint, request, SuccessfulJobStartDto.class);
            return jobStartDto.getJobGuid();
        } catch (ApiCallException e) {
            log.log(Level.SEVERE, "Unable to create new application '" + applicationName + "'", e);
            throw new JobServiceException("Creation of application failed", e);
        }
    }

    @Override
    public String startAddVersionJob(String appGuid, String applicationName, String zipFileName, String versionName, Date versionReleaseDate, boolean cloneVersion)
            throws JobServiceException {
        return startAddVersionJob(appGuid, applicationName, zipFileName, versionName, versionReleaseDate, cloneVersion, false);
    }

    @Override
    public String startAddVersionJob(String appGuid, String applicationName, String sourcePath, String versionName, Date versionReleaseDate, boolean cloneVersion, boolean enableSecurityDataflow)
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
        JobRequestBuilder builder = JobRequestBuilder.newInstance(appGuid, sourcePath, cloneVersion ? JobType.CLONE_VERSION : JobType.ADD_VERSION)
                .versionName(versionName)
                .releaseAndSnapshotDate(versionReleaseDate)
                .securityObjective(enableSecurityDataflow);

        return startAddVersionJob(builder);
    }

    @Override
    public String startAddVersionJob(JobRequestBuilder builder) throws JobServiceException {

        ApiInfoDto apiInfoDto = getApiInfoDto();
        if (apiInfoDto.isEnablePackagePathCheck()) {
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
            SuccessfulJobStartDto dto = restApiService.postForEntity(ApiEndpointHelper.getJobsEndpoint(), jobRequest, SuccessfulJobStartDto.class);

            if (dto == null || StringUtils.isBlank(dto.getJobGuid())) {
                throw new JobServiceException("No response from AIP Console when start the job");
            }

            // State was suspended after uploading for versions <= 1.9
            // After that, suspended jobs mean that no job executor is available for now (so don't do the update)
            SemVer aipConsoleVersion = SemVer.parse(apiInfoDto.getApiVersion());
            if (aipConsoleVersion.getMajor() <= 1 && aipConsoleVersion.getMinor() <= 9) {
                String jobDetailsEndpoint = ApiEndpointHelper.getJobDetailsEndpoint(dto.getJobGuid());
                JobStatusWithSteps jobStatusWithSteps = restApiService.getForEntity(jobDetailsEndpoint, JobStatusWithSteps.class);
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
        return pollAndWaitForJobFinished(jobGuid, JobStatus::getState);
    }

    @Override
    public <R> R pollAndWaitForJobFinished(String jobGuid, Function<JobStatusWithSteps, R> callback) throws JobServiceException {
        return pollAndWaitForJobFinished(jobGuid,
                jobStep -> log.info("Current step is : " + jobStep.getProgressStep()),
                jobContentDto -> printLog(jobContentDto),
                callback);
    }

    @Override
    public <R> R pollAndWaitForJobFinished(String jobGuid, Consumer<JobStatusWithSteps> stepChangedCallback, Consumer<LogContentDto> pollingCallback, Function<JobStatusWithSteps, R> completionCallback) throws JobServiceException {
        assert StringUtils.isNotBlank(jobGuid);

        String jobDetailsEndpoint = ApiEndpointHelper.getJobDetailsEndpoint(jobGuid);
        String previousStep = "";
        log.fine("Checking status of Job with GUID " + jobGuid);
        try {
            JobStatusWithSteps jobStatus;
            String logName = null;
            int startOffset = 0;
            while (true) {
                // Force login to keep session alive (jobs endpoint doesn't refresh session status)
                restApiService.login();
                jobStatus = restApiService.getForEntity(jobDetailsEndpoint, JobStatusWithSteps.class);
                String currentStep = jobStatus.getProgressStep();

                if (currentStep != null && !currentStep.equalsIgnoreCase(previousStep)) {
                    previousStep = currentStep;
                    if (stepChangedCallback != null) {
                        stepChangedCallback.accept(jobStatus);
                    }
                    logName = getLogName(jobGuid, currentStep);
                    startOffset = 0;
                }

                if (!StringUtils.isAnyBlank(logName, currentStep)) {
                    LogContentDto logContent = restApiService.getForEntity("/api/jobs/" + jobGuid + "/steps/" + currentStep + "/logs/" + logName + "?nbLines=3000&startOffset=" + startOffset, LogContentDto.class);
                    pollingCallback.accept(logContent);
                    startOffset = startOffset + logContent.getNbLines();
                }

                if (jobStatus.getState() != JobState.STARTED && jobStatus.getState() != JobState.STARTING) {
                    break;
                }

                Thread.sleep(pollingSleepDuration);
            }
            return completionCallback.apply(jobStatus);
        } catch (InterruptedException | ApiCallException e) {
            log.log(Level.SEVERE, "Error occurred while polling the job status", e);
            throw new JobServiceException(e);
        }
    }

    private String getLogName(String jobGuid, String step) throws ApiCallException {
        Set<LogsDto> logs = restApiService.getForEntity("/api/jobs/" + jobGuid + "/steps/" + step + "/logs", new TypeReference<Set<LogsDto>>() {
        });
        return logs.stream().filter(l -> l.getLogType().equalsIgnoreCase("MAIN_LOG")).findFirst().map(LogsDto::getLogName).orElse(null);
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
