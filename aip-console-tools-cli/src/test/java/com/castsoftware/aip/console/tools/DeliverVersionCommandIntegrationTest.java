package com.castsoftware.aip.console.tools;

import com.castsoftware.aip.console.tools.commands.DeliverVersionCommand;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobExecutionDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobRequestBuilder;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.exceptions.ApplicationServiceException;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;
import com.castsoftware.aip.console.tools.core.exceptions.PackagePathInvalidException;
import com.castsoftware.aip.console.tools.core.exceptions.UploadException;
import com.castsoftware.aip.console.tools.core.utils.Constants;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import picocli.CommandLine;

import java.io.File;
import java.util.Date;
import java.util.function.Function;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = {AipConsoleToolsCliIntegrationTest.class})
@ActiveProfiles(TestConstants.PROFILE_INTEGRATION_TEST)
public class DeliverVersionCommandIntegrationTest extends AipConsoleToolsCliBaseTest {
    @Autowired
    private DeliverVersionCommand deliverVersionCommand;

    @Override
    protected void cleanupTestCommand() {
        // ===================================
        //command not recreated between test.
        //So just clear the command as if it was brand newly created
        //The best way is to run java -jar .\aip-console-tools-cli.jar add ...
        // So that app creates new instances of commands.
        // Still this woks fine renewing parameters values each time.
        // Here only String types, but each test should set velues to requested ones
        // ===================================
        resetSharedOptions(deliverVersionCommand.getSharedOptions());
        deliverVersionCommand.setApplicationName(null);
        deliverVersionCommand.setBackupName(null);
        deliverVersionCommand.setDomainName(null);
        deliverVersionCommand.setFilePath(null);
        deliverVersionCommand.setNodeName(null);
        deliverVersionCommand.setVersionName(null);
    }

    @Test
    public void testDeliverVersionCommand_AddJobVersionFailed() throws ApplicationServiceException, UploadException, JobServiceException, PackagePathInvalidException {
        String[] args = new String[]{"--apikey",
                TestConstants.TEST_API_KEY,
                "--app-name=" + TestConstants.TEST_CREATRE_APP,
                "--file", sflPath.toString(),
                "--version-name", TestConstants.TEST_VERSION_NAME,
                "--no-clone", "--auto-create", "--enable-security-dataflow", "--backup",
                "--backup-name", TestConstants.TEST_BACKUP_NAME,
                "--domain-name", TestConstants.TEST_DOMAIN};

        // gives the existing application
        when(applicationService.getOrCreateApplicationFromName(any(String.class), anyBoolean(), eq(null), any(String.class), anyBoolean())).thenReturn(TestConstants.TEST_APP_GUID);
        when(applicationService.getApplicationNameFromGuid(TestConstants.TEST_APP_GUID)).thenReturn(TestConstants.TEST_CREATRE_APP);
        when(applicationService.getApplicationFromName(TestConstants.TEST_CREATRE_APP)).thenReturn(AipConsoleToolsCliBaseTest.simplifiedModeApp);
        when(uploadService.uploadFileAndGetSourcePath(any(String.class), any(String.class), any(File.class))).thenReturn(sflPath.toString());
        when(applicationService.applicationHasVersion(TestConstants.TEST_APP_GUID)).thenReturn(false);
        when(applicationService.createDeliveryConfiguration(TestConstants.TEST_APP_GUID, sflPath.toString(), null, false)).thenReturn(TestConstants.TEST_DELIVERY_CONFIG_GUID);
        when(jobsService.startAddVersionJob(any(JobRequestBuilder.class))).thenReturn(null);

        JobExecutionDto jobStatus = new JobExecutionDto();
        jobStatus.setAppGuid(TestConstants.TEST_APP_GUID);
        jobStatus.setState(JobState.FAILED);
        jobStatus.setCreatedDate(new Date());
        jobStatus.setAppName(TestConstants.TEST_CREATRE_APP);
        when(jobsService.pollAndWaitForJobFinished(eq(null), any(Function.class), anyBoolean())).thenReturn(jobStatus);

        runStringArgs(deliverVersionCommand, args);

        CommandLine.Model.CommandSpec spec = cliToTest.getCommandSpec();
        assertThat(spec, is(notNullValue()));
        assertThat(exitCode, is(Constants.RETURN_JOB_FAILED));
    }

    @Test
    public void testDeliverVersionCommand_JobCompleted() throws ApplicationServiceException, UploadException, JobServiceException, PackagePathInvalidException {
        boolean verbose = true;
        String[] args = new String[]{"--apikey",
                TestConstants.TEST_API_KEY,
                "--app-name=" + TestConstants.TEST_CREATRE_APP,
                "--file", sflPath.toString(),
                "--version-name", TestConstants.TEST_VERSION_NAME,
                "--node-name", TestConstants.TEST_NODE,
                "--no-clone", "--auto-create", "--enable-security-dataflow", "--backup",
                "--backup-name", TestConstants.TEST_BACKUP_NAME,
                "--domain-name", TestConstants.TEST_DOMAIN};

        // gives the existing application
        when(applicationService.getOrCreateApplicationFromName(any(String.class), anyBoolean(), any(String.class), any(String.class), anyBoolean())).thenReturn(TestConstants.TEST_APP_GUID);
        when(applicationService.getApplicationNameFromGuid(TestConstants.TEST_APP_GUID)).thenReturn(TestConstants.TEST_CREATRE_APP);
        when(applicationService.getApplicationFromName(TestConstants.TEST_CREATRE_APP)).thenReturn(AipConsoleToolsCliBaseTest.simplifiedModeApp);
        when(uploadService.uploadFileAndGetSourcePath(any(String.class), any(String.class), any(File.class))).thenReturn(sflPath.toString());
        when(applicationService.applicationHasVersion(TestConstants.TEST_APP_GUID)).thenReturn(false);
        when(applicationService.createDeliveryConfiguration(TestConstants.TEST_APP_GUID, sflPath.toString(), null, false)).thenReturn(TestConstants.TEST_DELIVERY_CONFIG_GUID);
        when(jobsService.startAddVersionJob(any(JobRequestBuilder.class))).thenReturn(TestConstants.TEST_JOB_GUID);

        JobExecutionDto jobStatus = new JobExecutionDto();
        jobStatus.setAppGuid(TestConstants.TEST_APP_GUID);
        jobStatus.setState(JobState.COMPLETED);
        jobStatus.setCreatedDate(new Date());
        jobStatus.setAppName(TestConstants.TEST_CREATRE_APP);
        when(jobsService.pollAndWaitForJobFinished(anyString(), any(Function.class), anyBoolean())).thenReturn(jobStatus);

        runStringArgs(deliverVersionCommand, args);

        CommandLine.Model.CommandSpec spec = cliToTest.getCommandSpec();
        assertThat(spec, is(notNullValue()));
        assertThat(exitCode, is(Constants.RETURN_OK));
    }
}
