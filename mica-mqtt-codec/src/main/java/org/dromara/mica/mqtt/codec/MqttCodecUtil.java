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

import org.dromara.mica.mqtt.codec.exception.DecoderException;
import org.dromara.mica.mqtt.codec.exception.MqttUnacceptableProtocolVersionException;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;

/**
 * 编解码工具
 *
 * @author netty
 * @author L.cm
 */
public final class MqttCodecUtil {
	private static final Logger logger = LoggerFactory.getLogger(MqttCodecUtil.class);
	public static final char TOPIC_LAYER = '/';
	public static final char TOPIC_WILDCARDS_ONE = '+';
	public static final char TOPIC_WILDCARDS_MORE = '#';
	private static final String MQTT_VERSION_KEY = "MQTT_V";

	private MqttCodecUtil() {
	}

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

	static void setMqttVersion(ChannelContext ctx, MqttVersion version) {
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
			// topic 中有空白符打印提示
			if (Character.isWhitespace(ch)) {
				logger.warn("注意：topic:[{}] 中包含空白字符串:[{}]，请检查是否正确", topicFilter, ch);
			} else if (TOPIC_WILDCARDS_ONE == ch || TOPIC_WILDCARDS_MORE == ch) {
				return true;
			}
		}
		return false;
	}

	static boolean isValidClientId(MqttVersion mqttVersion, int maxClientIdLength, String clientId) {
		if (clientId == null) {
			return false;
		}
		switch (mqttVersion) {
			case MQTT_3_1:
				return !clientId.isEmpty() && clientId.length() <= maxClientIdLength;
			case MQTT_3_1_1:
			case MQTT_5:
				// In 3.1.3.1 Client Identifier of MQTT 3.1.1 and 5.0 specifications, The Server MAY allow ClientId’s
				// that contain more than 23 encoded bytes. And, The Server MAY allow zero-length ClientId.
				return true;
			default:
				throw new MqttUnacceptableProtocolVersionException(mqttVersion + " is unknown mqtt version");
		}
	}

	static MqttFixedHeader validateFixedHeader(ChannelContext ctx, MqttFixedHeader mqttFixedHeader) {
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
}
