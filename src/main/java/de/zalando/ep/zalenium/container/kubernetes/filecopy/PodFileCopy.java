package de.zalando.ep.zalenium.container.kubernetes.filecopy;

import de.zalando.ep.zalenium.streams.InputStreamGroupIterator;


/**
 * Something that can copy files (typically logs or videos) from a remote pod
 */
public interface PodFileCopy {
    InputStreamGroupIterator copyFiles(String containerId, String folderName);
}
