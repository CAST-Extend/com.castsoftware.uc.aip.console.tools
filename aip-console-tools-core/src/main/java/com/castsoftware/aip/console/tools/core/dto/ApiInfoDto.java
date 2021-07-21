package com.castsoftware.aip.console.tools.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A DTO class for JSON message generation that contains the version of the api
 */
@Getter
@Setter
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiInfoDto {

    private String apiVersion = "";

    private String castVersion;

    private Map<String, Set<String>> dependencyMatrix = new HashMap<>();

    private boolean dashboardIntegrated = false;

    private boolean enableSnapshotValidation = false;

    private boolean enableDownloadDmt = false;

    private boolean enablePackagePathCheck = false;

    private SemVer apiVersionSemVer;

    // Whether Imaging is installed with the Flat
    private boolean imagingFlat = false;

    private boolean acStudioAudienceCastsoftware = false;

    public SemVer getApiVersionSemVer() {
        return getSemVer();
    }

    public boolean isExtractionRequired() {
        SemVer consoleVersion = getSemVer();
        // Extract for versions above 1.12
        return consoleVersion.getMajor() >= 1 && consoleVersion.getMinor() > 12 && consoleVersion.getMinor() < 19;
    }

    public boolean isSourcePathPrefixRequired() {
        SemVer consoleVersion = getSemVer();
        // Prefix is required for versions above 1.12 (?)
        return consoleVersion.getMajor() > 1 || consoleVersion.getMajor() == 1 && consoleVersion.getMinor() > 12;
    }

    public boolean isJobStatusWithDuration() {
        SemVer version = getSemVer();
        return version.getMajor() >= 1 && version.getMinor() >= 20;
    }

    public boolean isJobToBeResumed() {
        SemVer version = getSemVer();
        return version.getMajor() >= 1 && version.getMinor() <= 9;
    }

    public boolean isLastStepConsolidateSnapshot() {
        SemVer version = getSemVer();
        return version.getMajor() >= 1 && version.getMinor() <= 15;
    }

    private SemVer getSemVer() {
        if (apiVersionSemVer == null) {
            synchronized (this) {
                this.apiVersionSemVer = SemVer.parse(apiVersion);
            }
        }
        return apiVersionSemVer;
    }
}
