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

import org.dromara.mica.mqtt.core.server.model.Message;

/**
 * 集群消息转发，用于跨节点转发 PUBLISH 消息
 * 直接使用 Message 模型，可复用现有的 MQTT 消息编解码
 */
public class PublishForwardMessage extends ClusterMessage {
    private static final long serialVersionUID = 1L;

    private Message message;

    @Override
    public ClusterMessageType getType() {
        return ClusterMessageType.PUBLISH_FORWARD;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }
}
