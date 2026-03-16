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

import java.util.List;

public class SubscribeNotifyMessage extends ClusterMessage {
    private static final long serialVersionUID = 1L;

    private String clientId;        // 客户端ID
    private String nodeId;          // 节点ID
    private List<Subscribe> subscriptions; // 订阅主题列表

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public List<Subscribe> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<Subscribe> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
