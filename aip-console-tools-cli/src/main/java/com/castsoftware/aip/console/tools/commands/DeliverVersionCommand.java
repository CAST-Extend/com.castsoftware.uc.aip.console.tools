package com.castsoftware.aip.console.tools.commands;

import com.castsoftware.aip.console.tools.core.dto.ApplicationDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobExecutionDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobRequestBuilder;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobType;
import com.castsoftware.aip.console.tools.core.exceptions.ApiCallException;
import com.castsoftware.aip.console.tools.core.exceptions.ApiKeyMissingException;
import com.castsoftware.aip.console.tools.core.exceptions.ApplicationServiceException;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;
import com.castsoftware.aip.console.tools.core.exceptions.PackagePathInvalidException;
import com.castsoftware.aip.console.tools.core.exceptions.UploadException;
import com.castsoftware.aip.console.tools.core.services.ApplicationService;
import com.castsoftware.aip.console.tools.core.services.JobsService;
import com.castsoftware.aip.console.tools.core.services.RestApiService;
import com.castsoftware.aip.console.tools.core.services.UploadService;
import com.castsoftware.aip.console.tools.core.utils.Constants;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.File;
import java.nio.file.Files;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Deliver an application version on AIP Console
 */
@Component
@CommandLine.Command(
        name = "Deliver",
        mixinStandardHelpOptions = true,
        aliases = {"deliver"},
        description = "Delivers a new version to AIP Console"
)
@Slf4j
@Getter
@Setter
public class DeliverVersionCommand implements Callable<Integer> {
    private final RestApiService restApiService;
    private final JobsService jobsService;
    private final UploadService uploadService;
    private final ApplicationService applicationService;

    @CommandLine.Mixin
    private SharedOptions sharedOptions;

    @CommandLine.Option(names = {"-n", "--app-name"},
            paramLabel = "APPLICATION_NAME",
            description = "The Name of the application to rescan",
            required = true)
    private String applicationName;

    @CommandLine.Option(names = {"-f", "--file"},
            paramLabel = "FILE",
            description = "A local zip or tar.gz file OR a path to a folder on the node where the source if saved",
            required = true)
    private File filePath;

    @CommandLine.Option(names = {"-v", "--version-name"},
            paramLabel = "VERSION_NAME",
            description = "The name of the version to create")
    private String versionName;

    // Hiding this, to avoid breaking commands already using it
    // Analyze command will automatically set as current when starting so it's unnecessary
    @CommandLine.Option(names = {"-d", "--auto-deploy"},
            description = "Sets the version as current after delivery (The Analyze command will set as current automatically if the version is in delivered state)"
                    + " if specified without parameter: ${FALLBACK-VALUE}",
            fallbackValue = "true",
            hidden = true,
            hideParamSyntax = true)
    private boolean autoDeploy = false;

    @CommandLine.Option(names = {"--no-clone", "--no-rescan", "--create-new-version"},
            description = "Enable this flag to create a new version without cloning the latest version configuration. Note that when using \"in-place\" more, this parameter will be ignore and versions will always be cloned."
                    + " if specified without parameter: ${FALLBACK-VALUE}",
            fallbackValue = "true",
            defaultValue = "false")
    private boolean disableClone = false;

    @CommandLine.Option(names = "--auto-create",
            description = "If the given application name doesn't exist on the target server, it'll be automatically created before creating a new version"
                    + " if specified without parameter: ${FALLBACK-VALUE}",
            fallbackValue = "true")
    private boolean autoCreate = false;

    @CommandLine.Option(names = "--enable-security-dataflow",
            description = "If defined, this will activate the security dataflow for this version"
                    + " if specified without parameter: ${FALLBACK-VALUE}",
            fallbackValue = "true")
    private boolean enableSecurityDataflow = false;

    @CommandLine.Option(names = "--node-name",
            paramLabel = "NODE_NAME", description = "The name of the node on which the application will be created. Ignored if no --auto-create or the application already exists.")
    private String nodeName;

    @CommandLine.Option(names = {"-b", "--backup"},
            description = "Enable backup of application before delivering the new version"
                    + " if specified without parameter: ${FALLBACK-VALUE}",
            fallbackValue = "true")
    private boolean backupEnabled = false;

    @CommandLine.Option(names = "--backup-name",
            paramLabel = "BACKUP_NAME",
            description = "The name of the backup to create before delivering the new version. Defaults to 'backup_date.time'")
    private String backupName;

    @CommandLine.Option(names = "--auto-discover",
            description = "AIP Console will discover new technologies and install new extensions, to disable if run consistency check"
                    + " if specified without parameter: ${FALLBACK-VALUE}",
            fallbackValue = "true")
    private boolean autoDiscover = true;

    @CommandLine.Option(names = {"-exclude", "--exclude-patterns"},
            description = "File patterns(glob pattern) to exclude in the delivery, separated with comma")
    private String exclusionPatterns;

    @CommandLine.Option(names = {"-current", "--set-as-current"},
            description = "true or false depending on whether the version should be set as the current one or not."
                    + " if specified without parameter: ${FALLBACK-VALUE}",
            fallbackValue = "true")
    private boolean setAsCurrent = false;

    /**
     * Domain name
     */
    @CommandLine.Option(names = "--domain-name", paramLabel = "DOMAIN_NAME", description = "The name of the domain to assign to the application. Will be created if it doesn't exists. No domain will be assigned if left empty. Will only be used when creating the application.")
    private String domainName;

