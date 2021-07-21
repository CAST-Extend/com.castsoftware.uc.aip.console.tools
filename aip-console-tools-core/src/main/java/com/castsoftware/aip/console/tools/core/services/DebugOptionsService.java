package com.castsoftware.aip.console.tools.core.services;

import com.castsoftware.aip.console.tools.core.dto.DebugOptionsDto;
import com.castsoftware.aip.console.tools.core.exceptions.ApplicationServiceException;

public interface DebugOptionsService {
    /**
     * Get the existing {@code }debug options} settings
     *
     * @param appGuid host application
     * @return debug options
     * @throws ApplicationServiceException
     */
    DebugOptionsDto getDebugOptions(String appGuid) throws ApplicationServiceException;

    /**
     *
     * @param appGuid
     * @param newDebugOptions
     * @return old debug options available before update
     * @throws ApplicationServiceException
     */
    DebugOptionsDto updateDebugOptions(String appGuid, DebugOptionsDto newDebugOptions) throws ApplicationServiceException;

    void resetDebugOptions(String appGuid, DebugOptionsDto debugOptionsDto) throws ApplicationServiceException;
}
