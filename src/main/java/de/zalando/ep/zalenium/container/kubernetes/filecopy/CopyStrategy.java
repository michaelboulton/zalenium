package de.zalando.ep.zalenium.container.kubernetes.filecopy;

import de.zalando.ep.zalenium.util.Environment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;

public final class CopyStrategy {
    private static final Logger logger = LoggerFactory.getLogger(CopyStrategy.class.getName());

    private enum Strategy {
        FROM_COMMANDS {
            @Override
            public PodFileCopy construct(KubernetesClient client) {
                return new CommandCopier(client);
            }
        },
        FROM_VOLUMES {
            @Override
            public PodFileCopy construct(KubernetesClient client) {
                return new SharedVolumeCopier(client);
            }
        };

        public abstract PodFileCopy construct(KubernetesClient client);
    }

    @NotNull
    public static PodFileCopy getCopier(Environment environment, KubernetesClient client) {
        Strategy strategy;

        String envVariable = environment.getStringEnvVariable("ZALENIUM_COPY_FILES_STRATEGY", null);
        if (envVariable == null) {
//            Best effort
            strategy = Strategy.FROM_VOLUMES;
        } else {
            switch (envVariable) {
                case "COMMAND":
                    strategy = Strategy.FROM_COMMANDS;
                    break;
                case "VOLUME":
                    strategy = Strategy.FROM_VOLUMES;
                    break;
                default:
                    logger.warn("Unknown setting {}, using shared volumes", envVariable);
                    strategy = Strategy.FROM_VOLUMES;
            }
        }

        return strategy.construct(client);
    }
}
