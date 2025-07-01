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

package org.dromara.mica.mqtt.core.server.listener;

import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.dromara.mica.mqtt.core.server.http.websocket.MqttWsMsgHandler;
import org.dromara.mica.mqtt.core.server.protocol.MqttProtocol;
import org.tio.core.Node;
import org.tio.core.ssl.ClientAuth;
import org.tio.core.ssl.SslConfig;
import org.tio.core.uuid.SnowflakeTioUuid;
import org.tio.http.common.HttpConfig;
import org.tio.server.TioServer;
import org.tio.server.TioServerConfig;
import org.tio.websocket.server.WsTioServerHandler;
import org.tio.websocket.server.WsTioServerListener;

import java.io.InputStream;
import java.util.Objects;

/**
 * mqtt 协议监听器
 *
 * @author L.cm
 */
public class MqttProtocolListener implements IMqttProtocolListener {
	private final MqttProtocol protocol;
	private final Node serverNode;
	private final SslConfig sslConfig;

	public MqttProtocolListener(MqttProtocol protocol, Node serverNode) {
		this(protocol, serverNode, null);
	}

	public MqttProtocolListener(MqttProtocol protocol, Node serverNode, SslConfig sslConfig) {
		this.protocol = checkSSl(protocol, sslConfig);
		this.serverNode = IMqttProtocolListener.getServerNode(serverNode, protocol);
		this.sslConfig = sslConfig;
	}

	private static MqttProtocol checkSSl(MqttProtocol mqttProtocol, SslConfig sslConfig) {
		if ((MqttProtocol.MQTT_SSL == mqttProtocol || MqttProtocol.MQTT_WSS == mqttProtocol) && sslConfig == null) {
			throw new NullPointerException(mqttProtocol + " 缺少必要参数 SslConfig");
		} else {
			return Objects.requireNonNull(mqttProtocol, "MqttProtocol is null");
		}
	}

	@Override
	public MqttProtocol getProtocol() {
		return this.protocol;
	}

	@Override
	public Node getServerNode() {
		return this.serverNode;
	}

	@Override
	public SslConfig getSslConfig() {
		return this.sslConfig;
	}

	@Override
	public TioServer config(MqttServerCreator serverCreator, TioServerConfig mainServerConfig) {
		// 1. 服务配置
		TioServerConfig serverConfig;
		if (this.protocol == MqttProtocol.MQTT || this.protocol == MqttProtocol.MQTT_SSL) {
			serverConfig = getTcpServerConfig(serverCreator, mainServerConfig);
		} else {
			serverConfig = getWebSocketServerConfig(serverCreator, mainServerConfig);
		}
		serverConfig.setUseQueueDecode(mainServerConfig.useQueueDecode);
		serverConfig.setUseQueueSend(mainServerConfig.useQueueSend);
		serverConfig.setTaskService(mainServerConfig.getTaskService());
		// 2. 消息默认的心跳
		serverConfig.setHeartbeatTimeout(0);
		int readBufferSize = mainServerConfig.getReadBufferSize();
		if (readBufferSize > 0) {
			serverConfig.setReadBufferSize(readBufferSize);
		}
		// 3. 是否开启监控和 debug
		serverConfig.statOn = mainServerConfig.statOn;
		serverConfig.debug = mainServerConfig.debug;
		// 4. 如果开启了 ssl
		serverConfig.setSslConfig(sslConfig);
		// 5. 共享配置
		serverConfig.share(mainServerConfig);
		return new TioServer(serverNode, serverConfig);
	}

	/**
	 * 获取 tcp 服务配置
	 *
	 * @param serverCreator    serverCreator
	 * @param mainServerConfig mainServerConfig
	 * @return TioServerConfig
	 */
	private TioServerConfig getTcpServerConfig(MqttServerCreator serverCreator, TioServerConfig mainServerConfig) {
		return new TioServerConfig(serverCreator.getName() + '/' + this.getProtocol().name(),
			mainServerConfig.getTioServerHandler(), mainServerConfig.getTioServerListener());
	}

	/**
	 * 获取 websocket 配置
	 *
	 * @param serverCreator    MqttServerCreator
	 * @param mainServerConfig TioServerConfig
	 * @return TioServerConfig
	 */
	private TioServerConfig getWebSocketServerConfig(MqttServerCreator serverCreator, TioServerConfig mainServerConfig) {
		MqttWsMsgHandler handler = new MqttWsMsgHandler(serverCreator, mainServerConfig.getTioHandler());
		TioServerConfig tioServerConfig = new TioServerConfig(
			serverCreator.getName() + '/' + this.getProtocol().name(),
			new WsTioServerHandler(new HttpConfig(), handler),
			new WsTioServerListener(),
			mainServerConfig.tioExecutor,
			mainServerConfig.groupExecutor
		);
		tioServerConfig.setTioUuid(new SnowflakeTioUuid());
		return tioServerConfig;
	}

