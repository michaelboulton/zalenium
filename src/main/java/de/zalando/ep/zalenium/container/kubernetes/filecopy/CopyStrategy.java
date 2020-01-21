package de.zalando.ep.zalenium.container.kubernetes.filecopy;

import de.zalando.ep.zalenium.util.Environment;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;

@Slf4j
public final class CopyStrategy {
    @NotNull
    public static PodFileCopy getCopier(Environment environment, KubernetesClient client) {
        FileCopyStrategy fileCopyStrategy = FileCopyStrategy.fromEnvVar(environment);
        return fileCopyStrategy.construct(client);
    }
}
