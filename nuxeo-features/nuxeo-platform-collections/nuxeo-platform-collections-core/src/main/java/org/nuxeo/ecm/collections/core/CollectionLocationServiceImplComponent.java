/*
 * (C) Copyright 2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume Renard <grenard@nuxeo.com>
 */
package org.nuxeo.ecm.collections.core;

import java.util.Deque;
import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.collections.api.CollectionLocationService;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * This component is used to register the service that provide the collection location service support.
 *
 * @since 10.3
 */
public class CollectionLocationServiceImplComponent extends DefaultComponent {

    public static final String NAME = "org.nuxeo.ecm.collections.CollectionLocationService";

    private static final Log log = LogFactory.getLog(CollectionLocationService.class);

    protected Deque<CollectionLocationDescriptor> descriptors = new LinkedList<>();

    protected CollectionLocationService collectionLocationService;

    @Override
    public void activate(ComponentContext context) {
        log.info("CollectionLocationService activated");
    }

    @Override
    public void deactivate(ComponentContext context) {
        log.info("CollectionLocationService deactivated");
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter == CollectionLocationService.class) {
            return adapter.cast(getCollectionLocationService());
        }
        return null;
    }

    private CollectionLocationService getCollectionLocationService() {
        if (collectionLocationService == null) {
            Class<?> klass = getConfiguration().getCollectionLocationServiceClass();
            if (klass == null) {
                throw new NuxeoException("No class specified for the collectionLocationService");
            }
            try {
                collectionLocationService = (CollectionLocationService) klass.newInstance();
            } catch (ReflectiveOperationException e) {
                throw new NuxeoException("Failed to instantiate class " + klass, e);
            }
        }
        return collectionLocationService;
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        descriptors.add((CollectionLocationDescriptor) contribution);
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        descriptors.remove(contribution);
    }

    protected void recompute() {
        collectionLocationService = null;
    }

    public CollectionLocationDescriptor getConfiguration() {
        return descriptors.getLast();
    }

    // for tests only
    public static void reset() {
        CollectionLocationServiceImplComponent s = (CollectionLocationServiceImplComponent) Framework.getRuntime()
                                                                                           .getComponent(NAME);
        s.collectionLocationService = null;
    }
}
