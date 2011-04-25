package org.openengsb.connector.maven.internal;

import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.openengsb.core.api.context.ContextCurrentService;
import org.openengsb.domain.build.BuildDomainEvents;
import org.openengsb.domain.deploy.DeployDomainEvents;
import org.openengsb.domain.test.TestDomainEvents;

public class MavenServiceTestUT {
    @Test
    public void testCreatePlaintextReportService() throws Exception {
        System.setProperty("karaf.data", ".");
        MavenServiceInstanceFactory factory = new MavenServiceInstanceFactory();
        factory.setBuildEvents(mock(BuildDomainEvents.class));
        factory.setTestEvents(mock(TestDomainEvents.class));
        factory.setDeployEvents(mock(DeployDomainEvents.class));
        factory.setContextService(mock(ContextCurrentService.class));

        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("projectPath", "someValue");
        attributes.put("mvnVersion", "3.0.3");
        
        MavenServiceImpl mavenService = (MavenServiceImpl) factory.createNewInstance("id");
        factory.applyAttributes(mavenService, attributes);

        Assert.assertNotNull(mavenService);
    }
}
