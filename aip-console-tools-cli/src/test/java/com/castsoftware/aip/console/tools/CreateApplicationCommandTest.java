package com.castsoftware.aip.console.tools;

import com.castsoftware.aip.console.tools.commands.CreateApplicationCommand;
import com.castsoftware.aip.console.tools.factories.SpringAwareCommandFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import picocli.CommandLine;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class CreateApplicationCommandTest {
    private static final String TEST_CREATRE_APP = "To_Create_App-name";
    private static final String TEST_API_KEY = "API-Key";

    private AnnotationConfigApplicationContext context;
    private CommandLine createAppCmdLine;
    private CreateApplicationCommand createAppCmd;

    @Before
    public void startup() {
        load(AipIntegrationCliMain.class);
        CommandLine.IFactory factory = this.context.getBean(SpringAwareCommandFactory.class);
        createAppCmd = this.context.getBean(CreateApplicationCommand.class);
        createAppCmdLine = new CommandLine(createAppCmd, factory);
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            this.context.close();
        }
    }
    
    @Test
    public void testCreateApplicationCommand() {
        String[] sb = new String[]{"new", "--apikey", TEST_API_KEY, "--app-name=" + TEST_CREATRE_APP, "--inplace-mode", "true"};
        createAppCmdLine.parseArgs(sb);
        assertEquals(TEST_API_KEY, createAppCmd.getSharedOptions().getApiKey());
        assertEquals(TEST_CREATRE_APP, createAppCmd.getApplicationName());
        assertEquals(true, createAppCmd.isInPlaceMode());
        assertEquals(true, createAppCmd.getSharedOptions().isVerbose());
    }
    @Test
    public void testCreateApplicationCommand_WithTargetCssServer() {
        String TARGET_CSS_SERVER_NAME="host.docker.internal:2285";
        String[] sb = new String[]{"new", "--apikey", TEST_API_KEY, "--app-name=" + TEST_CREATRE_APP
                , "--inplace-mode", "true", "-css",TARGET_CSS_SERVER_NAME};
        createAppCmdLine.parseArgs(sb);
        assertEquals(TEST_API_KEY, createAppCmd.getSharedOptions().getApiKey());
        assertEquals(TEST_CREATRE_APP, createAppCmd.getApplicationName());
        assertEquals(true, createAppCmd.isInPlaceMode());
        assertEquals(true, createAppCmd.getSharedOptions().isVerbose());
        assertEquals(TARGET_CSS_SERVER_NAME, createAppCmd.getCssServerName());
    }

    @Test
    public void testCreateApplicationCommand_withAlias() {
        String[] sb = new String[]{"new", "--apikey", TEST_API_KEY, "-n", TEST_CREATRE_APP, "--inplace-mode=true", "--verbose=false"};
        createAppCmdLine.parseArgs(sb);
        assertEquals(TEST_CREATRE_APP, createAppCmd.getApplicationName());
        assertEquals(true, createAppCmd.isInPlaceMode());
        assertEquals(false, createAppCmd.getSharedOptions().isVerbose());
    }

    @Test
    public void testCreateApplicationCommand_withParamWithoutValue() {
        String[] sb = new String[]{"new", "--apikey=" + TEST_API_KEY, "-n", TEST_CREATRE_APP, "--inplace-mode", "--verbose"};
        createAppCmdLine.parseArgs(sb);
        assertEquals(TEST_CREATRE_APP, createAppCmd.getApplicationName());
        assertEquals(true, createAppCmd.isInPlaceMode());
        assertEquals(true, createAppCmd.getSharedOptions().isVerbose());
    }

    @Test
    public void testCreateApplicationCommand_withDefaultParamValues() {
        String[] sb = new String[]{"new", "--apikey=API-Key", "-n", TEST_CREATRE_APP};
        createAppCmdLine.parseArgs(sb);
        assertEquals(TEST_CREATRE_APP, createAppCmd.getApplicationName());
        assertEquals(false, createAppCmd.isInPlaceMode());
        assertEquals(true, createAppCmd.getSharedOptions().isVerbose());
    }

    private void load(Class<?> config, String... environment) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(config);
        applicationContext.refresh();
        this.context = applicationContext;
    }


}
