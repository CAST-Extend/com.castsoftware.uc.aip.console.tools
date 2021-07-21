package com.castsoftware.aip.console.tools;

import com.castsoftware.aip.console.tools.commands.CreateApplicationCommand;
import com.castsoftware.aip.console.tools.core.dto.DatabaseConnectionSettingsDto;
import com.castsoftware.aip.console.tools.core.dto.NodeDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobExecutionDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.exceptions.ApiCallException;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;
import com.castsoftware.aip.console.tools.core.utils.Constants;
import org.assertj.core.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import picocli.CommandLine;

import java.util.Date;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = {AipConsoleToolsCliIntegrationTest.class})
@ActiveProfiles(TestConstants.PROFILE_INTEGRATION_TEST)
public class CreateApplicationCommandIntegrationTest extends AipConsoleToolsCliBaseTest {
    @Autowired
    private CreateApplicationCommand createApplicationCommand;

    @Override
    protected void cleanupTestCommand() {
        resetSharedOptions(createApplicationCommand.getSharedOptions());
        createApplicationCommand.setApplicationName(null);
        createApplicationCommand.setDomainName(null);
        createApplicationCommand.setNodeName(null);
        createApplicationCommand.setCssServerName(null);
    }

    @Test
    public void testCreateApplicationCommand_badNodeName() throws ApiCallException {
        String[] args = new String[]{"--apikey", TestConstants.TEST_API_KEY,
                "-n", TestConstants.TEST_CREATRE_APP,
                "--node-name", TestConstants.TEST_NODE};

        NodeDto[] existingNodes = new NodeDto[]{new NodeDto("Unexisting_guid", "Unexisting_name", "Fake_scheme", "Unexisting_host", 000)};
        when(restApiService.getForEntity("/api/nodes", NodeDto[].class)).thenReturn(existingNodes);

        runStringArgs(createApplicationCommand, args);

        CommandLine.Model.CommandSpec spec = cliToTest.getCommandSpec();
        assertThat(spec, is(notNullValue()));
        assertThat(exitCode, is(Constants.RETURN_APPLICATION_NOT_FOUND));
    }

    @Test
    public void testCreateApplicationCommand_SimplifiedDeliveryMode_DefaultNode() throws JobServiceException {
        String[] args = new String[]{"--apikey", TestConstants.TEST_API_KEY,
                "-n", TestConstants.TEST_CREATRE_APP,
                "--inplace-mode",
                "--domain-name", TestConstants.TEST_DOMAIN};

        // gives the existing application
        JobExecutionDto jobStatus = new JobExecutionDto();
        jobStatus.setAppGuid(TestConstants.TEST_APP_GUID);
        jobStatus.setState(JobState.COMPLETED);
        jobStatus.setCreatedDate(new Date());
        jobStatus.setAppName(TestConstants.TEST_CREATRE_APP);

        when(jobsService.startCreateApplication(any(String.class), eq(null), any(String.class), anyBoolean(), any(String.class), eq(null))).thenReturn(TestConstants.TEST_JOB_GUID);
        when(jobsService.pollAndWaitForJobFinished(any(String.class), any(Function.class), anyBoolean())).thenReturn(Constants.RETURN_OK);

        runStringArgs(createApplicationCommand, args);

        CommandLine.Model.CommandSpec spec = cliToTest.getCommandSpec();
        assertThat(spec, is(notNullValue()));
        assertThat(exitCode, is(Constants.RETURN_OK));
    }

    @Test
    public void testCreateApplicationCommand_SimplifiedDeliveryMode_WithCssServer() throws JobServiceException, ApiCallException {
        String TARGET_CSS_SERVER_NAME="host.docker.internal:2285";
        String[] args = new String[]{"--apikey", TestConstants.TEST_API_KEY,
                "-n", TestConstants.TEST_CREATRE_APP,
                "--inplace-mode",
                "--domain-name", TestConstants.TEST_DOMAIN
        , "--css-server",TARGET_CSS_SERVER_NAME};

        // gives the existing application
        JobExecutionDto jobStatus = new JobExecutionDto();
        jobStatus.setAppGuid(TestConstants.TEST_APP_GUID);
        jobStatus.setState(JobState.COMPLETED);
        jobStatus.setCreatedDate(new Date());
        jobStatus.setAppName(TestConstants.TEST_CREATRE_APP);

        DatabaseConnectionSettingsDto oneDb = new DatabaseConnectionSettingsDto();
        oneDb.setDatabaseName("postgres"); oneDb.setGuid("b6059ea8-cec9-4e62-86a6-065def8ebb69");
        oneDb.setHost("host.docker.internal");oneDb.setPort(2285);oneDb.setSslEnabled(false); oneDb.setUserName("operator");
        when(restApiService.getForEntity("api/settings/css-settings",DatabaseConnectionSettingsDto[].class))
                .thenReturn(Arrays.array(oneDb));
        when(jobsService.startCreateApplication(any(String.class), eq(null), any(String.class), anyBoolean(), any(String.class), eq("b6059ea8-cec9-4e62-86a6-065def8ebb69"))).thenReturn(TestConstants.TEST_JOB_GUID);
        when(jobsService.pollAndWaitForJobFinished(any(String.class), any(Function.class), anyBoolean())).thenReturn(Constants.RETURN_OK);

        runStringArgs(createApplicationCommand, args);

        CommandLine.Model.CommandSpec spec = cliToTest.getCommandSpec();
        assertThat(spec, is(notNullValue()));
        assertThat(exitCode, is(Constants.RETURN_OK));
    }
}
