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

import org.dromara.mica.mqtt.core.server.protocol.MqttProtocol;
import org.tio.core.Node;
import org.tio.core.ssl.ClientAuth;
import org.tio.core.ssl.SslConfig;

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
