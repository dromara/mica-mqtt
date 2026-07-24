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

import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.codec.exception.MqttUnacceptableProtocolVersionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
	private static final String MQTT_MAX_PACKET_SIZE_KEY = "MQTT_MPS";
	/**
	 * MQTT 5.0 Topic Alias：客户端→服务端的别名表（Map 形式）。
	 * <p>
	 * 客户端发 PUBLISH 时携带 {@code Topic Alias} 属性可省去重复的 topic 字符串。
	 * 本端作为服务端时，需要把 {@code alias → topic} 映射存进此表，以便后续收到的
	 * 仅有 alias 的 PUBLISH 能反查 topic。
	 */
	private static final String MQTT_CLIENT_TOPIC_ALIAS_MAP_KEY = "MQTT_CTA";
	/**
	 * MQTT 5.0 Topic Alias：服务端→客户端的别名表（Map 形式）。
	 * <p>
	 * 本端作为服务端下发 PUBLISH 时使用。
	 */
	private static final String MQTT_SERVER_TOPIC_ALIAS_MAP_KEY = "MQTT_STA";
	/**
	 * MQTT 5.0 Topic Alias Maximum：服务端在 CONNACK 中下发的最大 Topic Alias 取值。
	 * <p>
	 * 缓存到 ctx 是为了本端作为客户端时，在 PUBLISH 中校验 Topic Alias 是否越界。
	 * 0 表示服务端未下发（即不允许使用 Topic Alias），与 0xFFFF 的"MQTT spec 上限"区分。
	 */
	private static final String MQTT_TOPIC_ALIAS_MAXIMUM_KEY = "MQTT_TAM";
	/**
	 * 当前连接本端允许接收的 Topic Alias Maximum。
	 * 与 {@link #MQTT_TOPIC_ALIAS_MAXIMUM_KEY} 的对端接收上限方向相反。
	 */
	private static final String MQTT_INBOUND_TOPIC_ALIAS_MAXIMUM_KEY = "MQTT_ITAM";
	/**
	 * 实际的 Topic Alias 取值上限，spec 3.3.2.3.4 / 5.4.4：1 ~ 0xFFFF。
	 */
	public static final int MAX_TOPIC_ALIAS = 0xFFFF;
	/**
	 * 默认无限制（0 表示不限制），使用 {@code int} 的最大值兜底
	 */
	public static final int NO_MAX_PACKET_SIZE = 0;

	private MqttCodecUtil() {
	}

	/**
	 * 判断是否 mqtt5.0 协议
	 *
	 * @param ctx ChannelContext
	 * @return 是否 mqtt 5.0 协议
	 */
	public static boolean isMqtt5(ChannelContext ctx) {
		return MqttVersion.MQTT_5 == getMqttVersion(ctx);
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

	/**
	 * 设置 mqtt 版本
	 *
	 * @param ctx     ChannelContext
	 * @param version MqttVersion
	 */
	static void setMqttVersion(ChannelContext ctx, MqttVersion version) {
		ctx.set(MQTT_VERSION_KEY, version);
	}

	/**
	 * 获取 Maximum Packet Size（MQTT 5.0）。
	 * 客户端在 CONNECT 中通过 {@code Maximum Packet Size} 属性声明能接收的最大报文长度。
	 * 0 表示未设置（不限制）。
	 *
	 * @param ctx ChannelContext
	 * @return Maximum Packet Size
	 */
	public static int getMaxPacketSize(ChannelContext ctx) {
		Integer value = ctx.get(MQTT_MAX_PACKET_SIZE_KEY);
		return value == null ? NO_MAX_PACKET_SIZE : value;
	}

	/**
	 * 设置 Maximum Packet Size（MQTT 5.0）。
	 * 0 表示清除限制。
	 *
	 * @param ctx            ChannelContext
	 * @param maxPacketSize  Maximum Packet Size
	 */
	static void setMaxPacketSize(ChannelContext ctx, int maxPacketSize) {
		if (maxPacketSize == 0) {
			ctx.remove(MQTT_MAX_PACKET_SIZE_KEY);
		} else {
			ctx.set(MQTT_MAX_PACKET_SIZE_KEY, maxPacketSize);
		}
	}

	/**
	 * 获取当前会话的"客户端→服务端"Topic Alias 别名表（懒加载）。
	 * <p>
	 * MQTT 5.0 Topic Alias 是方向性的：这是本端作为<b>服务端</b>时，客户端发到本端的别名表。
	 * 表的 key 是 2 字节 unsigned 整数（{@link Integer}），value 是该别名对应的完整 topic。
	 * <p>
	 * 调用方可以安全地写入，不需要考虑初始化。
	 *
	 * @param ctx ChannelContext
	 * @return alias → topic 的并发 Map
	 */
	@SuppressWarnings("unchecked")
	public static Map<Integer, String> getClientTopicAliasMap(ChannelContext ctx) {
		Map<Integer, String> map = ctx.get(MQTT_CLIENT_TOPIC_ALIAS_MAP_KEY);
		if (map == null) {
			synchronized (ctx) {
				map = ctx.get(MQTT_CLIENT_TOPIC_ALIAS_MAP_KEY);
				if (map == null) {
					map = new ConcurrentHashMap<>();
					ctx.set(MQTT_CLIENT_TOPIC_ALIAS_MAP_KEY, map);
				}
			}
		}
		return map;
	}

	/**
	 * 获取当前会话的"服务端→客户端"Topic Alias 别名表（懒加载）。
	 * <p>
	 * 本端作为<b>服务端</b>下发 PUBLISH 时，业务方可以将常用 topic 维护到这张表里，
	 * 并通过 {@link org.dromara.mica.mqtt.codec.message.builder.MqttPublishBuilder#properties}
	 * 把 {@code Topic Alias} 属性附加到消息体上。
	 *
	 * @param ctx ChannelContext
	 * @return alias → topic 的并发 Map
	 */
	@SuppressWarnings("unchecked")
	public static Map<Integer, String> getServerTopicAliasMap(ChannelContext ctx) {
		Map<Integer, String> map = ctx.get(MQTT_SERVER_TOPIC_ALIAS_MAP_KEY);
		if (map == null) {
			synchronized (ctx) {
				map = ctx.get(MQTT_SERVER_TOPIC_ALIAS_MAP_KEY);
				if (map == null) {
					map = new ConcurrentHashMap<>();
					ctx.set(MQTT_SERVER_TOPIC_ALIAS_MAP_KEY, map);
				}
			}
		}
		return map;
	}

	/**
	 * 清理当前会话关联的 Topic Alias 别名表（连接断开时调用）。
	 *
	 * @param ctx ChannelContext
	 */
	public static void clearTopicAliasMaps(ChannelContext ctx) {
		ctx.remove(MQTT_CLIENT_TOPIC_ALIAS_MAP_KEY);
		ctx.remove(MQTT_SERVER_TOPIC_ALIAS_MAP_KEY);
	}

	/**
	 * 获取本端作为客户端时，服务端在 CONNACK 中下发的 Topic Alias Maximum。
	 * 0 表示未下发（不允许使用 Topic Alias）。
	 *
	 * @param ctx ChannelContext
	 * @return Topic Alias Maximum
	 */
	public static int getTopicAliasMaximum(ChannelContext ctx) {
		Integer value = ctx.get(MQTT_TOPIC_ALIAS_MAXIMUM_KEY);
		return value == null ? 0 : value;
	}

	/**
	 * 设置 Topic Alias Maximum。
	 * &lt;= 0 等同于未下发，调用方无需关心初始化。
	 *
	 * @param ctx               ChannelContext
	 * @param topicAliasMaximum Topic Alias Maximum
	 */
	static void setTopicAliasMaximum(ChannelContext ctx, int topicAliasMaximum) {
		if (topicAliasMaximum <= 0) {
			ctx.remove(MQTT_TOPIC_ALIAS_MAXIMUM_KEY);
		} else {
			ctx.set(MQTT_TOPIC_ALIAS_MAXIMUM_KEY, topicAliasMaximum);
		}
	}

	/**
	 * 获取本端在当前连接上允许接收的 Topic Alias Maximum。
	 * 服务端使用自身 CONNACK 宣告值，客户端使用自身 CONNECT 宣告值。
	 *
	 * @param ctx ChannelContext
	 * @return 入站 Topic Alias Maximum；0 表示不允许接收 Topic Alias
	 */
	public static int getInboundTopicAliasMaximum(ChannelContext ctx) {
		Integer value = ctx.get(MQTT_INBOUND_TOPIC_ALIAS_MAXIMUM_KEY);
		return value == null ? 0 : value;
	}

	/**
	 * 设置本端在当前连接上允许接收的 Topic Alias Maximum。
	 *
	 * @param ctx               ChannelContext
	 * @param topicAliasMaximum 入站 Topic Alias Maximum；0 表示禁用
	 */
	public static void setInboundTopicAliasMaximum(ChannelContext ctx, int topicAliasMaximum) {
		if (topicAliasMaximum <= 0) {
			ctx.remove(MQTT_INBOUND_TOPIC_ALIAS_MAXIMUM_KEY);
		} else {
			ctx.set(MQTT_INBOUND_TOPIC_ALIAS_MAXIMUM_KEY, topicAliasMaximum);
		}
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

	/**
	 * 验证 clientId
	 *
	 * @param mqttVersion       mqtt 版本
	 * @param maxClientIdLength 最大长度
	 * @param clientId          clientId
	 * @return 是否有效
	 */
	static boolean isInvalidClientId(MqttVersion mqttVersion, int maxClientIdLength, String clientId) {
		if (clientId == null) {
			return true;
		}
		switch (mqttVersion) {
			case MQTT_3_1:
				return clientId.isEmpty() || clientId.length() > maxClientIdLength;
			case MQTT_3_1_1:
			case MQTT_5:
				// In 3.1.3.1 Client Identifier of MQTT 3.1.1 and 5.0 specifications, The Server MAY allow ClientId’s
				// that contain more than 23 encoded bytes. And, The Server MAY allow zero-length ClientId.
				return false;
			default:
				throw new MqttUnacceptableProtocolVersionException(mqttVersion + " is unknown mqtt version");
		}
	}

}
