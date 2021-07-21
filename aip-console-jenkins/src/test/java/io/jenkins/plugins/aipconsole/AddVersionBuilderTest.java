package io.jenkins.plugins.aipconsole;

import com.castsoftware.aip.console.tools.core.dto.ApiInfoDto;
import com.castsoftware.aip.console.tools.core.dto.ApplicationDto;
import com.castsoftware.aip.console.tools.core.dto.NodeDto;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobRequestBuilder;
import com.castsoftware.aip.console.tools.core.dto.jobs.JobState;
import com.castsoftware.aip.console.tools.core.exceptions.ApiCallException;
import com.castsoftware.aip.console.tools.core.exceptions.JobServiceException;
import com.castsoftware.aip.console.tools.core.exceptions.UploadException;
import com.castsoftware.aip.console.tools.core.services.ApplicationService;
import com.castsoftware.aip.console.tools.core.services.JobsService;
import com.castsoftware.aip.console.tools.core.services.RestApiService;
import com.castsoftware.aip.console.tools.core.services.UploadService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Lists;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.tasks.Builder;
import hudson.util.Secret;
import io.jenkins.plugins.aipconsole.config.AipConsoleGlobalConfiguration;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Future;

import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_appNotFound;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_jobFailure;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_jobServiceException;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_nodeNotFound;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_error_uploadFailed;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_info_appNotFoundAutoCreate;
import static io.jenkins.plugins.aipconsole.Messages.AddVersionBuilder_AddVersion_success_analysisComplete;
import static io.jenkins.plugins.aipconsole.Messages.CreateApplicationBuilder_CreateApplication_error_jobServiceException;
import static io.jenkins.plugins.aipconsole.Messages.GenericError_error_accessDenied;
import static io.jenkins.plugins.aipconsole.Messages.GenericError_error_missingRequiredParameters;
import static io.jenkins.plugins.aipconsole.Messages.GenericError_error_noApiKey;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@RunWith(MockitoJUnitRunner.class)
public class AddVersionBuilderTest {
    private static final String TEST_URL = "http://localhost:8081";
    private static final String TEST_KEY = "key";
    private static final String TEST_CONTENT = "test";
    private static final String TEST_APP_NAME = "appName";
    private static final String TEST_ARCHIVE_NAME = "archive.zip";
    private static final String TEST_JOB_GUID = "jobGuid";
    private static final String TEST_NODE_NAME = "nodeName";
    private static final ApplicationDto TEST_APP = ApplicationDto.builder().name(TEST_APP_NAME).guid(TEST_APP_NAME).inPlaceMode(false).build();

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private RestApiService restApiService;

    @Mock
    private UploadService uploadService;

    @Mock
    private JobsService jobsService;

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private AddVersionBuilder addVersionBuilder;

    @Before
    public void setUp() throws Exception {
        AipConsoleGlobalConfiguration config = AipConsoleGlobalConfiguration.get();
        config.setAipConsoleUrl(TEST_URL);
        config.setApiKey(Secret.fromString(TEST_KEY));
        addVersionBuilder = new AddVersionBuilder(TEST_APP_NAME, TEST_ARCHIVE_NAME);
        MockitoAnnotations.initMocks(this);
        doReturn(ApiInfoDto.builder().apiVersion("1.12.0-DEV").build())
                .when(restApiService).getAipConsoleApiInfo();
        doReturn(TEST_APP)
                .when(applicationService).getApplicationFromGuid(TEST_APP_NAME);
    }

    @Test
    public void testAddVersionStepToJob() throws Exception {
        FreeStyleProject project = getProjectWithDefaultAddVersion();
        project = jenkins.configRoundtrip(project);
        AddVersionBuilder job = new AddVersionBuilder(TEST_APP_NAME, TEST_ARCHIVE_NAME);
        job.setDomainName("");
        jenkins.assertEqualDataBoundBeans(job, project.getBuildersList().get(0));
    }

