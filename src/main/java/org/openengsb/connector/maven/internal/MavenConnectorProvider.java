package org.openengsb.connector.maven.internal;

import org.openengsb.core.api.descriptor.ServiceDescriptor;
import org.openengsb.core.api.descriptor.ServiceDescriptor.Builder;
import org.openengsb.core.common.AbstractConnectorProvider;

public class MavenConnectorProvider extends AbstractConnectorProvider {

    @Override
    public ServiceDescriptor getDescriptor() {
        Builder builder = ServiceDescriptor.builder(strings);
        builder.id(this.id);
        builder.name("service.name").description("service.description");
        builder.attribute(builder.newAttribute().id("projectPath").name("service.projectPath.name")
            .description("service.projectPath.description").required().build());
        builder.attribute(builder.newAttribute().id("command").name("service.command.name")
            .description("service.command.description").required().build());
        builder.attribute(builder.newAttribute().id("mvnVersion").name("service.mvnVersion.name")
                .description("service.mvnVersion.description").required().build());
        return builder.build();
    }

}
