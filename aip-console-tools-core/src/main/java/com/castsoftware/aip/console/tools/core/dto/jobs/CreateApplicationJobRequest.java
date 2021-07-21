package com.castsoftware.aip.console.tools.core.dto.jobs;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApplicationJobRequest {
    private String appGuid;
    private String appName;
    private String domainName;
    @Builder.Default
    private boolean inPlaceMode = false;
    private String caipVersion;
    private String cssGuid;
}
