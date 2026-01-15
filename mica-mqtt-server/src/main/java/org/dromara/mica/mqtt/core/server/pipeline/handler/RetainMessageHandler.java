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

package org.dromara.mica.mqtt.core.server.pipeline.handler;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.pipeline.MqttPublishPipelineHandler;
import org.dromara.mica.mqtt.core.server.pipeline.PublishContext;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.utils.mica.IntPair;

/**
 * 保留消息处理器
 *
 * @author L.cm
 */
public class RetainMessageHandler implements MqttPublishPipelineHandler {
	private static final Logger logger = LoggerFactory.getLogger(RetainMessageHandler.class);
	private final IMqttMessageStore messageStore;
	private final String nodeName;

	public RetainMessageHandler(IMqttMessageStore messageStore, String nodeName) {
		this.messageStore = messageStore;
		this.nodeName = nodeName;
	}

	@Override
	public boolean handle(PublishContext context) {
		if (!context.isRetain()) {
			return true;
		}
		String topicName = context.getTopic();
		IntPair<String> retainPair = TopicUtil.retainTopicName(topicName);
		int timeOut = retainPair.getKey();
		if (timeOut < 0) {
			logger.error("MqttPublishMessage topic {} 不符合 $retain/${ttl}/topic 规则.", topicName);
			return false;
		}
		String actualTopic = retainPair.getValue();
		MqttQoS mqttQoS = context.getQos();
		byte[] payload = context.getPayload();
		// qos == 0 or payload is none,then clear previous retain message
		if (MqttQoS.QOS0 == mqttQoS || payload == null || payload.length == 0) {
			this.messageStore.clearRetainMessage(actualTopic);
		} else {
			Message retainMessage = new Message();
			retainMessage.setTopic(actualTopic);
			retainMessage.setQos(mqttQoS.value());
			retainMessage.setPayload(payload);
			retainMessage.setFromClientId(context.getClientId());
			retainMessage.setFromUsername(context.getUsername());
			retainMessage.setMessageType(MessageType.DOWN_STREAM);
			retainMessage.setRetain(true);
			retainMessage.setDup(context.isDup());
			retainMessage.setTimestamp(context.getTimestamp());
			retainMessage.setPeerHost(context.getPeerHost());
			retainMessage.setNode(nodeName);
			// 设置 MQTT5 properties
			if (context.getProperties() != null && !context.getProperties().isEmpty()) {
				retainMessage.setProperties(context.getProperties());
			}
			this.messageStore.addRetainMessage(actualTopic, timeOut, retainMessage);
		}
		return true;
	}

	@Override
	public int getOrder() {
		return 100; // 保留消息处理优先级较高
	}
}
