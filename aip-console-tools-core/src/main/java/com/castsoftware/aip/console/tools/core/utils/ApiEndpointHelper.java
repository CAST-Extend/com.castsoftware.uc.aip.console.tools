package com.castsoftware.aip.console.tools.core.utils;

import com.castsoftware.aip.console.tools.core.dto.jobs.CreateJobsRequest;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;

public class ApiEndpointHelper {

    private static final String ROOT_PATH = "/api";
    private static final String APPLICATIONS_ENDPOINT = "/applications";
    private static final String JOBS_ENDPOINT = "/jobs";
    private static final String UPLOAD_ENDPOINT = "/upload";
    private static final String VERSIONS_ENDPOINT = "/versions";
    private static final String EXTRACT_ENDPOINT = "/extract";
    private static final String DEBUG_OPTIONS_ENDPOINT = "/debug-options";
    private static final String SHOW_SQL_ENDPOINT = "/show-sql";
    private static final String AMT_PROFILE_ENDPOINT = "/activate-amt-memory-profile";
    private static final String CREATE_APPLICATION_ENDPOINT = "/create-application";
    public static final String ADD_VERSION_ENDPOINT = "/add-version";
    public static final String CLONE_VERSION_ENDPOINT = "/clone-version";
    public static final String ANALYZE_SECURITY_DATAFLOW = "/analyze-security-dataflow";
    public static final String RESYNC_APPLICATION = "/resync-application";
    public static final String SHERLOCK_BACKUP_ENDPOINT = "/sherlock-backup";
    public static final String RESTORE_ENDPOINT = "/restore";
    public static final String BACKUP_ENDPOINT = "/backup";
    public static final String REJECT_VERSION_ENDPOINT = "/reject-version";
    public static final String DELETE_VERSION_ENDPOINT = "/delete-version";
    public static final String PURGE_VERSION_ENDPOINT = "/purge-version";
    public static final String CONSOLIDATE_SNAPSHOT_ENDPOINT = "/consolidate-snapshot";
    public static final String ANALYZE_ENDPOINT = "/analyze";

    public static String getRootPath() {
        return ROOT_PATH + "/";
    }

    public static String getApplicationsPath() {
        return ROOT_PATH + APPLICATIONS_ENDPOINT;
    }

    public static String getApplicationPath(String appGuid) {
        assert appGuid != null && !appGuid.isEmpty();

        return getApplicationsPath() + "/" + appGuid;
    }

    public static String getApplicationVersionsPath(String appGuid) {
        assert appGuid != null && !appGuid.isEmpty();
        return getApplicationPath(appGuid) + VERSIONS_ENDPOINT;
    }

    public static String getApplicationCreateUploadPath(String appGuid) {
        assert appGuid != null && !appGuid.isEmpty();

        return getApplicationPath(appGuid) + UPLOAD_ENDPOINT;
    }

    public static String getApplicationUploadPath(String appGuid, String uploadGuid) {
        assert appGuid != null && !appGuid.isEmpty();
        assert uploadGuid != null && !uploadGuid.isEmpty();

        return getApplicationCreateUploadPath(appGuid) + "/" + uploadGuid;
    }

    public static String getApplicationExtractUploadPath(String appGuid, String uploadGuid) {
        return getApplicationUploadPath(appGuid, uploadGuid) + EXTRACT_ENDPOINT;
    }

    public static String getDebugOptionsPath(String appGuid) {
        return getApplicationPath(appGuid) + DEBUG_OPTIONS_ENDPOINT;
    }

    public static String getDebugOptionShowSqlPath(String appGuid) {
        return getDebugOptionsPath(appGuid) + SHOW_SQL_ENDPOINT;
    }

    public static String getDebugOptionAmtProfilePath(String appGuid) {
        return getDebugOptionsPath(appGuid) + AMT_PROFILE_ENDPOINT;
    }

    public static String getAmtProfilingDownloadUrl(String appGuid) {
        return ApiEndpointHelper.getDebugOptionsPath(appGuid) + "/logs" + AMT_PROFILE_ENDPOINT + "/download";
    }

    public static String getJobsEndpoint() {
        return ROOT_PATH + JOBS_ENDPOINT;
    }

    public static String getCreateApplicationEndPoint() {
        return getJobsEndpoint() + CREATE_APPLICATION_ENDPOINT;
    }

    public static String getJobsEndpoint(CreateJobsRequest jobRequest) throws JobServiceException {
        switch (jobRequest.getJobType()) {
            case ADD_VERSION:
                return getJobsEndpoint() + ADD_VERSION_ENDPOINT;
            case CLONE_VERSION:
                return getJobsEndpoint() + CLONE_VERSION_ENDPOINT;
            case DATAFLOW_SECURITY_ANALYZE:
                return getJobsEndpoint() + ANALYZE_SECURITY_DATAFLOW;
            case RESCAN_APPLICATION:
                return getJobsEndpoint() + RESYNC_APPLICATION;
            case UPLOAD_SNAPSHOT_VERSION:
                return getJobsEndpoint() + RESYNC_APPLICATION;
            case SHERLOCK_BACKUP:
                return getJobsEndpoint() + SHERLOCK_BACKUP_ENDPOINT;
            case RESTORE:
                return getJobsEndpoint() + RESTORE_ENDPOINT;
            case BACKUP:
                return getJobsEndpoint() + BACKUP_ENDPOINT;
            case REJECT_VERSION:
                return getJobsEndpoint() + REJECT_VERSION_ENDPOINT;
            case PURGE_VERSION:
                return getJobsEndpoint() + PURGE_VERSION_ENDPOINT;
            case DELETE_VERSION:
                return getJobsEndpoint() + DELETE_VERSION_ENDPOINT;
            case CONSOLIDATE_SNAPSHOT:
                return getJobsEndpoint() + CONSOLIDATE_SNAPSHOT_ENDPOINT;
            case ANALYZE:
                return getJobsEndpoint() + ANALYZE_ENDPOINT;
        }
        throw new JobServiceException("Undefined AIP Console job endpoint: " + jobRequest.getJobType().toString());
    }

    public static String getJobDetailsEndpoint(String jobGuid) {
        assert jobGuid != null && !jobGuid.isEmpty();

        return getJobsEndpoint() + "/" + jobGuid;
    }
}
