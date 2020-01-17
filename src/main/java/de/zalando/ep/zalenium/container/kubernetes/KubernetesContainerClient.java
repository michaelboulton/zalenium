package de.zalando.ep.zalenium.container.kubernetes;

import de.zalando.ep.zalenium.container.ContainerClient;
import de.zalando.ep.zalenium.container.ContainerClientRegistration;
import de.zalando.ep.zalenium.container.ContainerCreationStatus;
import de.zalando.ep.zalenium.container.kubernetes.filecopy.CommandCopier;
import de.zalando.ep.zalenium.container.kubernetes.filecopy.CopyStrategy;
import de.zalando.ep.zalenium.container.kubernetes.filecopy.PodFileCopy;
import de.zalando.ep.zalenium.container.kubernetes.filecopy.SharedVolumeCopier;
import de.zalando.ep.zalenium.streams.InputStreamGroupIterator;
import de.zalando.ep.zalenium.util.Environment;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import okhttp3.Response;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static de.zalando.ep.zalenium.container.kubernetes.filecopy.CopyStrategy.getCopier;

public class KubernetesContainerClient implements ContainerClient {

    private static final String[] PROTECTED_NODE_MOUNT_POINTS = {
            "/home/seluser/videos",
            "/dev/shm"
    };

    private static final Logger logger = LoggerFactory.getLogger(KubernetesContainerClient.class.getName());

    private static final String DEFAULT_ZALENIUM_CONTAINER_NAME = "zalenium";
    private static final String ZALENIUM_KUBERNETES_TOLERATIONS = "ZALENIUM_KUBERNETES_TOLERATIONS";
    private static final String ZALENIUM_KUBERNETES_NODE_SELECTOR = "ZALENIUM_KUBERNETES_NODE_SELECTOR";

    private KubernetesClient client;

    private String zaleniumAppName;

    private Pod zaleniumPod;

    private PodFileCopy copier;

    private Map<String, String> createdByZaleniumMap;
    private Map<String, String> appLabelMap;

    private Map<VolumeMount, Volume> mountedSharedFoldersMap = new HashMap<>();
    private VolumeMount nodeSharedArtifactsMount;
    private List<HostAlias> hostAliases = new ArrayList<>();
    private Map<String, String> nodeSelector = new HashMap<>();
    private List<Toleration> tolerations = new ArrayList<>();
    private String imagePullPolicy;
    private String schedulerName;
    private String serviceAccount;
    private List<LocalObjectReference> imagePullSecrets;
    private PodSecurityContext configuredPodSecurityContext;
    private SecurityContext configuredContainerSecurityContext;

    private final Map<String, Quantity> seleniumPodLimits = new HashMap<>();
    private final Map<String, Quantity> seleniumPodRequests = new HashMap<>();

    private final Environment environment;

    private final Function<PodConfiguration, DoneablePod> createDoneablePod;

    public KubernetesContainerClient(Environment environment,
                                     Function<PodConfiguration, DoneablePod> createDoneablePod,
                                     KubernetesClient client) {
        logger.info("Initialising Kubernetes support");
        String appName;

        copier = getCopier(environment, client);

        this.environment = environment;
        this.createDoneablePod = createDoneablePod;
        String hostname;
        try {
            this.client = client;

            // Lookup our current hostname, this lets us lookup ourselves via the kubernetes api
            hostname = findHostname();

            zaleniumPod = client.pods().withName(hostname).get();

            appName = zaleniumPod.getMetadata().getLabels().get("app");

            appLabelMap = new HashMap<>();
            appLabelMap.put("app", appName);

            createdByZaleniumMap = new HashMap<>();
            createdByZaleniumMap.put("createdBy", appName);
            zaleniumAppName = appName;

            discoverFolderMounts();
            discoverHostAliases();
            discoverNodeSelector();
            discoverTolerations();
            discoverImagePullSecrets();
            discoverPodSecurityContext();
            discoverSchedulerName();
            discoverContainerSecurityContext();
            discoverServiceAcount();
            buildResourceMaps();

        } catch (Exception e) {
            logger.error("Error initialising Kubernetes support", e);
            throw e;
        }

        logger.info(String.format(
                "Kubernetes support initialised.\n"
                        + "\tPod name: %s\n"
                        + "\tapp label: %s\n"
                        + "\tzalenium service name: %s\n"
                        + "\tSelenium Pod Resource Limits: %s\n"
                        + "\tSelenium Pod Resource Requests: %s",
                hostname, appName, zaleniumAppName,
                seleniumPodLimits.toString(), seleniumPodRequests.toString()));
    }

