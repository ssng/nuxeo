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
 *     Funsho David
 */
package org.nuxeo.ecm.core.bulk.computation;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.bulk.BulkCodecs;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.BulkServiceImpl;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.lib.stream.codec.Codec;
import org.nuxeo.lib.stream.computation.AbstractComputation;
import org.nuxeo.lib.stream.computation.ComputationContext;
import org.nuxeo.lib.stream.computation.Record;
import org.nuxeo.runtime.api.Framework;

/**
 * Saves the status into a key value store.
 * <p>
 * Inputs:
 * <ul>
 * <li>i1: Reads {@link BulkStatus} sharded by command id</li>
 * </ul>
 * Outputs:
 * <ul>
 * <li>o1: Write {@link BulkStatus} full into the done stream.</li>
 * </ul>
 * </p>
 *
 * @since 10.2
 */
public class BulkStatusComputation extends AbstractComputation {
    private static final Log log = LogFactory.getLog(BulkStatusComputation.class);

    public BulkStatusComputation(String name) {
        super(name, 1, 1);
    }

    @Override
    public void processRecord(ComputationContext context, String inputStreamName, Record record) {
        Codec<BulkStatus> codec = BulkCodecs.getStatusCodec();
        BulkStatus recordStatus = codec.decode(record.getData());
        BulkServiceImpl bulkService = (BulkServiceImpl) Framework.getService(BulkService.class);
        BulkStatus status;
        if (!recordStatus.isDelta()) {
            status = recordStatus;
        } else {
            status = bulkService.getStatus(recordStatus.getCommandId());
            if (BulkStatus.State.UNKNOWN.equals(status.getState())) {
                // this requires a manual intervention, the kv store might have been lost
                log.error(String.format("Stopping processing, unknown status for command: %s, offset: %s, record: %s.",
                        recordStatus.getCommandId(), context.getLastOffset(), record));
                context.askForTermination();
                return;
            }
            status.merge(recordStatus);
        }
        byte[] statusAsBytes = bulkService.setStatus(status);
        if (BulkStatus.State.COMPLETED.equals(status.getState())) {
            context.produceRecord(OUTPUT_1, status.getCommandId(), statusAsBytes);
        }
        context.askForCheckpoint();
    }
}
