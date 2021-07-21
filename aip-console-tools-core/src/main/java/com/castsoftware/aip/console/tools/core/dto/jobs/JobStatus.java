package com.castsoftware.aip.console.tools.core.dto.jobs;


import com.castsoftware.aip.console.tools.core.dto.deserializers.FullStepsDeserializer;
import com.castsoftware.aip.console.tools.core.dto.serializers.StatusStringMapSerializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatus {

    private JobState state;

    private String jobType;

    private Map<String, String> jobParameters;

    private String guid;

    private String url;

    private String appGuid;

    private String appName;

    //@JsonDeserialize(using = FullStepsDeserializer.class)List<JobStepDto>
    private List<JobStepDto> fullSteps;

    private Map<String, String> logUrls;

    private Map<String, String> logNames;

    private Date created;

    private Date updated;

    public JobStatus() {
    }

    public JobStatus(String guid, String url, JobType type, JobState state, Map<String, String> parameters,
                     Date created, Date updated, List<JobStepDto> fullSteps, Map<String, String> logUrls, Map<String, String> logNames) {
        this.guid = guid;
        this.url = url;
        this.jobType = type.getSerializableValue();
        this.state = state;
        this.jobParameters = parameters;
        this.created = created;
        this.updated = updated;
        this.fullSteps = fullSteps;
        this.logUrls = logUrls;
        this.logNames = logNames;
    }

    public JobStatus(JobStatus status) {
        appGuid = status.getAppGuid();
        appName = status.getAppName();
        guid = status.getGuid();
        url = status.getUrl();
        jobType = status.getJobType();
        state = status.getState();
        jobParameters = status.getJobParameters();
        created = status.getCreated();
        updated = status.getUpdated();
        fullSteps = status.getFullSteps();
        logUrls = status.getLogUrls();
        logNames = status.getLogNames();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = StatusStringMapSerializer.class)
    public Map<String, String> getLogUrls() {
        return logUrls;
    }
}
