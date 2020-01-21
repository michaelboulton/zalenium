package de.zalando.ep.zalenium.container.kubernetes.filecopy;

import de.zalando.ep.zalenium.util.Environment;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;


/**
 * How files are copied back from selenium nodes
 */
@Slf4j
public enum FileCopyStrategy {
    FROM_COMMANDS {
        @Override
        public PodFileCopy construct(KubernetesClient client) {
            return new CommandCopier(client);
        }

        @Override
        public Volume volumeForNode(Volume original) {
//            Set empty dir instead of pvc
//            TODO: Is there a way with this builder API to just copy the name...
            Volume newVolume = VolumeBuilder.builderOf(original).build();
            newVolume.setEmptyDir(new EmptyDirVolumeSource());
            newVolume.setPersistentVolumeClaim(null);
            return newVolume;
        }
    },
    FROM_VOLUMES {
        @Override
        public PodFileCopy construct(KubernetesClient client) {
            return new SharedVolumeCopier(client);
        }

        @Override
        public Volume volumeForNode(Volume original) {
//            Nop
            return original;
        }
    };

    public abstract PodFileCopy construct(KubernetesClient client);

    public abstract Volume volumeForNode(Volume original);

    public static FileCopyStrategy fromEnvVar(Environment environment) {
        FileCopyStrategy strategy;

        String envVariable = environment.getStringEnvVariable("ZALENIUM_COPY_FILES_STRATEGY", null);
        if (envVariable == null) {
//            Best effort
            log.debug("Using shared volumes to copy files");
            strategy = FileCopyStrategy.FROM_VOLUMES;
        } else {
            switch (envVariable) {
                case "COMMAND":
                    log.debug("Using remote commands to copy files");
                    strategy = FileCopyStrategy.FROM_COMMANDS;
                    break;
                case "VOLUME":
                    log.debug("Using shared volumes to copy files");
                    strategy = FileCopyStrategy.FROM_VOLUMES;
                    break;
                default:
                    log.warn("Unknown setting {}, using shared volumes", envVariable);
                    strategy = FileCopyStrategy.FROM_VOLUMES;
            }
        }

        return strategy;
    }
}
