package com.castsoftware.aip.console.tools.core.dto.jobs;

import com.castsoftware.aip.console.tools.core.utils.ApiEndpointHelper;
import com.castsoftware.aip.console.tools.core.utils.Constants;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobExecutionDto {
    private String guid;
    private Date createdDate;
    private Date modifiedDate;
    private JobType jobType;
    private String currentStep;
    private String startedBy;
    private Map<String, String> jobParameters = new HashMap<>();
    private List<JobStepExecutionDto> jobStepExecutions = new ArrayList<>();
    private JobState state = JobState.STARTING;
    private LocalDateTime startTime = null;
    private LocalDateTime endTime = null;
    public String getAppName() {
        return getJobParameters().getOrDefault(Constants.PARAM_APP_NAME,"");
    }
    public void setAppName(String appName) {
         getJobParameters().put(Constants.PARAM_APP_NAME,appName);
    }
    public String getAppGuid() {
        return getJobParameters().getOrDefault(Constants.PARAM_APP_GUID,"");
    }
    public  void setAppGuid(String appGuid) {
         getJobParameters().put(Constants.PARAM_APP_GUID,appGuid);
    }

}
