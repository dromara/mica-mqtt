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

package org.dromara.mica.mqtt.spring.client.config;

import lombok.Getter;
import lombok.Setter;
import org.dromara.mica.mqtt.codec.MqttConstant;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.MqttTopicSubscription;
import org.dromara.mica.mqtt.codec.MqttVersion;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.tio.utils.buffer.ByteBufferAllocator;

import java.util.List;

/**
 * MqttClient 配置
 *
 * @author wsq（冷月宫主）
 */
@Getter
@Setter
@ConfigurationProperties(MqttClientProperties.PREFIX)
public class MqttClientProperties {

	/**
	 * 配置前缀
	 */
	public static final String PREFIX = "mqtt.client";
	/**
	 * 是否启用，默认：true
	 */
	private boolean enabled = true;
	/**
	 * 名称，默认：Mica-Mqtt-Client
	 */
	private String name = "Mica-Mqtt-Client";
	/**
	 * 服务端 ip，默认：127.0.0.1
	 */
	private String ip = "127.0.0.1";
	/**
	 * 端口，默认：1883
	 */
	private int port = 1883;
	/**
	 * 用户名
	 */
	private String userName;
	/**
	 * 密码
	 */
	private String password;
	/**
	 * 客户端ID
	 */
	private String clientId;
	/**
	 * 超时时间，单位：秒，t-io 配置，可为 null
	 */
	private Integer timeout;
	/**
	 * 接收数据的 buffer size，默认：8k
	 */
	private DataSize readBufferSize = DataSize.ofBytes(MqttConstant.DEFAULT_MAX_READ_BUFFER_SIZE);
	/**
	 * 消息解析最大 bytes 长度，默认：10M
	 */
	private DataSize maxBytesInMessage = DataSize.ofBytes(MqttConstant.DEFAULT_MAX_BYTES_IN_MESSAGE);
	/**
	 * mqtt 3.1 会校验此参数为 23，为了减少问题设置成了 64
	 */
	private int maxClientIdLength = MqttConstant.DEFAULT_MAX_CLIENT_ID_LENGTH;
	/**
	 * Keep Alive (s)
	 */
	private int keepAliveSecs = 60;
	/**
	 * 自动重连
	 */
	private boolean reconnect = true;
	/**
	 * 重连的间隔时间，单位毫秒，默认：5000
	 */
	private long reInterval = 5000;
	/**
	 * 连续重连次数，当连续重连这么多次都失败时，不再重连。0和负数则一直重连
	 */
	private int retryCount = 0;
	/**
	 * 重连，重新订阅一个批次大小，默认：20
	 */
	private int reSubscribeBatchSize = 20;
	/**
	 * mqtt 协议，默认：MQTT_3_1_1
	 */
	private MqttVersion version = MqttVersion.MQTT_3_1_1;
	/**
	 * 清除会话
	 * <p>
	 * false 表示如果订阅的客户机断线了，那么要保存其要推送的消息，如果其重新连接时，则将这些消息推送。
	 * true 表示消除，表示客户机是第一次连接，消息所以以前的连接信息。
	 * </p>
	 */
	private boolean cleanSession = true;
	/**
	 * 开启保留 session 时，session 的有效期，默认：0
	 */
	private int sessionExpiryIntervalSecs = 0;
	/**
	 * ByteBuffer Allocator，支持堆内存和堆外内存，默认为：堆内存
	 */
	private ByteBufferAllocator bufferAllocator = ByteBufferAllocator.HEAP;
	/**
	 * 遗嘱消息
	 */
	private WillMessage willMessage;
	/**
	 * 全局订阅
	 */
	private List<MqttTopicSubscription> globalSubscribe;
	/**
	 * 是否开启监控，默认：false 不开启，节省内存
	 */
	private boolean statEnable = false;
	/**
	 * debug
	 */
	private boolean debug = false;
	/**
	 * mqtt 工作线程数，默认：2，如果消息量比较大，处理较慢，例如做 emqx 的转发消息处理，可以调大此参数
	 */
	private Integer mqttThreadPoolSize;
	/**
	 * ssl 配置
	 */
	private Ssl ssl = new Ssl();

	@Getter
	@Setter
	public static class WillMessage {
		/**
		 * 遗嘱消息 topic
		 */
		private String topic;
		/**
		 * 遗嘱消息 qos，默认： qos0
		 */
		private MqttQoS qos = MqttQoS.QOS0;
		/**
		 * 遗嘱消息 payload
		 */
		private String message;
		/**
		 * 遗嘱消息保留标识符，默认: false
		 */
		private boolean retain = false;
	}

	@Getter
	@Setter
	public static class Ssl {
		/**
		 * 启用 ssl
		 */
		private boolean enabled = false;
		/**
		 * keystore 证书路径
		 */
		private String keystorePath;
		/**
		 * keystore 密码
		 */
		private String keystorePass;
		/**
		 * truststore 证书路径
		 */
		private String truststorePath;
		/**
		 * truststore 密码
		 */
		private String truststorePass;
	}

}
