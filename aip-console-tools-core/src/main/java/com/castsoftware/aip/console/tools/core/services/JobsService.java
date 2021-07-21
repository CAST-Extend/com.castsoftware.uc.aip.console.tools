package com.castsoftware.aip.console.tools.core.services;

import com.castsoftware.aip.console.tools.core.dto.jobs.JobExecutionDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobRequestBuilder;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.dto.jobs.LogContentDto;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;

import java.util.Date;
import java.util.function.Consumer;
import java.util.function.Function;

public interface JobsService {
    /**
     * Start the "Create application" job, which will create a new application on the target AIP Console instance
     *
     * @param applicationName The name of the application
     * @param inplaceMode     indicate if the app will be in "inplace" mode
     * @return The job GUID on AIP Console
     * @throws JobServiceException If an error occurs while starting the job
     */
    String startCreateApplication(String applicationName, boolean inplaceMode, String caipVersion) throws JobServiceException;

    /**
     * Start the "Create application" job, which will create a new application on the target AIP Console instance
     *
     * @param applicationName The name of the application
     * @param nodeGuid        The Node GUID on which the application should be created. Can be null.
     * @param inplaceMode     indicate if the app will be in "inplace" mode
     * @return The job GUID on AIP Console
     * @throws JobServiceException If an error occurs while starting the job
     */
    String startCreateApplication(String applicationName, String nodeGuid, boolean inplaceMode, String caipVersion) throws JobServiceException;

    /**
     * Start the "Create application" job, which will create a new application on the target AIP Console instance
     * with the given domain name
     *
     * @param applicationName The name of the application
     * @param nodeGuid        The Node GUID on which the application should be created. Can be null.
     * @param domainName      The name of the domain to assign to this application
     * @param inplaceMode     indicate if the app will be in "inplace" mode
     * @param cssServerGuid target CSS server if any (can be null when none supplied)
     * @return The job GUID on AIP Console
     * @throws JobServiceException If an error occurs while starting the job
     */
    String startCreateApplication(String applicationName, String nodeGuid, String domainName, boolean inplaceMode, String caipVersion, String cssServerGuid) throws JobServiceException;

    /**
     * Start the "Create Version" job, which will create a new version for an application on AIP Console
     *
     * @param appGuid            The application GUID for which the version should be created
     * @param zipFileName        THe name of the ZIP File that was uploaded before
     * @param versionName        The name fo the version to create
     * @param versionReleaseDate The release date of this version
     * @param cloneVersion       Whether to clone an existing version or not (rescan)
     * @return The GUID of the job that was started on AIP Console
     * @throws JobServiceException IF any error occurs while starting the job
     */
    String startAddVersionJob(String appGuid, String applicationName, String caipVersion, String zipFileName, String versionName, Date versionReleaseDate, boolean cloneVersion) throws JobServiceException;

    /**
     * Start the "Create Version" job, which will create a new version for an application on AIP Console
     *
     * @param appGuid                The application GUID for which the version should be created
     * @param fileName               The name of the ZIP File that was uploaded before or the subfolder in source.folder.location
     * @param versionName            The name fo the version to create
     * @param versionReleaseDate     The release date of this version
     * @param cloneVersion           Whether to clone an existing version or not (rescan)
     * @param enableSecurityDataflow Whether the security dataflow parameter should be enabled in this version
     * @return The GUID of the job that was started on AIP Console
     * @throws JobServiceException IF any error occurs while starting the job
     */
    String startAddVersionJob(String appGuid, String applicationName, String caipVersion, String fileName, String versionName, Date versionReleaseDate, boolean cloneVersion, boolean enableSecurityDataflow) throws JobServiceException;

    String startAddVersionJob(JobRequestBuilder jobRequestBuilder) throws JobServiceException;

    /**
     * This starts any job based on the job request builder content
     *
     * @param jobRequestBuilder The instance containing the configuration of the job
     * @return the job's GUID
     */
    String startJob(JobRequestBuilder jobRequestBuilder) throws JobServiceException;

    /**
     * Polls AIP Console to get the status of the job with the given GUID.
     *
     * @param jobGuid The job GUID to poll
     * @return The {@link JobState} of the job once it is finished
     * @throws JobServiceException
     */
    JobState pollAndWaitForJobFinished(String jobGuid) throws JobServiceException;

    /**
     * Polls AIP Console and executes the given callback once the job is completed
     *
     * @param jobGuid   The job guid to poll
     * @param callback  A {@link Function} that takes in entry the {@link JobExecutionDto}
     * @param logOutput whether to output the log or not
     * @param <R>       The type or return for the callback
     * @return The result from the completionCallback
     * @throws JobServiceException If any error occurs while polling AIP Console
     */
    <R> R pollAndWaitForJobFinished(String jobGuid, Function<JobExecutionDto, R> callback, boolean logOutput) throws JobServiceException;

    /**
     * Polls AIP Console, then for each changes to the current step of the job, runs the provided stepChangedCallback.
     * Once the job is finished, we call completionCallback and return the result of its execution
     * <p/>
     * The {@link JobsServiceImpl#pollAndWaitForJobFinished(String)} uses this method internally.
     * It polls the status and on any step changes, it'll log the current step.
     * Once the job is complete, we simply get {@link JobState} from the {@link JobExecutionDto} and return it.
     * <p/>
     *
     * @param jobGuid             The GUID of the job to poll on AIP Console
     * @param stepChangedCallback A {@link Consumer} that will be run on each changes to the current step in the job
     * @param completionCallback  a {@link Function} to execute once the job is finished.
     * @param <R>                 The return type for the completionCallback
     * @return The value from the completionCallback method
     * @throws JobServiceException If any error occurs while polling the job on AIP Console
     */
    //<R> R pollAndWaitForJobFinished(String jobGuid, Consumer<JobStatusWithSteps> stepChangedCallback, Function<JobStatusWithSteps, R> completionCallback) throws JobServiceException;

    <R> R pollAndWaitForJobFinished(String jobGuid, Consumer<JobExecutionDto> stepChangedCallback, Consumer<LogContentDto> pollingCallback, Function<JobExecutionDto, R> completionCallback) throws JobServiceException;

    void cancelJob(String jobGuid) throws JobServiceException;
}