	/**
	 * 配置 websocket 服务
	 *
	 * @param serverCreator    MqttServerCreator
	 * @param mqttServerConfig ServerTioConfig
	 * @return TioServer
	 */
	private TioServer configWs(MqttServerCreator serverCreator, TioServerConfig mqttServerConfig) {
		// 1. 配置 websocket
		MqttWsMsgHandler wsMsgHandler = new MqttWsMsgHandler(serverCreator, mqttServerConfig.getTioHandler());
		TioServerConfig tioServerConfig = new TioServerConfig(
			this.getProtocol().name(),
			new WsTioServerHandler(new HttpConfig(), wsMsgHandler),
			new WsTioServerListener(),
			mqttServerConfig.tioExecutor,
			mqttServerConfig.groupExecutor
		);
		tioServerConfig.setTaskService(mqttServerConfig.getTaskService());
		tioServerConfig.setHeartbeatTimeout(0);
		tioServerConfig.setTioUuid(new SnowflakeTioUuid());
		tioServerConfig.setReadBufferSize(mqttServerConfig.getReadBufferSize());
		tioServerConfig.debug = mqttServerConfig.debug;
		tioServerConfig.setSslConfig(sslConfig);
		// 共享数据
		tioServerConfig.share(mqttServerConfig);
		return new TioServer(serverNode, tioServerConfig);
	}

	public static Builder mqttBuilder() {
		return new Builder(MqttProtocol.MQTT);
	}

	public static SslBuilder mqttSslBuilder() {
		return new SslBuilder(MqttProtocol.MQTT_SSL);
	}

	public static Builder wsBuilder() {
		return new Builder(MqttProtocol.MQTT_WS);
	}

	public static SslBuilder wssBuilder() {
		return new SslBuilder(MqttProtocol.MQTT_WSS);
	}

	public static class Builder {
		protected final MqttProtocol protocol;
		protected Node serverNode;

		Builder(MqttProtocol protocol) {
			this.protocol = protocol;
		}

		public Builder serverNode(int port) {
			this.serverNode = new Node(null, port);
			return this;
		}

		public Builder serverNode(String ip, int port) {
			this.serverNode = new Node(ip, port);
			return this;
		}

		public MqttProtocolListener build() {
			return new MqttProtocolListener(protocol, serverNode, null);
		}
	}

	public static class SslBuilder extends Builder {
		private SslConfig sslConfig;

		SslBuilder(MqttProtocol protocol) {
			super(protocol);
		}

		public Builder sslConfig(SslConfig sslConfig) {
			this.sslConfig = sslConfig;
			return this;
		}

		public Builder useSsl(InputStream keyStoreInputStream, String keyPasswd) {
			return sslConfig(SslConfig.forServer(keyStoreInputStream, keyPasswd));
		}

		public Builder useSsl(InputStream keyStoreInputStream, String keyPasswd, ClientAuth clientAuth) {
			return sslConfig(SslConfig.forServer(keyStoreInputStream, keyPasswd, clientAuth));
		}

		public Builder useSsl(InputStream keyStoreInputStream, String keyPasswd, InputStream trustStoreInputStream, String trustPassword, ClientAuth clientAuth) {
			return sslConfig(SslConfig.forServer(keyStoreInputStream, keyPasswd, trustStoreInputStream, trustPassword, clientAuth));
		}

		public Builder useSsl(String keyStoreFile, String keyPasswd) {
			return sslConfig(SslConfig.forServer(keyStoreFile, keyPasswd));
		}

		public Builder useSsl(String keyStoreFile, String keyPasswd, ClientAuth clientAuth) {
			return sslConfig(SslConfig.forServer(keyStoreFile, keyPasswd, clientAuth));
		}

		public Builder useSsl(String keyStoreFile, String keyPasswd, String trustStoreFile, String trustPassword, ClientAuth clientAuth) {
			return sslConfig(SslConfig.forServer(keyStoreFile, keyPasswd, trustStoreFile, trustPassword, clientAuth));
		}

		@Override
		public MqttProtocolListener build() {
			return new MqttProtocolListener(protocol, serverNode, sslConfig);
		}
	}
}
