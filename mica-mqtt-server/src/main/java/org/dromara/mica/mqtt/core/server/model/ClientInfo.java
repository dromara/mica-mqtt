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

package org.dromara.mica.mqtt.core.server.model;

import org.dromara.mica.mqtt.codec.MqttCodecUtil;
import org.dromara.mica.mqtt.codec.MqttVersion;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.tio.core.ChannelContext;
import org.tio.core.Node;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 客户端信息
 *
 * @author L.cm
 */
public class ClientInfo implements Serializable {

	/**
	 * 客户端 Id
	 */
	private String clientId;
	/**
	 * 用户名
	 */
	private String username;
	/**
	 * keep alive
	 */
	private long keepAlive;
	/**
	 * 连接成功
	 */
	private boolean connected;
	/**
	 * 协议名称
	 */
	private String protoName;
	/**
	 * 协议版本
	 */
	private int protoVer;
	/**
	 * 协议全名
	 */
	private String protoFullName;
	/**
	 * 是否 WebSocket
	 */
	private boolean webSocket;
	/**
	 * 是否开启 SSL
	 */
	private boolean ssl;
	/**
	 * ip
	 */
	private String ipAddress;
	/**
	 * 端口
	 */
	private int port;
	/**
	 * 连接成功时间
	 */
	private Long connectedAt;
	/**
	 * 创建时间
	 */
	private long createdAt;
	/**
	 * 解码队列长度
	 */
	private int decodeQueueSize;
	/**
	 * 业务处理队列长度
	 */
	private int handlerQueueSize;
	/**
	 * 发送队列长度
	 */
	private int sendQueueSize;

	/**
	 * 构造 ClientInfo
	 *
	 * @param serverCreator MqttServerCreator
	 * @param context       ChannelContext
	 * @return ClientInfo
	 */
	public static ClientInfo form(MqttServerCreator serverCreator, ChannelContext context) {
		return form(serverCreator, context, ClientInfo::new);
	}

	/**
	 * 构造 ClientInfo
	 *
	 * @param serverCreator MqttServerCreator
	 * @param context       ChannelContext
	 * @param extFunc       Supplier
	 * @return ClientInfo
	 */
	public static ClientInfo form(MqttServerCreator serverCreator, ChannelContext context, Supplier<ClientInfo> extFunc) {
		ClientInfo clientInfo = extFunc.get();
		// 客户端信息
		clientInfo.setClientId(context.getBsId());
		clientInfo.setUsername(context.getUserId());
		clientInfo.setConnected(context.isAccepted());
		// keepAlive 心跳时间
		clientInfo.setKeepAlive(getClientKeepAlive(context, serverCreator));
		// ip、port
		Node clientNode = context.getClientNode();
		clientInfo.setIpAddress(clientNode.getIp());
		clientInfo.setPort(clientNode.getPort());
		// mqtt 版本信息
		MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(context);
		clientInfo.setProtoName(mqttVersion.protocolName());
		clientInfo.setProtoVer(mqttVersion.protocolLevel());
		clientInfo.setProtoFullName(mqttVersion.fullName());
		clientInfo.setSsl(context.sslFacadeContext != null);
		clientInfo.setWebSocket(context.containsKey("TIO_W_S_C"));
		// 时间信息
		clientInfo.setConnectedAt(context.stat.timeFirstConnected);
		clientInfo.setCreatedAt(context.stat.timeCreated);
		// 队列信息
		clientInfo.setDecodeQueueSize(context.getDecodeQueueSize());
		clientInfo.setHandlerQueueSize(context.getHandlerQueueSize());
		clientInfo.setSendQueueSize(context.getSendQueueSize());
		return clientInfo;
	}


	/**
	 * 获取客户端的心跳时长
	 *
	 * @param context       ChannelContext
	 * @param serverCreator MqttServerCreator
	 * @return 心跳时长
	 */
	public static long getClientKeepAlive(ChannelContext context, MqttServerCreator serverCreator) {
		// keepAlive
		if (context.heartbeatTimeout == null) {
			return 60L;
		} else {
			float keepAliveBackoff = serverCreator.getKeepaliveBackoff();
			long keepAliveTs = (long) (context.heartbeatTimeout / (keepAliveBackoff * 2));
			return TimeUnit.MILLISECONDS.toSeconds(keepAliveTs);
		}
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public long getKeepAlive() {
		return keepAlive;
	}

	public void setKeepAlive(long keepAlive) {
		this.keepAlive = keepAlive;
	}

	public boolean isConnected() {
		return connected;
	}

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	public String getProtoName() {
		return protoName;
	}

	public void setProtoName(String protoName) {
		this.protoName = protoName;
	}

	public int getProtoVer() {
		return protoVer;
	}

	public void setProtoVer(int protoVer) {
		this.protoVer = protoVer;
	}

	public String getProtoFullName() {
		return protoFullName;
	}

	public void setProtoFullName(String protoFullName) {
		this.protoFullName = protoFullName;
	}

	public boolean isWebSocket() {
		return webSocket;
	}

	public void setWebSocket(boolean webSocket) {
		this.webSocket = webSocket;
	}

	public boolean isSsl() {
		return ssl;
	}

	public void setSsl(boolean ssl) {
		this.ssl = ssl;
	}

	public String getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public Long getConnectedAt() {
		return connectedAt;
	}

	public void setConnectedAt(Long connectedAt) {
		this.connectedAt = connectedAt;
	}

	public long getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(long createdAt) {
		this.createdAt = createdAt;
	}

	public int getDecodeQueueSize() {
		return decodeQueueSize;
	}

	public void setDecodeQueueSize(int decodeQueueSize) {
		this.decodeQueueSize = decodeQueueSize;
	}

	public int getHandlerQueueSize() {
		return handlerQueueSize;
	}

	public void setHandlerQueueSize(int handlerQueueSize) {
		this.handlerQueueSize = handlerQueueSize;
	}

	public int getSendQueueSize() {
		return sendQueueSize;
	}

	public void setSendQueueSize(int sendQueueSize) {
		this.sendQueueSize = sendQueueSize;
	}

	@Override
	public String toString() {
		return "ClientInfo{" +
			"clientId='" + clientId + '\'' +
			", username='" + username + '\'' +
			", keepAlive=" + keepAlive +
			", connected=" + connected +
			", protoName='" + protoName + '\'' +
			", protoVer=" + protoVer +
			", protoFullName='" + protoFullName + '\'' +
			", webSocket=" + webSocket +
			", ssl=" + ssl +
			", ipAddress='" + ipAddress + '\'' +
			", port=" + port +
			", connectedAt=" + connectedAt +
			", createdAt=" + createdAt +
			", decodeQueueSize=" + decodeQueueSize +
			", handlerQueueSize=" + handlerQueueSize +
			", sendQueueSize=" + sendQueueSize +
			'}';
	}
}
