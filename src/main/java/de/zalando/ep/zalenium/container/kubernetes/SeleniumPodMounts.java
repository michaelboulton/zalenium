package de.zalando.ep.zalenium.container.kubernetes;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

/**
 * Tracks volumes mounted on a specific pod for copying back later
 *
 * Needed to get around the fact that it didn't used to track folder mounts properly and would not copy back the correct files
 */
@Data
@Builder
@Slf4j
@AllArgsConstructor
final public class SeleniumPodMounts {
    @NonNull
    private final String sharedMountFolder;
    @NonNull
    private final String videoMountFolder;
    @NonNull
    private final String logMountFolder;
    @NonNull
    private final String podFolderId;

    /**
     * Generate a uuid for a pod and create the folders to mount
     */
    public static SeleniumPodMounts createMounts(@NotNull String nodeSharedArtifactsMountPath) {
        String podUuid = UUID.randomUUID().toString();

        String workDir = nodeSharedArtifactsMountPath + "/" + podUuid;
        String videoDir = workDir + "/videos";
        String logDir = workDir + "/logs";

//        Create them here on the hub for copying back in to
        if (!Files.exists(Paths.get(workDir))) {
            Objects.requireNonNull(createDirectories(workDir));
            Objects.requireNonNull(createDirectories(videoDir));
            Objects.requireNonNull(createDirectories(logDir));
        }

        return SeleniumPodMounts.builder()
                .podFolderId(podUuid)
                .sharedMountFolder(workDir)
                .videoMountFolder(videoDir)
                .logMountFolder(logDir)
                .build();
    }

    @Nullable
    private static Path createDirectories(String dirName) {
        try {
            return Files.createDirectories(Paths.get(dirName));
        } catch (IOException e) {
            log.error("Error creating folder {}: {}", dirName, e);
            return null;
        }
    }

    /**
     * Hack to get the correct path to copy things from
     *
     * Because the docker selenium client has some hardcoded paths and its eaier to change here rather than rafactor it properly into an enum-type thing
     */
    public String getActualPath(@NotNull String desired) {
        if (desired.contains("video")) {
            return this.videoMountFolder;
        } else if (desired.contains("log")) {
            return this.logMountFolder;
        } else {
            log.warn("Unexpected desired path '{}'", desired);
            return this.sharedMountFolder;
        }
    }
}
