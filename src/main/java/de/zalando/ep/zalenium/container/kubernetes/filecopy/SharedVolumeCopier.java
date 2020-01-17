package de.zalando.ep.zalenium.container.kubernetes.filecopy;

import de.zalando.ep.zalenium.streams.InputStreamGroupIterator;
import de.zalando.ep.zalenium.streams.MapInputStreamAdapter;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.AllArgsConstructor;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


@AllArgsConstructor
public class SharedVolumeCopier implements PodFileCopy {

    private KubernetesClient client;

    public InputStreamGroupIterator copyFiles(String containerId, String folderName) {
        Map<String, File> streams = new HashMap<>();

        Optional<String> oWorkDir = client.pods().withName(containerId).get()
                .getSpec().getContainers().get(0).getEnv()
                .stream()
                .filter(env -> env.getName().equals("SHARED_DIR"))
                .map(env -> env.getValue())
                .findFirst();

        if (!oWorkDir.isPresent()) {
            throw new RuntimeException("SHARED_DIR not present in pod" + containerId);
        }
        String workDir = oWorkDir.get();

        File dir = new File(workDir + folderName);
        File[] directoryListing = dir.listFiles();
        for (File f : directoryListing != null ? directoryListing : new File[0]) {
            if (f.getName().endsWith(".log") || f.getName().endsWith(".mp4")) {
                streams.put(f.getName(), f);
            }
        }

        return new MapInputStreamAdapter(streams);
    }
}
