/* Copyright (c) 2022 Peigen.info. All rights reserved. */

package org.dromara.mica.mqtt.server.solon.config;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import net.dreamlu.mica.net.core.Node;
import net.dreamlu.mica.net.core.TioConfig;
import net.dreamlu.mica.net.core.ssl.ClientAuth;
import net.dreamlu.mica.net.http.mcp.server.transport.SseTransport;
import net.dreamlu.mica.net.http.mcp.server.transport.StreamableHttpTransport;
import org.dromara.mica.mqtt.codec.MqttConstant;
import org.noear.solon.annotation.Configuration;
import org.noear.solon.annotation.Inject;

/**
 * <b>(MqttServerProperties)</b>
 *
 * @author Lihai
 * @version 1.0.0
 * @since 2023/7/19
 */
@Inject(value = "${" + MqttServerProperties.PREFIX + "}", required = false)
@Configuration
@Data
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
	 * 接收数据的 buffer size，默认：8KB
	 */
	private String readBufferSize = "8KB";
	/**
	 * 消息解析最大 bytes 长度，默认：10MB
	 */
	private String maxBytesInMessage = "10MB";
	/**
	 * debug
	 */
	private boolean debug = false;
	/**
	 * mqtt 3.1 会校验此参数为 23，为了减少问题设置成了 64
	 */
	private int maxClientIdLength = MqttConstant.DEFAULT_MAX_CLIENT_ID_LENGTH;
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
	/**
	 * mqtt tcp 监听器
	 */
	private Listener mqttListener = new Listener();
	/**
	 * mqtt tcp ssl 监听器
	 */
	private SslListener mqttSslListener = new SslListener();
	/**
	 * websocket mqtt 监听器
	 */
	private Listener wsListener = new Listener();
	/**
	 * websocket ssl mqtt 监听器
	 */
	private SslListener wssListener = new SslListener();
	/**
	 * http api 监听器
	 */
	private HttpListener httpListener = new HttpListener();
	/**
	 * tio 编解码等线程数
	 */
	private Integer tioExecutorSize;
	/**
	 * AIO AsynchronousChannelGroup 的线程池
	 */
	private Integer groupExecutorSize;
	/**
	 * mqtt 工作线程数，默认：8或2倍CPU核心数（取较大值），如果消息量比较大，处理较慢，例如做 emqx 的转发消息处理，可以调大此参数
	 */
	private Integer mqttExecutorSize;
	/**
	 * MQTT 5.0 服务端能力配置，用于在 CONNACK 中告知客户端
	 */
	private Properties properties = new Properties();
	/**
	 * 线程池关闭等待超时时间（秒），默认 6000s（约 100 分钟，沿用 mica-net 默认值）。
	 * <p>
	 * 服务端 stop 时会按连接逐个触发 IMqttConnectStatusListener.onDisconnect，
	 * 这些任务由 groupExecutor（默认 8~16 线程）串行处理。该值仅控制 awaitTermination 的阻塞时长，
	 * 超时不会调用 shutdownNow() 强制中断；仍在运行的任务会自然执行直到结束。
	 * <p>
	 * 请同步将部署环境终止宽限期（如 k8s terminationGracePeriodSeconds）调到不小于此值，否则进程会被 SIGKILL 强杀。
	 */
	private int shutdownTimeoutSec = TioConfig.DEFAULT_SHUTDOWN_TIMEOUT_SEC;

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

	@Getter
	@Setter
	public static class Listener {
		/**
		 * 是否启用，默认：关闭
		 */
		private boolean enable = false;
		/**
		 * 服务端 ip
		 */
		private String ip;
		/**
		 * 端口
		 */
		private Integer port;
		/**
		 * 获取服务节点
		 *
		 * @return ServerNode
		 */
		public Node getServerNode() {
			if (this.ip == null && this.port == null) {
				return null;
			} else {
				return new Node(this.ip, this.port);
			}
		}
	}

	@Getter
	@Setter
	public static class SslListener extends Listener {
		/**
		 * ssl 配置
		 */
		private Ssl ssl = new Ssl();
	}

	@Getter
	@Setter
	public static class Ssl {
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
	public static class HttpListener extends Listener {
		/**
		 * basic 认证
		 */
		private HttpBasicAuth basicAuth = new HttpBasicAuth();
		/**
		 * mcp 配置
		 */
		private Mcp mcp = new Mcp();
		/**
		 * ssl 配置
		 */
		private HttpSsl ssl = new HttpSsl();
	}

	@Getter
	@Setter
	public static class HttpSsl extends Ssl {
		/**
		 * 是否启用，默认：关闭
		 */
		private boolean enable = false;
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
	public static class Mcp {
		/**
		 * 是否启用，默认：关闭
		 */
		private boolean enable = false;
		/**
		 * stream http endpoint
		 */
		private String endpoint = StreamableHttpTransport.DEFAULT_ENDPOINT;
		/**
		 * sse 端点
		 */
		private String sseEndpoint = SseTransport.DEFAULT_SSE_ENDPOINT;
		/**
		 * message 端点
		 */
		private String sseMessageEndpoint = SseTransport.DEFAULT_MESSAGE_ENDPOINT;
	}

	@Getter
	@Setter
	public static class Properties {
		/**
		 * Receive Maximum，服务端允许客户端同时处理的 QoS1/QoS2 未确认报文上限
		 */
		private int receiveMaximum = 65535;
		/**
		 * Maximum QoS，服务端支持的最大 QoS
		 */
		private int maximumQos = 2;
		/**
		 * Retain Available，服务端是否支持保留消息
		 */
		private boolean retainAvailable = true;
		/**
		 * Maximum Packet Size，服务端可处理的最大报文大小（字节）
		 */
		private int maximumPacketSize = 268435456;
		/**
		 * Topic Alias Maximum，服务端支持的最大主题别名数（0 表示不启用）
		 */
		private int topicAliasMaximum = 0;
		/**
		 * Wildcard Subscription Available，服务端是否支持通配符订阅
		 */
		private boolean wildcardSubscriptionAvailable = true;
		/**
		 * Shared Subscription Available，服务端是否支持共享订阅
		 */
		private boolean sharedSubscriptionAvailable = true;
		/**
		 * Subscription Identifier Available，服务端是否支持订阅标识符
		 */
		private boolean subscriptionIdentifierAvailable = true;
		/**
		 * Server Keep Alive，服务端接管心跳（秒），0 表示不接管
		 */
		private int serverKeepAlive = 0;
	}

}
