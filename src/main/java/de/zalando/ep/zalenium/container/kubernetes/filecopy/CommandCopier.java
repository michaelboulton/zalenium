package de.zalando.ep.zalenium.container.kubernetes.filecopy;

import de.zalando.ep.zalenium.streams.InputStreamGroupIterator;
import de.zalando.ep.zalenium.streams.TarInputStreamGroupWrapper;
import io.fabric8.kubernetes.api.model.DoneablePod;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Directly copies files form the container by accessing the Kubernetes API
 */
@AllArgsConstructor
@Slf4j
public class CommandCopier implements PodFileCopy {
    private KubernetesClient client;

    private static String convertInputStreamToString(@NotNull InputStream inputStream) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }

        return result.toString(StandardCharsets.UTF_8.name());

    }

    private InputStream readCommandOutput(String[] command, String containerId) {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        log.info("Running {}", Arrays.asList(command).toString());
        CopyFilesExecListener listener = new CopyFilesExecListener(stderr, command, containerId);
        PodResource<Pod, DoneablePod> pod = client.pods().withName(containerId);

//        val volumeMount = pod.get().getSpec().getContainers().get(0).getVolumeMounts().stream()
//                .filter(v -> v.getMountPath().startsWith("/tmp/mounted"))
//                .findFirst()
//                .get();
//
//        assert(false);
////        FIXME: need to just copy the whole folder...? folderName doesn't really say _where_ it is on the pod

        ExecWatch exec = pod.redirectingOutput().writingError(stderr).usingListener(listener).exec(command);

        // FIXME: This is a bit dodgy, but we need the listener to be able to close the ExecWatch in failure conditions,
        // because it doesn't cleanup properly and deadlocks.
        // Needs bugs fixed inside kubernetes-client.
        listener.setExecWatch(exec);

        // When zalenium is under high load sometimes the stdout isn't connected by the time we try to read from it.
        // Let's wait until it is connected before proceeding.
        listener.waitForInputStreamToConnect();

        return exec.getOutput();
    }

    private void listContents(String containerId, String folderName) {
        InputStream contents = readCommandOutput(new String[]{"ls", folderName}, containerId);

        String execOutput = null;
        try {
            execOutput = convertInputStreamToString(contents);
        } catch (IOException e) {
            log.error(e.toString());
            return;
        }
        log.info(execOutput);
    }

    @NotNull
    @Contract("_, _ -> new")
    public InputStreamGroupIterator copyFiles(String containerId, String folderName) {
        listContents(containerId, folderName);

        String[] command = new String[]{"tar", "-C", folderName, "-c", "."};

        InputStream execOutput = readCommandOutput(command, containerId);
        return new TarInputStreamGroupWrapper(new TarArchiveInputStream(execOutput));
    }
}
