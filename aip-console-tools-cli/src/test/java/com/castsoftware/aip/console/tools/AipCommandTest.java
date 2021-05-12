package com.castsoftware.aip.console.tools;

import com.castsoftware.aip.console.tools.factories.SpringAwareCommandFactory;
import org.junit.After;
import org.junit.Before;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import picocli.CommandLine;

import java.lang.reflect.ParameterizedType;
import java.util.concurrent.Callable;

public abstract class AipCommandTest<T extends Callable<Integer>> {
    protected T aipCommand;
    protected AnnotationConfigApplicationContext context;
    protected CommandLine aipCommandLine;
    private Class<T> classType;
    
    protected static final String TEST_CREATRE_APP = "To_Create_App-name";
    protected static final String TEST_API_KEY = "API-Key";


    @Before
    public void startup() {
        load(AipIntegrationCliMain.class);
        CommandLine.IFactory factory = this.context.getBean(SpringAwareCommandFactory.class);
        //Works well for our simple implementation
        ParameterizedType parameterizedType = (ParameterizedType) getClass().getGenericSuperclass();
        classType = (Class<T>) parameterizedType.getActualTypeArguments()[0];

        aipCommand = this.context.getBean(classType);
        aipCommandLine = new CommandLine(aipCommand, factory);
    }

    @After
    public void tearDown() {
        if (this.context != null) {
            this.context.close();
        }
    }

    protected void load(Class<?> config, String... environment) {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
        //EnvironmentTestUtils.addEnvironment(applicationContext, environment);
        applicationContext.register(config);
        applicationContext.refresh();
        this.context = applicationContext;
    }

}
