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

package org.dromara.mica.mqtt.spring.server.config;

import lombok.Getter;
import lombok.Setter;
import org.dromara.mica.mqtt.codec.MqttConstant;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.tio.core.ssl.ClientAuth;
import org.tio.utils.buffer.ByteBufferAllocator;

/**
 * MqttServer 配置
 *
 * @author L.cm
 */
@Getter
@Setter
@ConfigurationProperties(MqttServerProperties.PREFIX)
public class MqttServerProperties {

	/**
	 * 配置前缀
	 */
	public static final String PREFIX = "mqtt.server";
	/**
	 * 是否启用，默认：启用
	 */
	private boolean enabled = true;
	/**
	 * 名称
	 */
	private String name = "Mica-Mqtt-Server";
	/**
	 * 服务端 ip
	 */
	private String ip;
	/**
	 * 端口
	 */
	private int port = 1883;
	/**
	 * mqtt 认证
	 */
	private MqttAuth auth = new MqttAuth();
	/**
	 * 心跳超时时间(单位: 毫秒 默认: 1000 * 120)，如果用户不希望框架层面做心跳相关工作，请把此值设为0或负数
	 */
	private Long heartbeatTimeout;
	/**
	 * MQTT 客户端 keepalive 系数，连接超时缺省为连接设置的 keepalive * keepaliveBackoff * 2，默认：0.75
	 * <p>
	 * 如果读者想对该值做一些调整，可以在此进行配置。比如设置为 0.75，则变为 keepalive * 1.5。但是该值不得小于 0.5，否则将小于 keepalive 设定的时间。
	 */
	private float keepaliveBackoff = 0.75F;
	/**
	 * 接收数据的 buffer size，默认：8k
	 */
	private DataSize readBufferSize = DataSize.ofBytes(MqttConstant.DEFAULT_MAX_READ_BUFFER_SIZE);
	/**
	 * 消息解析最大 bytes 长度，默认：10M
	 */
	private DataSize maxBytesInMessage = DataSize.ofBytes(MqttConstant.DEFAULT_MAX_BYTES_IN_MESSAGE);
	/**
	 * 堆内存和堆外内存
	 */
	private ByteBufferAllocator bufferAllocator = ByteBufferAllocator.HEAP;
	/**
	 * ssl 配置
	 */
	private Ssl ssl = new Ssl();
	/**
	 * debug
	 */
	private boolean debug = false;
	/**
	 * mqtt 3.1 会校验此参数为 23，为了减少问题设置成了 64
	 */
	private int maxClientIdLength = MqttConstant.DEFAULT_MAX_CLIENT_ID_LENGTH;
	/**
	 * http 端口，默认：18083
	 */
	private int httpPort = 18083;
	/**
	 * 开启 websocket 服务，默认：true
	 */
	private boolean websocketEnable = true;
	/**
	 * websocket 端口，默认：8083
	 */
	private int websocketPort = 8083;
	/**
	 * 开启 http 服务，默认：false
	 */
	private boolean httpEnable = false;
	/**
	 * http basic auth
	 */
	private HttpBasicAuth httpBasicAuth = new HttpBasicAuth();
	/**
	 * 节点名称，用于处理集群
	 */
	private String nodeName;
	/**
	 * 是否开启监控，不开启可节省内存，默认：true
	 */
	private boolean statEnable = true;
	/**
	 * 开启代理协议，支持 nginx proxy_protocol    on;
	 */
	private boolean proxyProtocolOn = false;

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
		/**
		 * 认证类型
		 */
		private ClientAuth clientAuth = ClientAuth.NONE;
	}

	@Getter
	@Setter
	public static class HttpBasicAuth {
		/**
		 * 是否启用，默认：关闭
		 */
		private boolean enable = false;
		/**
		 * http Basic 认证账号
		 */
		private String username;
		/**
		 * http Basic 认证密码
		 */
		private String password;
	}

	@Getter
	@Setter
	public static class MqttAuth {
		/**
		 * 是否启用，默认：关闭
		 */
		private boolean enable = false;
		/**
		 * http Basic 认证账号
		 */
		private String username;
		/**
		 * http Basic 认证密码
		 */
		private String password;
	}

}
