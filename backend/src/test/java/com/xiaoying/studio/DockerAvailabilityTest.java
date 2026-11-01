package com.xiaoying.studio;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerAvailabilityTest {
    @Test void dockerMustBePresentWhenExplicitlyRequired() {
        if(Boolean.getBoolean("requireDocker")) assertTrue(DockerClientFactory.instance().isDockerAvailable(),"MySQL container tests are required in CI");
    }
}