    @Test
    public void testAddVersionStepToJob_TargetingPrivateConsole() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(addVersionBuilder);
        addVersionBuilder.setApiKey(Secret.fromString("Z-Y-X"));
        //addVersionBuilder.setAipConsoleUrl("http://localhost:8083");
        AddVersionBuilder job = new AddVersionBuilder(TEST_APP_NAME, TEST_ARCHIVE_NAME);
        job.setApiKey(Secret.fromString("Z-Y-X"));
        job.setAipConsoleUrl(addVersionBuilder.getDescriptor().getAipConsoleUrl());
        jenkins.assertEqualDataBoundBeans(job, project.getBuildersList().get(0));
    }

    @Test
    public void testBuildFreestyleDefaultOk() throws Exception {
        FreeStyleProject project = getProjectWithDefaultAddVersionAndFile(TEST_ARCHIVE_NAME);

        doNothing().
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);
        doReturn(TEST_APP_NAME)
                .when(applicationService).getApplicationGuidFromName(TEST_APP_NAME);
        doReturn(TEST_APP)
                .when(applicationService).getApplicationFromName(TEST_APP_NAME);
        doReturn(true)
                .when(uploadService).uploadInputStream(eq(TEST_APP_NAME), anyString(), anyLong(), isA(InputStream.class));
        doReturn(TEST_JOB_GUID)
                .when(jobsService).startAddVersionJob(any(JobRequestBuilder.class));
        doReturn(JobState.COMPLETED)
                .when(jobsService).pollAndWaitForJobFinished(eq(TEST_JOB_GUID), any(), any(), any());

        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_success_analysisComplete(), build);
    }

    @Test
    @Ignore
    public void testBuildPipelineScriptOk() throws Exception {
        // Replace path separators with / for the groovy script
        String filePath = StringUtils.replaceChars(createTempFileAndGetPath(TEST_ARCHIVE_NAME), File.separatorChar, '/');
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("node {" +
                "  aipAddVersion applicationName: '" + TEST_APP_NAME + "', filePath: '" + filePath + "'" +
                "}", true)
        );

        doNothing().
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);
        doReturn(TEST_APP_NAME)
                .when(applicationService).getApplicationGuidFromName(TEST_APP_NAME);
        doReturn(true)
                .when(uploadService).uploadInputStream(eq(TEST_APP_NAME), anyString(), anyLong(), isA(InputStream.class));
        doReturn(TEST_JOB_GUID)
                .when(jobsService).startAddVersionJob(any(JobRequestBuilder.class));
        doReturn(JobState.COMPLETED)
                .when(jobsService).pollAndWaitForJobFinished(eq(TEST_JOB_GUID), any(), any(), any());

        WorkflowRun workflowRun = jenkins.buildAndAssertSuccess(job);
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_success_analysisComplete(), workflowRun);
    }

    @Test
    public void testBuildMissingRequiredStepParameters() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(new AddVersionBuilder("", ""));

        Future<FreeStyleBuild> futureBuild = project.scheduleBuild2(0);
        FreeStyleBuild build = jenkins.assertBuildStatus(Result.NOT_BUILT, futureBuild.get());
        jenkins.assertLogContains(GenericError_error_missingRequiredParameters(), build);
    }

    @Test
    public void testBuildMissingApiKeyParameter() throws Exception {
        FreeStyleProject project = getProjectWithDefaultAddVersion();
        AipConsoleGlobalConfiguration.get().setApiKey(null);

        Future<FreeStyleBuild> futureBuild = project.scheduleBuild2(0);
        FreeStyleBuild build = jenkins.assertBuildStatus(Result.NOT_BUILT, futureBuild.get());
        jenkins.assertLogContains(GenericError_error_noApiKey(), build);
    }

    @Test
    public void testBuildAipConsoleAccessError() throws Exception {
        FreeStyleProject project = getProjectWithDefaultAddVersionAndFile(TEST_ARCHIVE_NAME);
        doThrow(new ApiCallException(500)).
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);

        Future<FreeStyleBuild> futureBuild = project.scheduleBuild2(0);
        FreeStyleBuild build = jenkins.assertBuildStatus(Result.FAILURE, futureBuild.get());
        jenkins.assertLogContains(GenericError_error_accessDenied(TEST_URL), build);
    }

    @Test
    public void testBuildApplicationNotFoundAndAutoCreateFalse() throws Exception {
        Assert.assertFalse("Auto create should be false. Some changes were persisted to the builder ?", addVersionBuilder.isAutoCreate());
        addVersionBuilder.setFilePath(createTempFileAndGetPath(TEST_ARCHIVE_NAME));
        FreeStyleProject project = getProjectWithBuilder(addVersionBuilder);

        doNothing().
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);
        doReturn(null)
                .when(applicationService).getApplicationFromGuid(TEST_APP_NAME);

        Future<FreeStyleBuild> futureBuild = project.scheduleBuild2(0);
        FreeStyleBuild build = jenkins.assertBuildStatus(Result.FAILURE, futureBuild.get());
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_error_appNotFound(TEST_APP_NAME), build);
    }

    @Test
    public void testBuildApplicationUploadFailure() throws Exception {
        addVersionBuilder.setFilePath(createTempFileAndGetPath(TEST_ARCHIVE_NAME));
        FreeStyleProject project = getProjectWithBuilder(addVersionBuilder);

        doNothing().
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);
        doReturn(TEST_APP)
                .when(applicationService).getApplicationFromName(TEST_APP_NAME);
        doThrow(new UploadException("Fake error"))
                .when(uploadService).uploadInputStream(eq(TEST_APP_NAME), anyString(), anyLong(), isA(InputStream.class));
        Future<FreeStyleBuild> futureBuild = project.scheduleBuild2(0);
        FreeStyleBuild build = jenkins.assertBuildStatus(Result.FAILURE, futureBuild.get());
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_error_uploadFailed(), build);
    }

    @Test
    public void testBuildErrorCreatingAnalyzeJob() throws Exception {
        addVersionBuilder.setFilePath(createTempFileAndGetPath(TEST_ARCHIVE_NAME));
        FreeStyleProject project = getProjectWithBuilder(addVersionBuilder);

        doNothing().
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);
        doReturn(TEST_APP_NAME)
                .when(applicationService).getApplicationGuidFromName(TEST_APP_NAME);
        doReturn(TEST_APP)
                .when(applicationService).getApplicationFromName(TEST_APP_NAME);
        doReturn(true)
                .when(uploadService).uploadInputStream(eq(TEST_APP_NAME), anyString(), anyLong(), isA(InputStream.class));
        doThrow(new JobServiceException("fake exception"))
                .when(jobsService).startAddVersionJob(any(JobRequestBuilder.class));

        Future<FreeStyleBuild> futureBuild = project.scheduleBuild2(0);
        FreeStyleBuild build = jenkins.assertBuildStatus(Result.FAILURE, futureBuild.get());
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_error_jobServiceException(), build);
    }

    @Test
    public void testBuildAnalyseResultCancelled() throws Exception {
        addVersionBuilder.setFilePath(createTempFileAndGetPath(TEST_ARCHIVE_NAME));
        FreeStyleProject project = getProjectWithBuilder(addVersionBuilder);

        doNothing().
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);
        doReturn(TEST_APP)
                .when(applicationService).getApplicationFromName(TEST_APP_NAME);
        doReturn(true)
                .when(uploadService).uploadInputStream(eq(TEST_APP_NAME), anyString(), anyLong(), isA(InputStream.class));
        doReturn(TEST_JOB_GUID)
                .when(jobsService).startAddVersionJob(any(JobRequestBuilder.class));
        doReturn(JobState.CANCELED)
                .when(jobsService).pollAndWaitForJobFinished(eq(TEST_JOB_GUID), any(), any(), any());

        Future<FreeStyleBuild> futureBuild = project.scheduleBuild2(0);
        FreeStyleBuild build = jenkins.assertBuildStatus(Result.FAILURE, futureBuild.get());
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_error_jobFailure(JobState.CANCELED), build);
    }

    @Test
    public void testBuildApplicationNotFoundCreateApplicationFails() throws Exception {
        Assert.assertTrue("Node name should be null/empty. Other test set it and it wasn't reset ?",
                StringUtils.isBlank(addVersionBuilder.getNodeName()));
        addVersionBuilder.setFilePath(createTempFileAndGetPath(TEST_ARCHIVE_NAME));
        addVersionBuilder.setAutoCreate(true);
        FreeStyleProject project = getProjectWithBuilder(addVersionBuilder);

        doNothing().
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);
        doReturn(null)
                .when(applicationService).getApplicationGuidFromName(TEST_APP_NAME);
        doReturn(null)
                .when(applicationService).getApplicationFromName(TEST_APP_NAME);
        doThrow(new JobServiceException("cannot create application"))
                .when(jobsService).startCreateApplication(eq(TEST_APP_NAME), anyString(), anyBoolean(), anyString());

        Future<FreeStyleBuild> futureBuild = project.scheduleBuild2(0);
        FreeStyleBuild build = jenkins.assertBuildStatus(Result.FAILURE, futureBuild.get());
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_info_appNotFoundAutoCreate(TEST_APP_NAME), build);
        jenkins.assertLogContains(CreateApplicationBuilder_CreateApplication_error_jobServiceException(TEST_APP_NAME, TEST_URL), build);
    }

    @Test
    public void testBuildNodeNotFoundWhenCreatingApplication() throws Exception {
        addVersionBuilder.setFilePath(createTempFileAndGetPath(TEST_ARCHIVE_NAME));
        addVersionBuilder.setAutoCreate(true);
        addVersionBuilder.setNodeName(TEST_NODE_NAME);
        FreeStyleProject project = getProjectWithBuilder(addVersionBuilder);

        doNothing().
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);
        doReturn(null)
                .when(applicationService).getApplicationGuidFromName(TEST_APP_NAME);
        doReturn(new ArrayList<NodeDto>())
                .when(restApiService).getForEntity(eq("/api/nodes"), isA(TypeReference.class));

        Future<FreeStyleBuild> futureBuild = project.scheduleBuild2(0);
        FreeStyleBuild build = jenkins.assertBuildStatus(Result.FAILURE, futureBuild.get());
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_error_nodeNotFound(TEST_NODE_NAME), build);
    }

    @Test
    public void testBuildCreateAppOnNodeWithNameSuccess() throws Exception {
        addVersionBuilder.setFilePath(createTempFileAndGetPath(TEST_ARCHIVE_NAME));
        addVersionBuilder.setAutoCreate(true);
        addVersionBuilder.setNodeName(TEST_NODE_NAME);
        addVersionBuilder.setDomainName(null);
        FreeStyleProject project = getProjectWithBuilder(addVersionBuilder);

        doNothing().
                when(restApiService).validateUrlAndKey(TEST_URL, null, TEST_KEY);
        doReturn(null)
                .when(applicationService).getApplicationGuidFromName(TEST_APP_NAME);
        doReturn(Collections.singletonList(new NodeDto(TEST_NODE_NAME, TEST_NODE_NAME, "http", "localhost", 8082)))
                .when(restApiService).getForEntity(eq("/api/nodes"), isA(TypeReference.class));
        doReturn("createAppGuid")
                .when(jobsService).startCreateApplication(TEST_APP_NAME, TEST_NODE_NAME, null, false, null,null);
        doReturn(TEST_APP_NAME)
                .when(jobsService).pollAndWaitForJobFinished(eq("createAppGuid"), any(), any(), any());
        doReturn(true)
                .when(uploadService).uploadInputStream(eq(TEST_APP_NAME), anyString(), anyLong(), isA(InputStream.class));
        doReturn(TEST_JOB_GUID)
                .when(jobsService).startAddVersionJob(any(JobRequestBuilder.class));
        doReturn(JobState.COMPLETED)
                .when(jobsService).pollAndWaitForJobFinished(eq(TEST_JOB_GUID), any(), any(), any());

        FreeStyleBuild build = jenkins.buildAndAssertSuccess(project);
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_info_appNotFoundAutoCreate(TEST_APP_NAME), build);
        jenkins.assertLogContains(AddVersionBuilder_AddVersion_success_analysisComplete(), build);
    }

    private FreeStyleProject getProjectWithBuilder(Builder builder) throws IOException {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        project.getBuildersList().add(builder);
        return project;
    }

    private FreeStyleProject getProjectWithDefaultAddVersion() throws IOException {
        return getProjectWithBuilder(addVersionBuilder);
    }

    private FreeStyleProject getProjectWithDefaultAddVersionAndFile(String fileName) throws IOException {
        addVersionBuilder.setFilePath(createTempFileAndGetPath(fileName));
        return getProjectWithBuilder(addVersionBuilder);
    }

    private String createTempFileAndGetPath(String fileName) throws IOException {
        File tempFile = temporaryFolder.newFile(fileName);
        Files.write(tempFile.toPath(), Lists.newArrayList(TEST_CONTENT));
        return tempFile.getAbsolutePath();
    }
}