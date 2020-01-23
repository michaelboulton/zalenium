package de.zalando.ep.zalenium.container;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContainerClientRegistration {
    private String containerId;

    private Integer noVncPort;

    private String ipAddress;
}
