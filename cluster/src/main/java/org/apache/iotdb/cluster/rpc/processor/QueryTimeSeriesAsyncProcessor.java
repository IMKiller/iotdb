/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.cluster.rpc.processor;

import com.alipay.remoting.AsyncContext;
import com.alipay.remoting.BizContext;
import com.alipay.sofa.jraft.Status;
import com.alipay.sofa.jraft.closure.ReadIndexClosure;
import com.alipay.sofa.jraft.util.Bits;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.iotdb.cluster.entity.Server;
import org.apache.iotdb.cluster.entity.raft.DataPartitionRaftHolder;
import org.apache.iotdb.cluster.entity.raft.RaftService;
import org.apache.iotdb.cluster.rpc.request.QueryTimeSeriesRequest;
import org.apache.iotdb.cluster.rpc.response.QueryTimeSeriesResponse;
import org.apache.iotdb.db.exception.PathErrorException;
import org.apache.iotdb.db.metadata.MManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QueryTimeSeriesAsyncProcessor extends BasicAsyncUserProcessor<QueryTimeSeriesRequest> {

  private static final Logger LOGGER = LoggerFactory.getLogger(QueryTimeSeriesAsyncProcessor.class);

  private final AtomicInteger requestId = new AtomicInteger(0);

  private MManager mManager = MManager.getInstance();

  private Server server;

  public QueryTimeSeriesAsyncProcessor(Server server) {
    this.server = server;
  }

  @Override
  public void handleRequest(BizContext bizContext, AsyncContext asyncContext,
      QueryTimeSeriesRequest queryMetadataRequest) {
    String groupId = queryMetadataRequest.getGroupID();
    final byte[] reqContext = new byte[4];
    Bits.putInt(reqContext, 0, requestId.incrementAndGet());
    DataPartitionRaftHolder dataPartitionHolder = (DataPartitionRaftHolder) server
        .getDataPartitionHolder(groupId);
    ((RaftService) dataPartitionHolder.getService()).getNode()
        .readIndex(reqContext, new ReadIndexClosure() {

          @Override
          public void run(Status status, long index, byte[] reqCtx) {
            QueryTimeSeriesResponse response = QueryTimeSeriesResponse.createEmptyInstance(groupId);
            if (status.isOk()) {
              try {
                for (String path : queryMetadataRequest.getPath()) {
                  response.addTimeSeries(mManager.getShowTimeseriesPath(path));
                }
              } catch (final PathErrorException e) {
                response = QueryTimeSeriesResponse.createErrorInstance(groupId, e.toString());
              }
            } else {
              response = QueryTimeSeriesResponse.createErrorInstance(groupId, status.getErrorMsg());
            }
            asyncContext.sendResponse(response);
          }
        });
  }

  @Override
  public String interest() {
    return QueryTimeSeriesRequest.class.getName();
  }
}
