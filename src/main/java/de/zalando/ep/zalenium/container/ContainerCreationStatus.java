package de.zalando.ep.zalenium.container;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ContainerCreationStatus {

    private boolean isCreated;

    private String containerName;

    private String containerId;

    private String nodePort;

    public ContainerCreationStatus(boolean isCreated) {
        this.isCreated = isCreated;
    }
}
