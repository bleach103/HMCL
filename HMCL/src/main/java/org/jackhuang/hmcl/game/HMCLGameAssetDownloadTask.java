/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.AbstractDependencyManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class HMCLGameAssetDownloadTask extends Task {

    private final AbstractDependencyManager dependencyManager;
    private final Version version;
    private final AssetIndexInfo assetIndexInfo;
    private final File assetIndexFile;
    private final List<Task> dependents = new LinkedList<>();
    private final List<Task> dependencies = new LinkedList<>();

    /**
     * Constructor.
     *
     * @param dependencyManager the dependency manager that can provides {@link GameRepository}
     * @param version           the <b>resolved</b> version
     */
    public HMCLGameAssetDownloadTask(HMCLDependencyManager dependencyManager, Version version) {
        this.dependencyManager = dependencyManager;
        this.version = version;
        this.assetIndexInfo = version.getAssetIndex();
        this.assetIndexFile = dependencyManager.getGameRepository().getIndexFile(version.getId(), assetIndexInfo.getId());

        if (!assetIndexFile.exists())
            dependents.add(new HMCLGameAssetIndexDownloadTask(dependencyManager, version));
    }

    @Override
    public Collection<Task> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() throws Exception {
        AssetIndex index = Constants.GSON.fromJson(FileUtils.readText(assetIndexFile), AssetIndex.class);
        int progress = 0;
        if (index != null)
            for (AssetObject assetObject : index.getObjects().values()) {
                if (Thread.interrupted())
                    throw new InterruptedException();

                File file = dependencyManager.getGameRepository().getAssetObject(version.getId(), assetIndexInfo.getId(), assetObject);
                if (file.isFile())
                    HMCLLocalRepository.REPOSITORY.tryCacheAssetObject(assetObject, file.toPath());
                else {
                    Optional<Path> path = HMCLLocalRepository.REPOSITORY.getAssetObject(assetObject);
                    if (path.isPresent()) {
                        FileUtils.copyFile(path.get().toFile(), file);
                    } else {
                        String url = dependencyManager.getDownloadProvider().getAssetBaseURL() + assetObject.getLocation();
                        FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(url), file, new FileDownloadTask.IntegrityCheck("SHA-1", assetObject.getHash()));
                        task.setName(assetObject.getHash());
                        dependencies.add(task.finalized((v, succ) -> {
                            if (succ)
                                HMCLLocalRepository.REPOSITORY.cacheAssetObject(assetObject, file.toPath());
                        }));
                    }
                }

                updateProgress(++progress, index.getObjects().size());
            }
    }
}