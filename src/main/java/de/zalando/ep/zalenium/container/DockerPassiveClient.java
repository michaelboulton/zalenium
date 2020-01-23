package de.zalando.ep.zalenium.container;

import de.zalando.ep.zalenium.proxy.DockeredSeleniumStarter;
import de.zalando.ep.zalenium.streams.InputStreamGroupIterator;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.*;


@Slf4j
public class DockerPassiveClient implements ContainerClient {
    @Setter
    private String nodeId;

    private List<ContainerClientRegistration> linkedContainers = new ArrayList<>();

    @Override
    @NotNull
    public ContainerClientRegistration registerNode(String zaleniumContainerName, @NotNull URL remoteHost) {
        ContainerClientRegistration registration = ContainerClientRegistration.builder()
                .containerId(UUID.randomUUID().toString() + zaleniumContainerName)
                .ipAddress(remoteHost.getHost())
                .noVncPort(remoteHost.getPort() + DockeredSeleniumStarter.NO_VNC_PORT_GAP)
                .build();

        linkedContainers.add(registration);

        return registration;
    }

    @Override
    @Nullable
    public InputStreamGroupIterator copyFiles(String containerId, String folderName) {
//        TODO
        return null;
    }

    @Override
    public void stopContainer(String containerId) {
        log.info("Not stopping any containers");
    }

    @Override
    public void executeCommand(String containerId, String[] command, boolean waitForExecution) {
        log.info("Not running command '{}", Arrays.toString(command));
    }

    @Override
    public String getLatestDownloadedImage(String imageName) {
        return "latest";
    }

    @Override
    public ContainerCreationStatus createContainer(String zaleniumContainerName, String image, Map<String, String> envVars, String nodePort) {
        log.info("Not creating containers");
        return new ContainerCreationStatus(false);
    }

    @Override
    public void initialiseContainerEnvironment() {
        log.info("Not doing anything to initialise container environment");
    }

    @Override
    @Nullable
    public String getContainerIp(String containerName) {
        Optional<ContainerClientRegistration> registrationOptional = linkedContainers.stream()
                .filter(reg -> reg.getContainerId().contains(containerName))
                .findFirst();
        if (registrationOptional.isPresent()) {
            return registrationOptional
                    .get()
                    .getIpAddress();
        } else {
            log.warn("No container registered as {}", containerName);
            return null;
        }
    }

    @Override
    public boolean isReady(ContainerCreationStatus container) {
//        No creation, so it should be ready by now
        return true;
    }

    @Override
    public boolean isTerminated(ContainerCreationStatus container) {
//        As above
        return false;
    }
}
