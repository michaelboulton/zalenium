package de.zalando.ep.zalenium.container.kubernetes.filecopy;

import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("WeakerAccess")
public final class CopyFilesExecListener implements ExecListener {

    private static final Logger logger = LoggerFactory.getLogger(CopyFilesExecListener.class.getName());

    private AtomicBoolean closedResource = new AtomicBoolean(false);
    private ExecWatch execWatch;
    private String containerId;
    private ByteArrayOutputStream stderr;
    private String[] command;
    private final CountDownLatch openLatch = new CountDownLatch(1);

    public CopyFilesExecListener(ByteArrayOutputStream stderr, String[] command, String containerId) {
        super();
        this.stderr = stderr;
        this.command = command;
        this.containerId = containerId;
    }

    public void setExecWatch(ExecWatch execWatch) {
        this.execWatch = execWatch;
    }

    @Override
    public void onOpen(Response response) {
        openLatch.countDown();
    }

    @Override
    public void onFailure(Throwable t,
                          Response response) {
        logger.error(String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)), t);
    }

    @Override
    public void onClose(int code,
                        String reason) {


        // Dirty hack to workaround the fact that ExecWatch doesn't automatically close any resources
        boolean isClosed = closedResource.getAndSet(true);
        boolean hasErrors = stderr.size() > 0;
        if (!isClosed && hasErrors) {
            logger.error(String.format("%s Copy files command failed with:\n\tcommand: %s\n\t stderr:\n%s",
                    containerId,
                    Arrays.toString(command),
                    stderr.toString()));
            this.execWatch.close();
        }
    }

    public void waitForInputStreamToConnect() {
        try {
            this.openLatch.await();
        } catch (InterruptedException e) {
            logger.error(String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)), e);
        }
    }
}
