package com.castsoftware.aip.console.tools.core.dto.jobs;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
public class JobStepExecutionDto {
    private String stepType;
    private Date startTime;
    private Date endTime;
}
