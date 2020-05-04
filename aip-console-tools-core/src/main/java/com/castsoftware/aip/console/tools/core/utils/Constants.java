package com.castsoftware.aip.console.tools.core.utils;

import java.util.regex.Pattern;

public class Constants {

    private Constants() {
        // NOP
    }

    // Return Codes
    public static final int RETURN_OK = 0;
    public static final int RETURN_NO_PASSWORD = 1;
    public static final int RETURN_LOGIN_ERROR = 2;
    public static final int RETURN_UPLOAD_ERROR = 3;
    public static final int RETURN_JOB_POLL_ERROR = 4;
    public static final int RETURN_JOB_FAILED = 5;
    public static final int RETURN_APPLICATION_INFO_MISSING = 6;
    public static final int RETURN_APPLICATION_NOT_FOUND = 7;
    public static final int RETURN_SOURCE_FOLDER_NOT_FOUND = 8;

    public static final int UNKNOWN_ERROR = 1000;

    // Utils
    public static final Pattern AIP_VERSION_PATTERN = Pattern.compile("^(1\\.9\\.1-SNAPSHOT)|(1\\.[6-9]\\.\\d)");
    public static final String API_KEY_HEADER = "X-API-KEY";

    // Job names
    public static final String ANALYZE = "analyze";
    public static final String REJECT_VERSION = "reject_version";
    public static final String DELETE_VERSION = "delete_version";
    public static final String PURGE_VERSION = "purge_version";
    public static final String ADD_VERSION = "add_version";
    public static final String CLONE_VERSION = "clone_version";

    public static final String DELETE_SNAPSHOT = "delete_snapshot";
    public static final String CONSOLIDATE_SNAPSHOT = "consolidate_snapshot";
    public static final String UPLOAD_SNAPSHOTS = "upload_snapshots";
    public static final String DECLARE_APPLICATION = "declare_application";
    public static final String DELETE_APPLICATION = "delete_application";
    public static final String FUNCTION_POINTS = "function_points";
    public static final String BACKUP = "BACKUP";
    public static final String RESTORE = "RESTORE";
    public static final String SHERLOCK_BACKUP = "SHERLOCK_BACKUP";
    public static final String DELIVER_VERSION = "deliver_version";
    public static final String UPLOAD_DELIVER_VERSION = "upload_deliver_version";
    public static final String RESCAN_APPLICATION = "rescan_application";
    public static final String UPLOAD_SNAPSHOT_VERSION = "upload_snapshot_version";
    public static final String DATAFLOW_SECURITY_ANALYZE = "dataflow_security_analyze";

    // Job params
    public static final String PARAM_APP_NAME = "appName";
    public static final String PARAM_NODE_GUID = "nodeGuid";
    public static final String PARAM_VERSION_NAME = "versionName";
    public static final String PARAM_SNAPSHOT_CAPTURE_DATE = "snapshotCaptureDate";
    public static final String PARAM_START_STEP = "startStep";
    public static final String PARAM_END_STEP = "endStep";
    public static final String PARAM_APP_GUID = "appGuid";
    public static final String PARAM_SUSPEND = "suspend";
    public static final String PARAM_IGNORE_CHECK = "ignoreFileStructureCheck";
    public static final String PARAM_RELEASE_DATE = "releaseDate";
    public static final String PARAM_SOURCE_ARCHIVE = "sourceArchive";
    public static final String PARAM_SOURCE_FOLDER = "sourceFolder";
    public static final String PARAM_FILENAME = "fileName";
    public static final String PARAM_VERSION_OBJECTIVES = "objectives";
    public static final String PARAM_SOURCE_PATH = "sourcePath";

    // Job Step Names
    public static final String EXTRACT_STEP_NAME = "unzip_source";
    public static final String CODE_SCANNER_STEP_NAME = "code_scanner";
    public static final String SET_CURRENT_STEP_NAME = "setcurrent";
    public static final String ACCEPTANCE_STEP_NAME = "accept";
    public static final String ANALYSIS_STEP_NAME = "analyze";
    public static final String SNAPSHOT_STEP_NAME = "snapshot";
    public static final String CONSOLIDATE_STEP_NAME = "consolidate_snapshot";

    // Other constants
    public static final long DEFAULT_HTTP_TIMEOUT = 90L;
}

