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
import org.dromara.mica.mqtt.core.server.http.api.MqttHttpApi;
import org.dromara.mica.mqtt.core.server.http.api.auth.BasicAuthFilter;
import org.dromara.mica.mqtt.core.server.http.handler.HttpFilter;
import org.dromara.mica.mqtt.core.server.http.handler.MqttHttpRequestHandler;
import org.dromara.mica.mqtt.core.server.http.handler.MqttHttpRoutes;
import org.dromara.mica.mqtt.core.server.http.mcp.MqttMcp;
import org.dromara.mica.mqtt.core.server.protocol.MqttProtocol;
import org.tio.core.Node;
import org.tio.core.TcpConst;
import org.tio.core.ssl.ClientAuth;
import org.tio.core.ssl.SslConfig;
import org.tio.core.uuid.SeqTioUuid;
import org.tio.http.common.HttpConfig;
import org.tio.http.mcp.server.McpServer;
import org.tio.http.server.HttpTioServerHandler;
import org.tio.http.server.HttpTioServerListener;
import org.tio.server.TioServer;
import org.tio.server.TioServerConfig;

import java.io.InputStream;
import java.util.Objects;

/**
 * mqtt http api 监听器
 *
 * @author L.cm
 */
public class MqttHttpApiListener implements IMqttProtocolListener {
	private static final MqttProtocol PROTOCOL = MqttProtocol.MQTT_HTTP_API;
	private final Node serverNode;
	private final HttpFilter authFilter;
	private final McpServer mcpServer;
	private final SslConfig sslConfig;

	MqttHttpApiListener(Node serverNode,
						HttpFilter authFilter,
						McpServer mcpServer,
						SslConfig sslConfig) {
		this.serverNode = IMqttProtocolListener.getServerNode(serverNode, PROTOCOL);
		this.authFilter = authFilter;
		this.mcpServer = mcpServer;
		this.sslConfig = sslConfig;
	}

	@Override
	public MqttProtocol getProtocol() {
		return PROTOCOL;
	}

	@Override
	public Node getServerNode() {
		return this.serverNode;
	}

	public HttpFilter getAuthFilter() {
		return authFilter;
	}

	public McpServer getMcpServer() {
		return mcpServer;
	}

	@Override
	public SslConfig getSslConfig() {
		return this.sslConfig;
	}

	@Override
	public TioServer config(MqttServerCreator serverCreator, TioServerConfig mqttServerConfig) {
		// 1 http 路由配置
		MqttHttpApi httpApi = new MqttHttpApi(serverCreator, mqttServerConfig);
		httpApi.register();
		// 2 认证配置
		if (authFilter != null) {
			MqttHttpRoutes.addFilter(authFilter);
		}
		// 3. 是否开启 mcp
		if (mcpServer != null) {
			MqttMcp mqttMcp = new MqttMcp(serverCreator, mqttServerConfig, mcpServer);
			mqttMcp.register();
		}
		// 4. http 服务配置
		TioServerConfig tioServerConfig = new TioServerConfig(
			serverCreator.getName() + '/' + this.getProtocol().name(),
			new HttpTioServerHandler(new HttpConfig(), new MqttHttpRequestHandler()),
			new HttpTioServerListener(),
			mqttServerConfig.tioExecutor, mqttServerConfig.groupExecutor
		);
		tioServerConfig.setTaskService(mqttServerConfig.getTaskService());
		// 5. 心跳超时时间，默认 30s
		tioServerConfig.setHeartbeatTimeout(1000 * 30L);
		tioServerConfig.setShortConnection(true);
		tioServerConfig.setReadBufferSize(TcpConst.MAX_DATA_LENGTH);
		tioServerConfig.setTioUuid(new SeqTioUuid());
		tioServerConfig.setSslConfig(sslConfig);
		return new TioServer(serverNode, tioServerConfig);
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		IMqttProtocolListener that = (IMqttProtocolListener) o;
		return Objects.equals(serverNode, that.getServerNode());
	}

	@Override
	public int hashCode() {
		return serverNode.hashCode();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private Node serverNode;
		private HttpFilter authFilter;
		private McpServer mcpServer;
		private SslConfig sslConfig;

		public Builder serverNode(int port) {
			return this.serverNode(new Node(null, port));
		}

		public Builder serverNode(String ip, int port) {
			return this.serverNode(new Node(ip, port));
		}

		public Builder serverNode(Node serverNode) {
			this.serverNode = serverNode;
			return this;
		}

		public Builder authFilter(HttpFilter authFilter) {
			this.authFilter = authFilter;
			return this;
		}

		public Builder basicAuth(String username, String password) {
			return this.authFilter(new BasicAuthFilter(username, password));
		}

		public Builder mcpServer(McpServer mcpServer) {
			this.mcpServer = mcpServer;
			return this;
		}

		public Builder mcpServer() {
			return mcpServer(new McpServer());
		}

		public Builder mcpServer(String sseEndpoint, String messageEndpoint) {
			return mcpServer(new McpServer(sseEndpoint, messageEndpoint));
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

		public MqttHttpApiListener build() {
			return new MqttHttpApiListener(serverNode, authFilter, mcpServer, sslConfig);
		}
	}

}
