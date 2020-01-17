package de.zalando.ep.zalenium.container.kubernetes.filecopy;

import de.zalando.ep.zalenium.streams.InputStreamGroupIterator;
import de.zalando.ep.zalenium.streams.TarInputStreamGroupWrapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import lombok.AllArgsConstructor;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;

@AllArgsConstructor
public class CommandCopier implements PodFileCopy {
    private KubernetesClient client;

    @NotNull
    @Contract("_, _ -> new")
    public InputStreamGroupIterator copyFiles(String containerId, String folderName) {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        String[] command = new String[]{"tar", "-C", folderName, "-c", "."};
        CopyFilesExecListener listener = new CopyFilesExecListener(stderr, command, containerId);
        ExecWatch exec = client.pods().withName(containerId).redirectingOutput().writingError(stderr).usingListener(listener).exec(command);

        // FIXME: This is a bit dodgy, but we need the listener to be able to close the ExecWatch in failure conditions,
        // because it doesn't cleanup properly and deadlocks.
        // Needs bugs fixed inside kubernetes-client.
        listener.setExecWatch(exec);

        // When zalenium is under high load sometimes the stdout isn't connected by the time we try to read from it.
        // Let's wait until it is connected before proceeding.
        listener.waitForInputStreamToConnect();

        return new TarInputStreamGroupWrapper(new TarArchiveInputStream(exec.getOutput()));
    }
}
