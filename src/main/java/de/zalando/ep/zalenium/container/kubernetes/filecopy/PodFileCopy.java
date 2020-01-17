package de.zalando.ep.zalenium.container.kubernetes.filecopy;

import de.zalando.ep.zalenium.streams.InputStreamGroupIterator;

public interface PodFileCopy {
    InputStreamGroupIterator copyFiles(String containerId, String folderName);
}