    private void buildResourceMaps() {
        for (Resources resource : Resources.values()) {
            String envValue = environment.getStringEnvVariable(resource.getEnvVar(), null);
            if (StringUtils.isNotBlank(envValue)) {
                Map<String, Quantity> resourceMap = null;
                switch (resource.getResourceType()) {
                    case REQUEST:
                        resourceMap = seleniumPodRequests;
                        break;
                    case LIMIT:
                        resourceMap = seleniumPodLimits;
                        break;
                    default:
                        break;
                }
                if (resourceMap != null) {
                    Quantity quantity = new Quantity(envValue);
                    resourceMap.put(resource.getRequestType(), quantity);
                }
            }
        }
        // Default to imagePullPolicy: Always if ENV variable "ZALENIUM_KUBERNETES_IMAGE_PULL_POLICY" is not provided
        imagePullPolicy = environment.getStringEnvVariable("ZALENIUM_KUBERNETES_IMAGE_PULL_POLICY", ImagePullPolicyType.Always.name());
    }

    private void discoverHostAliases() {
        List<HostAlias> configuredHostAliases = zaleniumPod.getSpec().getHostAliases();
        if (!configuredHostAliases.isEmpty()) {
            hostAliases = configuredHostAliases;
        }
    }

    private void discoverNodeSelector() {
        final Map<String, String> nodeSelectorFromEnv = environment.getMapEnvVariable(ZALENIUM_KUBERNETES_NODE_SELECTOR, new HashMap<>());
        if (nodeSelectorFromEnv != null && !nodeSelectorFromEnv.isEmpty()) {
            nodeSelector = nodeSelectorFromEnv;
        } else {
            final Map<String, String> configuredNodeSelector = zaleniumPod.getSpec().getNodeSelector();
            if (configuredNodeSelector != null && !configuredNodeSelector.isEmpty()) {
                nodeSelector = configuredNodeSelector;
            }
        }
    }

    private void discoverTolerations() {
        final List<Toleration> tolerationsFromEnv = environment.getYamlListEnvVariable(ZALENIUM_KUBERNETES_TOLERATIONS, Toleration.class, new ArrayList<Toleration>());
        if (tolerationsFromEnv != null && !tolerationsFromEnv.isEmpty()) {
            tolerations = tolerationsFromEnv;
        } else {
            final List<Toleration> configuredTolerations = zaleniumPod.getSpec().getTolerations();
            if (configuredTolerations != null && !configuredTolerations.isEmpty()) {
                tolerations = configuredTolerations;
            }
        }
    }

    private void discoverSchedulerName() {
        schedulerName = zaleniumPod.getSpec().getSchedulerName();
    }

    private void discoverImagePullSecrets() {
        List<LocalObjectReference> configuredPullSecrets = zaleniumPod.getSpec().getImagePullSecrets();
        if (!configuredPullSecrets.isEmpty()) {
            imagePullSecrets = configuredPullSecrets;
        }
    }

