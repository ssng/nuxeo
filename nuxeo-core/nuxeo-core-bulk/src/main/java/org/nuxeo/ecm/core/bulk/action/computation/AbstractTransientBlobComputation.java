/*
 * (C) Copyright 2018 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     pierre
 */
package org.nuxeo.ecm.core.bulk.action.computation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.bulk.action.CSVExportAction;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 10.3
 */
public abstract class AbstractTransientBlobComputation extends AbstractComputation {

    private Path temp;

    @Override
    public void init(ComputationContext context) {
        super.init(context);
        try {
            temp = Files.createTempDirectory(metadata().name());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create temp directory for " + this);
        }
    }

    public AbstractTransientBlobComputation(String name) {
        super(name, 1, 1);
    }

    protected String getTransientStoreKey(String commandId) {
        return metadata.name() + commandId;
    }

    public Blob getBlob(String key) {
        TransientStore store = Framework.getService(TransientStoreService.class).getStore(CSVExportAction.ACTION_NAME);
        List<Blob> blobs = store.getBlobs(key);
        Blob blob = blobs == null || blobs.isEmpty() ? null : blobs.get(0);
        if (blob == null) {
            getLog().error("Could not retrieve blob for key " + key);
        }
        return blob;
    }

    protected void storeBlob(Blob blob, String commandId) {
        TransientStore store = Framework.getService(TransientStoreService.class).getStore(CSVExportAction.ACTION_NAME);
        store.putBlobs(getTransientStoreKey(commandId), Collections.singletonList(blob));
        store.setCompleted(getTransientStoreKey(commandId), true);
    }

    protected Path createTemp(String commandId) {
        return temp.resolve(commandId + ".csv");
    }

    protected Log getLog() {
        return LogFactory.getLog(getClass());
    }
}
