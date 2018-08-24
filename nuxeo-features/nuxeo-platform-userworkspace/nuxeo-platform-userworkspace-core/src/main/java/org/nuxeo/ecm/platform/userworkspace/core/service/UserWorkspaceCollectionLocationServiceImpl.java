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
package org.nuxeo.ecm.platform.userworkspace.core.service;

import java.util.Locale;
import java.util.MissingResourceException;

import org.nuxeo.common.utils.i18n.I18NUtils;
import org.nuxeo.ecm.collections.api.CollectionConstants;
import org.nuxeo.ecm.collections.api.CollectionLocationService;
import org.nuxeo.ecm.collections.api.FavoritesConstants;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.platform.userworkspace.api.UserWorkspaceService;
import org.nuxeo.ecm.platform.web.common.locale.LocaleProvider;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 10.3
 */
public class UserWorkspaceCollectionLocationServiceImpl implements CollectionLocationService {

    @Override
    public DocumentModel getUserDefaultCollectionsRoot(CoreSession session) {
        DocumentModel defaultCollectionsRoot = createDefaultCollectionsRoot(session,
                getCurrentUserPersonalWorkspace(session));
        return session.getOrCreateDocument(defaultCollectionsRoot, doc -> initDefaultCollectionsRoot(session, doc));
    }

    @Override
    public DocumentModel getUserFavorites(CoreSession session) {
        DocumentModel location = getCurrentUserPersonalWorkspace(session);
        if (location == null) {
            // no location => no favorites (transient user for instance)
            return null;
        }
        DocumentModel favorites = createFavorites(session, location);
        return session.getOrCreateDocument(favorites, doc -> initCreateFavorites(session, doc));
    }

    protected DocumentModel getCurrentUserPersonalWorkspace(CoreSession session) {
        UserWorkspaceService uws = Framework.getService(UserWorkspaceService.class);
        return uws.getCurrentUserPersonalWorkspace(session);
    }

    protected Locale getLocale(final CoreSession session) {
        Locale locale = null;
        locale = Framework.getService(LocaleProvider.class).getLocale(session);
        if (locale == null) {
            locale = Locale.getDefault();
        }
        return new Locale(Locale.getDefault().getLanguage());
    }

    protected DocumentModel createFavorites(CoreSession session, DocumentModel userWorkspace) {
        DocumentModel doc = session.createDocumentModel(userWorkspace.getPath().toString(),
                FavoritesConstants.DEFAULT_FAVORITES_NAME, FavoritesConstants.FAVORITES_TYPE);
        String title = null;
        try {
            title = I18NUtils.getMessageString("messages", FavoritesConstants.DEFAULT_FAVORITES_TITLE, new Object[0],
                    getLocale(session));
        } catch (MissingResourceException e) {
            title = FavoritesConstants.DEFAULT_FAVORITES_NAME;
        }
        doc.setPropertyValue("dc:title", title);
        doc.setPropertyValue("dc:description", "");
        return doc;
    }

    protected DocumentModel initCreateFavorites(CoreSession session, DocumentModel favorites) {
        ACP acp = new ACPImpl();
        ACE denyEverything = new ACE(SecurityConstants.EVERYONE, SecurityConstants.EVERYTHING, false);
        ACE allowEverything = new ACE(session.getPrincipal().getName(), SecurityConstants.EVERYTHING, true);
        ACL acl = new ACLImpl();
        acl.setACEs(new ACE[] { allowEverything, denyEverything });
        acp.addACL(acl);
        favorites.setACP(acp, true);
        return favorites;
    }

    protected DocumentModel createDefaultCollectionsRoot(final CoreSession session, DocumentModel userWorkspace) {
        DocumentModel doc = session.createDocumentModel(userWorkspace.getPath().toString(),
                CollectionConstants.DEFAULT_COLLECTIONS_NAME, CollectionConstants.COLLECTIONS_TYPE);
        String title;
        try {
            title = I18NUtils.getMessageString("messages", CollectionConstants.DEFAULT_COLLECTIONS_TITLE, new Object[0],
                    getLocale(session));
        } catch (MissingResourceException e) {
            title = CollectionConstants.DEFAULT_COLLECTIONS_TITLE;
        }
        doc.setPropertyValue("dc:title", title);
        doc.setPropertyValue("dc:description", "");
        return doc;
    }

    protected DocumentModel initDefaultCollectionsRoot(final CoreSession session, DocumentModel collectionsRoot) {
        ACP acp = new ACPImpl();
        ACE denyEverything = new ACE(SecurityConstants.EVERYONE, SecurityConstants.EVERYTHING, false);
        ACE allowEverything = new ACE(session.getPrincipal().getName(), SecurityConstants.EVERYTHING, true);
        ACL acl = new ACLImpl();
        acl.setACEs(new ACE[] { allowEverything, denyEverything });
        acp.addACL(acl);
        collectionsRoot.setACP(acp, true);
        return collectionsRoot;
    }

}