    private void discoverFolderMounts() {
        List<VolumeMount> volumeMounts = zaleniumPod.getSpec().getContainers().get(0).getVolumeMounts();

        List<VolumeMount> validMounts = new ArrayList<>();
        volumeMounts.stream()
                .filter(volumeMount -> !Arrays.asList(PROTECTED_NODE_MOUNT_POINTS).contains(volumeMount.getMountPath()))
                .forEach(validMounts::add);

        // Look through the volume mounts to see if the shared folder is mounted
        List<Volume> volumes = zaleniumPod.getSpec().getVolumes();

        logger.info("Could mount {} volumes", validMounts.size());

        for (VolumeMount validMount : validMounts) {
            volumes.stream()
                    .filter(volume -> validMount.getName().equalsIgnoreCase(volume.getName()))
                    .findFirst()
                    .ifPresent(volume -> {
                        if (nodeSharedArtifactsMount == null) {
                            nodeSharedArtifactsMount = validMount;
                        }
                        mountedSharedFoldersMap.put(validMount, volume);
                        logger.info("Mounting {}", validMount);
                    });
        }
    }

    private String findHostname() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = null;
        }

        return hostname;
    }

    private void discoverPodSecurityContext() {
        configuredPodSecurityContext = zaleniumPod.getSpec().getSecurityContext();
    }

    private void discoverContainerSecurityContext() {
//        This used to use findFirst().elsE(null) but it was actually raising an NPE for some rason
        List<SecurityContext> securityContexts = zaleniumPod.getSpec().getContainers()
                .stream()
                .filter(c -> DEFAULT_ZALENIUM_CONTAINER_NAME.equals(c.getName()))
                .map(Container::getSecurityContext)
                .collect(Collectors.toList());

        if (securityContexts.isEmpty()) {
            configuredContainerSecurityContext = null;
        } else {
            configuredContainerSecurityContext = securityContexts.get(0);
        }
    }

    private void discoverServiceAcount() {
        serviceAccount = environment.getStringEnvVariable(
                "ZALENIUM_SELENIUM_CONTAINER_SERVICE_ACCOUNT",
                zaleniumPod.getSpec().getServiceAccount()
        );
    }

    @Override
    public void setNodeId(String nodeId) {
        // We don't care about the nodeId, as it's essentially the same as the containerId, which is passed in where necessary.
    }

    /**
     * Copy some files by executing a tar command to the stdout and return the InputStream that contains the tar
     * contents.
     * <p>
     * Unfortunately due to the fact that any error handling happens on another thread, if the tar command fails the
     * InputStream will simply be empty and it will close. It won't propagate an Exception to the reader of the
     * InputStream.
     *
     * @return
     */
    @Override
    public InputStreamGroupIterator copyFiles(String containerId, String folderName) {
        return copier.copyFiles(containerId, folderName);
    }

    @Override
    public void stopContainer(String containerId) {
        client.pods().withName(containerId).delete();
    }

    @Override
    public void executeCommand(String containerId, String[] command, boolean waitForExecution) {
        final CountDownLatch latch = new CountDownLatch(1);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        logger.debug("{} {}", containerId, Arrays.toString(command));
        ExecWatch exec = client.pods().withName(containerId).writingOutput(baos).writingError(baos).usingListener(new ExecListener() {

            @Override
            public void onOpen(Response response) {
            }

            @Override
            public void onFailure(Throwable t,
                                  Response response) {
                logger.error(String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)), t);
                latch.countDown();
            }

            @Override
            public void onClose(int code,
                                String reason) {
                latch.countDown();
            }
        }).exec(command);

        Supplier<Void> waitForResultsAndCleanup = () -> {

            try {
                latch.await();
            } catch (InterruptedException e) {
                logger.error(String.format("%s Failed to execute command %s", containerId, Arrays.toString(command)), e);
            } finally {
                exec.close();
            }

            logger.debug(String.format("%s completed %s", containerId, Arrays.toString(command)));
            logger.debug(String.format("%s %s", containerId, baos.toString()));

            return null;
        };
        if (waitForExecution) {
            // If we're going to wait, let's use the same thread
            waitForResultsAndCleanup.get();
        } else {
            // Let the common ForkJoinPool handle waiting for the results, since we don't care when it finishes.
            CompletableFuture.supplyAsync(waitForResultsAndCleanup);
        }
    }

    @Override
    public String getLatestDownloadedImage(String imageName) {
        // Nothing to do here, this is managed by the ImagePullPolicy when creating a container.
        // Currently the kubernetes API can't manage images, the OpenShift API has some extra hooks though, which we could potential use.
        return imageName;
    }

    @Override
    public ContainerCreationStatus createContainer(String zaleniumContainerName, String image, Map<String, String> envVars,
                                                   String nodePort) {
        String containerIdPrefix = String.format("%s-%s-", zaleniumAppName, nodePort);

        // Convert the environment variables into the Kubernetes format.
        List<EnvVar> flattenedEnvVars = envVars.entrySet().stream()
                .map(e -> new EnvVar(e.getKey(), e.getValue(), null))
                .collect(Collectors.toList());

        if (nodeSharedArtifactsMount != null) {
            String workDir = nodeSharedArtifactsMount.getMountPath() + "/" + UUID.randomUUID().toString();
            flattenedEnvVars.add(new EnvVar("SHARED_DIR", workDir, null));
            String videoDir = workDir;
            flattenedEnvVars.add(new EnvVar("VIDEOS_DIR", videoDir, null));
            String logDir = workDir;
            flattenedEnvVars.add(new EnvVar("LOGS_DIR", logDir, null));
            if (!Files.exists(Paths.get(workDir))) {
                Objects.requireNonNull(createDirectories(workDir));
                Objects.requireNonNull(createDirectories(videoDir));
                Objects.requireNonNull(createDirectories(logDir));
            }
        }

        Map<String, String> podSelector = new HashMap<>();

        PodConfiguration config = new PodConfiguration();
        config.setNodePort(nodePort);
        config.setClient(client);
        config.setContainerIdPrefix(containerIdPrefix);
        config.setImage(image);
        config.setEnvVars(flattenedEnvVars);
        Map<String, String> labels = new HashMap<>();
        labels.putAll(createdByZaleniumMap);
        labels.putAll(appLabelMap);
        labels.putAll(podSelector);
        config.setLabels(labels);
        config.setImagePullPolicy(imagePullPolicy);
        config.setImagePullSecrets(imagePullSecrets);
        config.setMountedSharedFoldersMap(mountedSharedFoldersMap);
        config.setHostAliases(hostAliases);
        config.setNodeSelector(nodeSelector);
        config.setTolerations(tolerations);
        config.setPodLimits(seleniumPodLimits);
        config.setPodRequests(seleniumPodRequests);
        config.setOwner(zaleniumPod);
        config.setSchedulerName(schedulerName);
        config.setServiceAccount(serviceAccount);
        config.setPodSecurityContext(configuredPodSecurityContext);
        config.setContainerSecurityContext(configuredContainerSecurityContext);

        DoneablePod doneablePod = createDoneablePod.apply(config);

        // Create the container
        Pod createdPod = doneablePod.done();
        String containerName = createdPod.getMetadata() == null ? containerIdPrefix : createdPod.getMetadata().getName();
        return new ContainerCreationStatus(true, containerName, containerName, nodePort);
    }

    @Nullable
    private static Path createDirectories(String dirName) {
        try {
            return Files.createDirectories(Paths.get(dirName));
        } catch (IOException e) {
            logger.error("Error creating folder {}: {}", dirName, e);
            return null;
        }
    }

    @Override
    public void initialiseContainerEnvironment() {
        // Delete any leftover pods from a previous time
        deleteSeleniumPods();

        // Register a shutdown hook to cleanup pods
        Runtime.getRuntime().addShutdownHook(new Thread(this::deleteSeleniumPods, "KubernetesContainerClient shutdown hook"));
    }

    @Override
    public String getContainerIp(String containerName) {
        Pod pod = client.pods().withName(containerName).get();
        if (pod != null) {
            String podIP = pod.getStatus().getPodIP();
            logger.debug(String.format("Pod %s, IP -> %s", containerName, podIP));
            return podIP;
        } else {
            return null;
        }
    }

    public boolean isReady(ContainerCreationStatus container) {
        Pod pod = client.pods().withName(container.getContainerName()).get();
        if (pod == null) {
            return false;
        } else {
            return pod.getStatus().getConditions().stream()
                    .filter(condition -> condition.getType().equals("Ready"))
                    .map(condition -> condition.getStatus().equals("True"))
                    .findFirst()
                    .orElse(false);
        }
    }

    public boolean isTerminated(ContainerCreationStatus container) {
        Pod pod = client.pods().withName(container.getContainerName()).get();
        if (pod == null) {
            logger.info("Container {} has no pod - terminal.", container);
            return true;
        } else {
            List<ContainerStatus> containerStatuses = pod.getStatus().getContainerStatuses();
            Optional<ContainerStateTerminated> terminated = containerStatuses.stream()
                    .flatMap(status -> Optional.ofNullable(status.getState()).map(Stream::of).orElse(Stream.empty()))
                    .flatMap(state -> Optional.ofNullable(state.getTerminated()).map(Stream::of).orElse(Stream.empty()))
                    .findFirst();

            terminated.ifPresent(state -> logger.info("Container {} is {} - terminal.", container, state));

            return terminated.isPresent();
        }
    }

    private void deleteSeleniumPods() {
        logger.info("About to clean up any left over docker-selenium pods created by Zalenium");
        client.pods().withLabels(createdByZaleniumMap).delete();
    }

    @Override
    public ContainerClientRegistration registerNode(String zaleniumContainerName, URL remoteHost) {
        String podIpAddress = remoteHost.getHost();

        // The only way to lookup a pod name by IP address is by looking at all pods in the namespace it seems.
        PodList list = client.pods().withLabels(createdByZaleniumMap).list();

        String containerId = null;
        Pod currentPod = null;
        for (Pod pod : list.getItems()) {

            if (podIpAddress.equals(pod.getStatus().getPodIP())) {
                containerId = pod.getMetadata().getName();
                currentPod = pod;
                break;
            }
        }

        if (containerId == null) {
            throw new IllegalStateException("Unable to locate pod by ip address, registration will fail");
        }
        ContainerClientRegistration registration = new ContainerClientRegistration();

        List<EnvVar> podEnvironmentVariables = currentPod.getSpec().getContainers().get(0).getEnv();
        Optional<EnvVar> noVncPort = podEnvironmentVariables.stream().filter(env -> "NOVNC_PORT".equals(env.getName())).findFirst();

        if (noVncPort.isPresent()) {
            Integer noVncPortInt = Integer.decode(noVncPort.get().getValue());

            registration.setNoVncPort(noVncPortInt);
        } else {
            logger.warn("{} Couldn't find NOVNC_PORT, live preview will not work.", containerId);
        }

        registration.setIpAddress(currentPod.getStatus().getPodIP());
        registration.setContainerId(containerId);

        return registration;
    }

    private enum Resources {

        CPU_REQUEST(ResourceType.REQUEST, "cpu", "ZALENIUM_KUBERNETES_CPU_REQUEST"),
        CPU_LIMIT(ResourceType.LIMIT, "cpu", "ZALENIUM_KUBERNETES_CPU_LIMIT"),
        MEMORY_REQUEST(ResourceType.REQUEST, "memory", "ZALENIUM_KUBERNETES_MEMORY_REQUEST"),
        MEMORY_LIMIT(ResourceType.LIMIT, "memory", "ZALENIUM_KUBERNETES_MEMORY_LIMIT");

        private ResourceType resourceType;
        private String requestType;
        private String envVar;

        Resources(ResourceType resourceType, String requestType, String envVar) {
            this.resourceType = resourceType;
            this.requestType = requestType;
            this.envVar = envVar;
        }

        public String getRequestType() {
            return requestType;
        }

        public ResourceType getResourceType() {
            return resourceType;
        }

        public String getEnvVar() {
            return envVar;
        }
    }

    private enum ResourceType {
        REQUEST, LIMIT
    }

    private enum ImagePullPolicyType {
        Always, IfNotPresent
    }

    public static DoneablePod createDoneablePodDefaultImpl(PodConfiguration config) {

        logger.info("Creating pod: {}", (config));

//        Create init containers that will create folder if they don't exist
        List<Container> initContainers = Stream.of("SHARED_DIR", "VIDEOS_DIR", "LOGS_DIR")
                .map(dirName -> {
                    Container mkdirContainer = new Container();
                    mkdirContainer.setCommand(Arrays.asList("mkdir", "-v", "-p", String.format("$(%s)", dirName)));
                    mkdirContainer.setImage(config.getImage());
                    mkdirContainer.setName("mkdir-" + dirName.replace("_", "-2").toLowerCase());
                    mkdirContainer.setEnv(config.getEnvVars());

                    mkdirContainer.setVolumeMounts(new ArrayList<>(config.getMountedSharedFoldersMap().keySet()));

                    return mkdirContainer;
                })
                .collect(Collectors.toList());

        PodFluent.SpecNested<DoneablePod> doneablePodSpecNested = config.getClient().pods()
                .createNew()

                .withNewMetadata()
                .withGenerateName(config.getContainerIdPrefix())
                .addToLabels(config.getLabels())
                .withOwnerReferences(config.getOwnerRef())
                .endMetadata()

                .withNewSpec()
                .withNodeSelector(config.getNodeSelector())
                .withTolerations(config.getTolerations())
                .withSecurityContext(config.getPodSecurityContext())

                // Add a memory volume that we can use for /dev/shm
                .addNewVolume()
                .withName("dshm")
                .withNewEmptyDir()
                .withMedium("Memory")
                .endEmptyDir()
                .endVolume()

                .addNewContainer()
                .withName("selenium-node")
                .withImage(config.getImage())
                .withImagePullPolicy(config.getImagePullPolicy())
                .addAllToEnv(config.getEnvVars())
                .withSecurityContext(config.getContainerSecurityContext())
                .addNewVolumeMount()
                .withName("dshm")
                .withMountPath("/dev/shm")
                .endVolumeMount()
                .withNewResources()
                .addToLimits(config.getPodLimits())
                .addToRequests(config.getPodRequests())
                .endResources()
                // Add a readiness health check so that we can know when the selenium pod is ready to accept requests
                // so then we can initiate a registration.
                .withNewReadinessProbe()
                .withNewExec()
                .addToCommand(new String[]{"/bin/sh", "-c", "http_proxy=\"\" curl -s http://`hostname -i`:"
                        + config.getNodePort() + "/wd/hub/status | jq .value.ready | grep true"})
                .endExec()
                .withInitialDelaySeconds(5)
                .withFailureThreshold(60)
                .withPeriodSeconds(1)
                .withTimeoutSeconds(5)
                .withSuccessThreshold(1)
                .endReadinessProbe()
                .endContainer()

                .withInitContainers(initContainers)

                .withServiceAccount(config.getServiceAccount())
                .withRestartPolicy("Always")
                .withImagePullSecrets(config.getImagePullSecrets());

        if (config.getSchedulerName() != null) {
            doneablePodSpecNested = doneablePodSpecNested.withSchedulerName(config.getSchedulerName());
        }

        DoneablePod doneablePod = doneablePodSpecNested.endSpec();

        // Add the shared folders if available
        for (Map.Entry<VolumeMount, Volume> entry : config.getMountedSharedFoldersMap().entrySet()) {
            logger.info("Mounting {}", entry);
            doneablePod = doneablePod
                    .editSpec()
                    .addNewVolumeLike(entry.getValue())
                    .and()
                    .editFirstContainer()
                    .addNewVolumeMountLike(entry.getKey())
                    .endVolumeMount()
                    .endContainer()
                    .endSpec();
        }

        // Add configured host aliases, if any
        for (HostAlias hostAlias : config.getHostAliases()) {
            doneablePod = doneablePod
                    .editSpec()
                    .addNewHostAliasLike(hostAlias)
                    .endHostAlias()
                    .endSpec();
        }

        return doneablePod;
    }
}
