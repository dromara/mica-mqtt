/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.dromara.mica.mqtt.codec;

import org.tio.core.ChannelContext;

/**
 * 编解码工具
 *
 * @author netty
 * @author L.cm
 */
public final class MqttCodecUtil {
	public static final char TOPIC_WILDCARDS_ONE = '+';
	public static final char TOPIC_WILDCARDS_MORE = '#';
	private static final String MQTT_VERSION_KEY = "MQTT_V";

	/**
	 * mqtt 版本
	 *
	 * @param ctx ChannelContext
	 * @return MqttVersion
	 */
	public static MqttVersion getMqttVersion(ChannelContext ctx) {
		MqttVersion version = ctx.get(MQTT_VERSION_KEY);
		if (version == null) {
			return MqttVersion.MQTT_3_1_1;
		}
		return version;
	}

	protected static void setMqttVersion(ChannelContext ctx, MqttVersion version) {
		ctx.set(MQTT_VERSION_KEY, version);
	}

	/**
	 * 判断是否 topic filter
	 *
	 * @param topicFilter topicFilter
	 * @return 是否 topic filter
	 */
	public static boolean isTopicFilter(String topicFilter) {
		// 从尾部开始遍历，因为 + # 一般出现在 topicFilter 的尾部
		for (int i = topicFilter.length() - 1; i >= 0; i--) {
			char ch = topicFilter.charAt(i);
			if (TOPIC_WILDCARDS_ONE == ch || TOPIC_WILDCARDS_MORE == ch) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 是否校验过的 topicName
	 *
	 * @param topicName topicName
	 * @return 是否校验过的 topicName
	 */
	public static boolean isValidPublishTopicName(String topicName) {
		// publish topic name must not contain any wildcard
		return !isTopicFilter(topicName);
	}

	protected static boolean isValidClientId(MqttVersion mqttVersion, int maxClientIdLength, String clientId) {
		if (clientId == null) {
			return false;
		}
		switch (mqttVersion) {
			case MQTT_3_1:
				return clientId.length() >= MqttConstant.MIN_CLIENT_ID_LENGTH && clientId.length() <= maxClientIdLength;
			case MQTT_3_1_1:
			case MQTT_5:
				// In 3.1.3.1 Client Identifier of MQTT 3.1.1 and 5.0 specifications, The Server MAY allow ClientId’s
				// that contain more than 23 encoded bytes. And, The Server MAY allow zero-length ClientId.
				return true;
			default:
				throw new IllegalArgumentException(mqttVersion + " is unknown mqtt version");
		}
	}

	protected static MqttFixedHeader validateFixedHeader(ChannelContext ctx, MqttFixedHeader mqttFixedHeader) {
		switch (mqttFixedHeader.messageType()) {
			case PUBREL:
			case SUBSCRIBE:
			case UNSUBSCRIBE:
				if (MqttQoS.QOS1 != mqttFixedHeader.qosLevel()) {
					throw new DecoderException(mqttFixedHeader.messageType().name() + " message must have QoS 1");
				}
				return mqttFixedHeader;
			case AUTH:
				if (MqttVersion.MQTT_5 != MqttCodecUtil.getMqttVersion(ctx)) {
					throw new DecoderException("AUTH message requires at least MQTT 5");
				}
				return mqttFixedHeader;
			default:
				return mqttFixedHeader;
		}
	}

	protected static MqttFixedHeader resetUnusedFields(MqttFixedHeader mqttFixedHeader) {
		switch (mqttFixedHeader.messageType()) {
			case CONNECT:
			case CONNACK:
			case PUBACK:
			case PUBREC:
			case PUBCOMP:
			case SUBACK:
			case UNSUBACK:
			case PINGREQ:
			case PINGRESP:
			case DISCONNECT:
				if (mqttFixedHeader.isDup() ||
					MqttQoS.QOS0 != mqttFixedHeader.qosLevel() ||
					mqttFixedHeader.isRetain()) {
					return new MqttFixedHeader(
						mqttFixedHeader.messageType(),
						false,
						MqttQoS.QOS0,
						false,
						mqttFixedHeader.remainingLength());
				}
				return mqttFixedHeader;
			case PUBREL:
			case SUBSCRIBE:
			case UNSUBSCRIBE:
				if (mqttFixedHeader.isRetain()) {
					return new MqttFixedHeader(
						mqttFixedHeader.messageType(),
						mqttFixedHeader.isDup(),
						mqttFixedHeader.qosLevel(),
						false,
						mqttFixedHeader.remainingLength());
				}
				return mqttFixedHeader;
			default:
				return mqttFixedHeader;
		}
	}

	private MqttCodecUtil() {
	}
}
