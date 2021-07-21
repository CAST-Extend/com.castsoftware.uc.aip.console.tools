package com.castsoftware.aip.console.tools.core.services;

import com.castsoftware.aip.console.tools.core.dto.ApiInfoDto;
import com.castsoftware.aip.console.tools.core.dto.DebugOptionsDto;
import com.castsoftware.aip.console.tools.core.dto.JsonDto;
import com.castsoftware.aip.console.tools.core.exceptions.ApiCallException;
import com.castsoftware.aip.console.tools.core.exceptions.ApplicationServiceException;
import com.castsoftware.aip.console.tools.core.utils.ApiEndpointHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.java.Log;

@Log
public class DebugOptionsServiceImpl implements DebugOptionsService {
    private RestApiService restApiService;

    public DebugOptionsServiceImpl(RestApiService restApiService) {
        this.restApiService = restApiService;
    }

    @Override
    public DebugOptionsDto getDebugOptions(String appGuid) throws ApplicationServiceException {
        try {
            return restApiService.getForEntity(ApiEndpointHelper.getDebugOptionsPath(appGuid), new TypeReference<DebugOptionsDto>() {
            });
        } catch (ApiCallException e) {
            log.info("Debug options not available for the target AIP Console version");
            return DebugOptionsDto.builder().build();
        }
    }

    @Override
    public DebugOptionsDto updateDebugOptions(String appGuid, DebugOptionsDto newDebugOptions) throws ApplicationServiceException {
        //==============
        // Ony set debug option when ON. Run Analysis always consider these options as OFF(disabled)
        //==============
        DebugOptionsDto oldDebugOptions = getDebugOptions(appGuid);
        if (newDebugOptions.isShowSql()) {
            updateShowSqlDebugOption(appGuid, newDebugOptions.isShowSql());
        }
        if (newDebugOptions.isActivateAmtMemoryProfile()) {
            updateAmtProfileDebugOption(appGuid, newDebugOptions.isActivateAmtMemoryProfile());
        }
        return oldDebugOptions;
    }

    @Override
    public void resetDebugOptions(String appGuid, DebugOptionsDto debugOptionsDto) throws ApplicationServiceException {
        updateShowSqlDebugOption(appGuid, debugOptionsDto.isShowSql());
        updateAmtProfileDebugOption(appGuid, debugOptionsDto.isActivateAmtMemoryProfile());
    }

    private void updateShowSqlDebugOption(String appGuid, boolean showSql) throws ApplicationServiceException {
        try {
            restApiService.putForEntity(ApiEndpointHelper.getDebugOptionShowSqlPath(appGuid), JsonDto.of(showSql), String.class);
        } catch (ApiCallException e) {
            log.info("Debug options not available for the target AIP Console version");
        }
    }

    private void updateAmtProfileDebugOption(String appGuid, boolean amtProfile) throws ApplicationServiceException {
        try {
            //--------------------------------------------------------------
            //The PUT shouldn't returned anything than void.class, but doing so clashed as object mapper is trying to map
            //Some response body. The response interpreter here does behave as expected.
            //Using String.class prevents from type clash (!#?)
            //--------------------------------------------------------------
            restApiService.putForEntity(ApiEndpointHelper.getDebugOptionAmtProfilePath(appGuid), JsonDto.of(amtProfile), String.class);
        } catch (ApiCallException e) {
            log.info("Debug options not available for the target AIP Console version");
        }
    }
}
