/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & dreamlu.net).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.mica.mqtt.broker.cluster.message;

import org.dromara.mica.mqtt.core.server.model.Subscribe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 状态同步响应消息，包含节点上的所有订阅信息
 */
public class StateSyncResponseMessage extends ClusterMessage {
    private static final long serialVersionUID = 1L;

    // clientId -> nodeId
    private Map<String, String> clientNodeMap = new HashMap<>();
    // clientId -> subscriptions
    private Map<String, List<Subscribe>> subscriptionMap = new HashMap<>();

    @Override
    public ClusterMessageType getType() {
        return ClusterMessageType.STATE_SYNC_RESPONSE;
    }

    public Map<String, String> getClientNodeMap() {
        return clientNodeMap;
    }

    public void setClientNodeMap(Map<String, String> clientNodeMap) {
        this.clientNodeMap = clientNodeMap;
    }

    public Map<String, List<Subscribe>> getSubscriptionMap() {
        return subscriptionMap;
    }

    public void setSubscriptionMap(Map<String, List<Subscribe>> subscriptionMap) {
        this.subscriptionMap = subscriptionMap;
    }
}