    public DeliverVersionCommand(RestApiService restApiService, JobsService jobsService, UploadService uploadService, ApplicationService applicationService) {
        this.restApiService = restApiService;
        this.jobsService = jobsService;
        this.uploadService = uploadService;
        this.applicationService = applicationService;
    }

    @Override
    public Integer call() throws Exception {
        // Same as a part of the AddVersion command
        // Upload a local file or register a remote path
        // And then starts a job up to "Delivery"
        // Should deploy/mark as current
        if (StringUtils.isBlank(applicationName)) {
            log.error("No application name provided. Exiting.");
            return Constants.RETURN_APPLICATION_INFO_MISSING;
        }

        try {
            if (sharedOptions.getTimeout() != Constants.DEFAULT_HTTP_TIMEOUT) {
                restApiService.setTimeout(sharedOptions.getTimeout(), TimeUnit.SECONDS);
            }
            restApiService.validateUrlAndKey(sharedOptions.getFullServerRootUrl(), sharedOptions.getUsername(), sharedOptions.getApiKeyValue());
        } catch (ApiKeyMissingException e) {
            return Constants.RETURN_NO_PASSWORD;
        } catch (ApiCallException e) {
            return Constants.RETURN_LOGIN_ERROR;
        }

        log.info("Deliver version command has triggered with log output = '{}'", sharedOptions.isVerbose());

        String applicationGuid;
        Thread shutdownHook = null;

        try {
            log.info("Searching for application '{}' on AIP Console", applicationName);
            applicationGuid = applicationService.getOrCreateApplicationFromName(applicationName, autoCreate, nodeName, domainName, sharedOptions.isVerbose());
            if (StringUtils.isBlank(applicationGuid)) {
                String message = autoCreate ?
                        "Creation of the application '{}' failed on AIP Console" :
                        "Application '{}' was not found on AIP Console";
                log.error(message, applicationName);
                return Constants.RETURN_APPLICATION_NOT_FOUND;
            }

            ApplicationDto app = applicationService.getApplicationFromName(applicationName);
            if (app.isInPlaceMode() && Files.isRegularFile(filePath.toPath())) {
                log.error("The application is created in \"in-place\" mode, only folder path is allowed to deliver in this mode.");
                return Constants.RETURN_INPLACE_MODE_ERROR;
            }

            String sourcePath = uploadService.uploadFileAndGetSourcePath(applicationName, applicationGuid, filePath);
            // check that the application actually has versions, otherwise it's just an add version job

            // Clone the version if we're in "in-place" mode or the user wants to clone the version and the application has versions
            boolean cloneVersion = (app.isInPlaceMode() || !disableClone) && applicationService.applicationHasVersion(applicationGuid);

            JobRequestBuilder builder = JobRequestBuilder
                    .newInstance(applicationGuid, sourcePath, cloneVersion ? JobType.CLONE_VERSION : JobType.ADD_VERSION, app.getCaipVersion())
                    .endStep(autoDeploy ? Constants.SET_CURRENT_STEP_NAME : Constants.DELIVER_VERSION)
                    .versionName(versionName)
                    .releaseAndSnapshotDate(new Date())
                    .securityObjective(enableSecurityDataflow)
                    .backupApplication(backupEnabled)
                    .backupName(backupName)
                    .autoDiscover(autoDiscover);

            if (app.isInPlaceMode() || setAsCurrent) {
                //should got up to "set as current" when in-place mode is operating
                builder.endStep(Constants.SET_CURRENT_STEP_NAME);
            }

            String deliveryConfigGuid = applicationService.createDeliveryConfiguration(applicationGuid, sourcePath, exclusionPatterns, cloneVersion);
            log.info("delivery configuration guid " + deliveryConfigGuid);
            if (StringUtils.isNotBlank(deliveryConfigGuid)) {
                builder.deliveryConfigGuid(deliveryConfigGuid);
            }

            String jobGuid = jobsService.startAddVersionJob(builder);
            shutdownHook = getShutdownHookForJobGuid(jobGuid);

            Runtime.getRuntime().addShutdownHook(shutdownHook);
            JobExecutionDto jobStatus = jobsService.pollAndWaitForJobFinished(jobGuid, Function.identity(), sharedOptions.isVerbose());
            if (JobState.COMPLETED == jobStatus.getState()) {
                log.info("Delivery of application {} was completed successfully.", applicationName);
                return Constants.RETURN_OK;
            } else {
                log.error("Job did not complete. Status is '{}' on step '{}'", jobStatus.getState(), jobStatus.getCurrentStep());
                return Constants.RETURN_JOB_FAILED;
            }
        } catch (ApplicationServiceException e) {
            return Constants.RETURN_APPLICATION_INFO_MISSING;
        } catch (UploadException e) {
            log.error("Error occurred while attempting to upload the given file.", e);
            return Constants.RETURN_UPLOAD_ERROR;
        } catch (JobServiceException e) {
            return Constants.RETURN_JOB_POLL_ERROR;
        } catch (PackagePathInvalidException e) {
            log.error("Provided Path is invalid", e);
            return Constants.RETURN_JOB_FAILED;
        } finally {
            // Remove shutdown hook after execution
            // This is to avoid exceptions during job execution to
            if (shutdownHook != null) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
        }

    }

    private Thread getShutdownHookForJobGuid(String jobGuid) {
        return new Thread(() -> {
            log.info("Received termination signal. Cancelling currently running job on AIP Console and exiting.");
            try {
                jobsService.cancelJob(jobGuid);
            } catch (JobServiceException e) {
                log.error("Cannot cancel the job on AIP Console. Please cancel it manually.", e);
            }
        });
    }
}
