package com.castsoftware.aip.console.tools.core.dto.jobs;

import com.castsoftware.aip.console.tools.core.utils.Constants;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum JobType {

    ANALYZE(Constants.ANALYZE, "Run analysis"),

    REJECT_VERSION(Constants.REJECT_VERSION, "Reject version"),

    DELETE_VERSION(Constants.DELETE_VERSION, "Delete version"),

    PURGE_VERSION(Constants.PURGE_VERSION, "Purge version"),

    DELETE_SNAPSHOT(Constants.DELETE_SNAPSHOT, "Delete snapshot"),

    UPLOAD_SNAPSHOTS(Constants.UPLOAD_SNAPSHOTS, "Upload snapshots"),

    CONSOLIDATE_SNAPSHOT(Constants.CONSOLIDATE_SNAPSHOT, "Consolidate snapshot"),

    CREATE_APPLICATION(Constants.CREATE_APPLICATION, "Register application"),

    DELETE_APPLICATION(Constants.DELETE_APPLICATION, "Delete application"),

    FUNCTION_POINTS(Constants.FUNCTION_POINTS, "Compute function points"),

    DELIVER_VERSION(Constants.DELIVER_VERSION, "Create and deliver version"),

    BACKUP(Constants.BACKUP, "backup"),

    RESTORE(Constants.RESTORE, "restore"),

    SHERLOCK_BACKUP(Constants.SHERLOCK_BACKUP, "Create a Sherlock backup"),

    UPLOAD_DELIVER_VERSION(Constants.UPLOAD_DELIVER_VERSION, "Unzip previously uploaded source and deliver version"),

    UPLOAD_SNAPSHOT_VERSION(Constants.UPLOAD_SNAPSHOT_VERSION, "Unzip previously uploaded source, create version, analyze and create a snapshot"),

    RESCAN_APPLICATION(Constants.RESCAN_APPLICATION, "Clone previous version, launch analysis and create snapshot"),

    ADD_VERSION(Constants.ADD_VERSION, "Add a new version"),

    CLONE_VERSION(Constants.CLONE_VERSION, "Clone an existing version's structure"),

    DATAFLOW_SECURITY_ANALYZE(Constants.DATAFLOW_SECURITY_ANALYZE, "Run dataflow security analyzer");

    private final String label;
    private final String displayName;

    JobType(String label, String displayName) {
        this.label = label;
        this.displayName = displayName;
    }

    @JsonCreator
    public static JobType fromString(String value) {
        return value == null ? null : JobType.valueOf(value.toUpperCase());
    }

    public String getLabel() {
        return label;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase();
    }

    public String getSerializableValue() {
        return toString();
    }
}
