/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     bjalon
 */
package org.nuxeo.ecm.platform.usermanager;

import static org.nuxeo.ecm.directory.localconfiguration.DirectoryConfigurationConstants.DIRECTORY_CONFIGURATION_FACET;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.localconfiguration.LocalConfigurationService;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.query.sql.model.Expression;
import org.nuxeo.ecm.core.query.sql.model.IdentityQueryTransformer;
import org.nuxeo.ecm.core.query.sql.model.Literal;
import org.nuxeo.ecm.core.query.sql.model.LiteralList;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.query.sql.model.QueryBuilder;
import org.nuxeo.ecm.core.query.sql.model.Reference;
import org.nuxeo.ecm.core.query.sql.model.StringLiteral;
import org.nuxeo.ecm.directory.BaseSession.FieldDetector;
import org.nuxeo.ecm.directory.localconfiguration.DirectoryConfiguration;
import org.nuxeo.runtime.api.Framework;

/**
 * @author bjalon
 */
public class DefaultUserMultiTenantManagement implements UserMultiTenantManagement {

    protected static final Log log = LogFactory.getLog(DefaultUserMultiTenantManagement.class);

    protected static final String SUFFIX_SEPARATOR = "-";

    protected String getDirectorySuffix(DocumentModel documentContext) {
        LocalConfigurationService localConfigurationService = Framework.getService(LocalConfigurationService.class);
        DirectoryConfiguration configuration = localConfigurationService.getConfiguration(DirectoryConfiguration.class,
                DIRECTORY_CONFIGURATION_FACET, documentContext);
        if (configuration != null && configuration.getDirectorySuffix() != null) {
            return SUFFIX_SEPARATOR + configuration.getDirectorySuffix();
        }
        return null;
    }

    @Override
    public void queryTransformer(UserManager um, Map<String, Serializable> filter, Set<String> fulltext,
            DocumentModel context) {
        String groupId = um.getGroupIdField();
        if (filter == null || fulltext == null) {
            throw new NuxeoException("Filter and Fulltext must be not null");
        }

        if (getDirectorySuffix(context) == null) {
            log.debug("Directory Local Configuration is null, don't need to filter");
            return;
        }

        String groupIdSuffix = getDirectorySuffix(context);

        if (!filter.containsKey(groupId)) {
            log.debug("no filter on group id, need to filter with the directory local " + "configuration suffix : "
                    + groupId + " = %" + groupIdSuffix);
            filter.put(groupId, "%" + groupIdSuffix);
            fulltext.add(groupId);
            return;
        }

        if (!(filter.get(groupId) instanceof String)) {
            throw new UnsupportedOperationException("Filter value on " + "group id is not a string : "
                    + filter.get(groupId));
        }

        String filterIdValue = (String) filter.get(um.getGroupIdField());
        filter.put(groupId, filterIdValue + groupIdSuffix);
    }

    @Override
    public QueryBuilder groupQueryTransformer(UserManager um, QueryBuilder queryBuilder, DocumentModel context) {
        String groupIdSuffix = getDirectorySuffix(context);
        if (groupIdSuffix == null) {
            log.debug("No tenant configuration");
            return queryBuilder;
        }
        queryBuilder = new QueryBuilder(queryBuilder); // copy
        MultiExpression multiexpr = (MultiExpression) queryBuilder.predicate();
        String groupIdField = um.getGroupIdField();
        if (FieldDetector.hasField(multiexpr, groupIdField)) {
            log.debug("Adding tenant to group matches");
            QueryTenantAdder qta = new QueryTenantAdder(groupIdField, groupIdSuffix);
            multiexpr = qta.transform(multiexpr);
            queryBuilder.predicates((List<Predicate>) (Object) multiexpr.values);
        }
        log.debug("Adding tenant filter to predicate");
        queryBuilder.addAndPredicate(Predicates.like(groupIdField, "%" + groupIdSuffix));
        return queryBuilder;
    }

    /**
     * Changes group equality or difference matches to take into account a suffix.
     * <p>
     * Throws for any more complex query on groups.
     *
     * @since 10.3
     */
    protected static class QueryTenantAdder extends IdentityQueryTransformer {

        protected final String groupIdField;

        protected final String groupIdSuffix;

        public QueryTenantAdder(String groupIdField, String groupIdSuffix) {
            this.groupIdField = groupIdField;
            this.groupIdSuffix = groupIdSuffix;
        }

        @Override
        public Expression transform(Expression node) {
            /*
             * If we have an expression of the form group = 'foo', we turn it into group = 'foo-tenanta'.
             * Same for group IN ('foo', 'bar') which becomes group IN ('foo-tenanta', 'bar-tenanta').
             */
            if (node.lvalue instanceof Reference && ((Reference) node.lvalue).name.equals(groupIdField)) {
                if (node.operator == Operator.EQ || node.operator == Operator.NOTEQ) {
                    if (node.rvalue instanceof StringLiteral) {
                        StringLiteral lit = addSuffix((StringLiteral) node.rvalue);
                        Expression expr = new Expression(node.lvalue, node.operator, lit);
                        expr.info = node.info;
                        return expr;
                    }
                } else if (node.operator == Operator.IN || node.operator == Operator.NOTIN) {
                    if (node.rvalue instanceof LiteralList) {
                        LiteralList list = new LiteralList();
                        for (Literal lit : (LiteralList) node.rvalue) {
                            if (lit instanceof StringLiteral) {
                                lit = addSuffix((StringLiteral) lit);
                            }
                            list.add(transform(lit));
                        }
                        Expression expr = new Expression(node.lvalue, node.operator, list);
                        expr.info = node.info;
                        return expr;
                    }
                }
            }
            return super.transform(node);
        }

        protected StringLiteral addSuffix(StringLiteral lit) {
            return new StringLiteral(lit.value + groupIdSuffix);
        }

        @Override
        public Reference transform(Reference node) {
            if (node.name.equals(groupIdField)) {
                throw new QueryParseException("Cannot evaluate " + node + " in multi-tenant mode");
            }
            return node;
        }
    }

    @Override
    public DocumentModel groupTransformer(UserManager um, DocumentModel group, DocumentModel context)
            {
        if (context == null) {
            return group;
        }
        String groupIdValue = group.getPropertyValue(um.getGroupIdField()) + getDirectorySuffix(context);
        group.setPropertyValue(um.getGroupIdField(), groupIdValue);
        return group;
    }

    @Override
    public String groupnameTranformer(UserManager um, String groupname, DocumentModel context) {
        String suffix = getDirectorySuffix(context);
        if (suffix != null) {
            groupname += suffix;
        }
        return groupname;
    }
}
