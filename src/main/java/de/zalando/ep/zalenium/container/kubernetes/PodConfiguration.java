package de.zalando.ep.zalenium.container.kubernetes;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.PodSecurityContext;
import io.fabric8.kubernetes.api.model.HostAlias;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.SecurityContext;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.*;

import java.util.List;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
@Data
public class PodConfiguration {

    private KubernetesClient client;
    private String containerIdPrefix;
    private String image;
    private String imagePullPolicy;
    private String nodePort;
    private List<LocalObjectReference> imagePullSecrets;
    private List<EnvVar> envVars;
    private List<HostAlias> hostAliases;
    private Map<String, String> labels;
    private Map<VolumeMount, Volume> mountedSharedFoldersMap;
    private Map<String, Quantity> podLimits;
    private Map<String, Quantity> podRequests;
    private Map<String, String> nodeSelector;
    private List<Toleration> tolerations;
    private OwnerReference ownerReference;
    private PodSecurityContext podSecurityContext;
    private SecurityContext containerSecurityContext;
    private String schedulerName;
    private String serviceAccount;

    public void setOwner(Pod ownerPod) {
        this.ownerReference = new OwnerReference(ownerPod.getApiVersion(), false, true, ownerPod.getKind(), ownerPod.getMetadata().getName(), ownerPod.getMetadata().getUid());
    }

    public OwnerReference getOwnerRef(){return ownerReference;}
}
