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

package org.dromara.mica.mqtt.core.server;

import org.dromara.mica.mqtt.codec.MqttConstant;
import org.dromara.mica.mqtt.core.serializer.MqttJsonSerializer;
import org.dromara.mica.mqtt.core.serializer.MqttSerializer;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerAuthHandler;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerPublishPermission;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerSubscribeValidator;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerUniqueIdService;
import org.dromara.mica.mqtt.core.server.broker.DefaultMqttBrokerDispatcher;
import org.dromara.mica.mqtt.core.server.dispatcher.AbstractMqttMessageDispatcher;
import org.dromara.mica.mqtt.core.server.dispatcher.IMqttMessageDispatcher;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.event.IMqttMessageListener;
import org.dromara.mica.mqtt.core.server.event.IMqttSessionListener;
import org.dromara.mica.mqtt.core.server.interceptor.IMqttMessageInterceptor;
import org.dromara.mica.mqtt.core.server.listener.IMqttProtocolListener;
import org.dromara.mica.mqtt.core.server.listener.MqttHttpApiListener;
import org.dromara.mica.mqtt.core.server.listener.MqttProtocolListener;
import org.dromara.mica.mqtt.core.server.listener.MqttProtocolListeners;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.session.InMemoryMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.server.store.InMemoryMqttMessageStore;
import org.dromara.mica.mqtt.core.server.support.DefaultMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.support.DefaultMqttServerAuthHandler;
import org.dromara.mica.mqtt.core.server.support.DefaultMqttServerProcessor;
import org.dromara.mica.mqtt.core.server.support.DefaultMqttServerUniqueIdServiceImpl;
import org.tio.core.Node;
import org.tio.core.task.HeartbeatMode;
import org.tio.server.TioServerConfig;
import org.tio.server.intf.TioServerHandler;
import org.tio.server.intf.TioServerListener;
import org.tio.utils.hutool.StrUtil;
import org.tio.utils.json.JsonAdapter;
import org.tio.utils.json.JsonUtil;
import org.tio.utils.thread.ThreadUtils;
import org.tio.utils.timer.DefaultTimerTaskService;
import org.tio.utils.timer.TimerTaskService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * mqtt 服务端参数构造
 *
 * @author L.cm
 * @author ChangJin Wei (魏昌进)
 */
public class MqttServerCreator {

	/**
	 * 名称
	 */
	private String name = "Mica-Mqtt-Server";
	/**
	 * 监听器
	 */
	private final List<IMqttProtocolListener> listeners = new ArrayList<>();
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
	private int readBufferSize = MqttConstant.DEFAULT_MAX_READ_BUFFER_SIZE;
	/**
	 * 消息解析最大 bytes 长度，默认：10M
	 */
	private int maxBytesInMessage = MqttConstant.DEFAULT_MAX_BYTES_IN_MESSAGE;
	/**
	 * 认证处理器
	 */
	private IMqttServerAuthHandler authHandler;
	/**
	 * 唯一 id 服务
	 */
	private IMqttServerUniqueIdService uniqueIdService;
	/**
	 * 订阅校验器
	 */
	private IMqttServerSubscribeValidator subscribeValidator;
	/**
	 * 发布权限校验
	 */
	private IMqttServerPublishPermission publishPermission;
	/**
	 * 消息处理器
	 */
	private IMqttMessageDispatcher messageDispatcher;
	/**
	 * 消息存储
	 */
	private IMqttMessageStore messageStore;
	/**
	 * session 管理
	 */
	private IMqttSessionManager sessionManager;
	/**
	 * session 监听
	 */
	private IMqttSessionListener sessionListener;
	/**
	 * 消息监听
	 */
	private IMqttMessageListener messageListener;
	/**
	 * 连接状态监听
	 */
	private IMqttConnectStatusListener connectStatusListener;
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
	 * 是否用队列发送
	 */
	private boolean useQueueSend = true;
	/**
	 * 是否用队列解码（系统初始化时确定该值，中途不要变更此值，否则在切换的时候可能导致消息丢失）
	 */
	private boolean useQueueDecode = false;
	/**
	 * 是否开启监控，不开启可节省内存，默认：true
	 */
	private boolean statEnable = true;
	/**
	 * TioConfig 自定义配置
	 */
	private Consumer<TioServerConfig> tioConfigCustomize;
	/**
	 * 消息拦截器
	 */
	private final MqttMessageInterceptors messageInterceptors = new MqttMessageInterceptors();
	/**
	 * taskService
	 */
	private TimerTaskService taskService;
	/**
	 * 业务消费线程
	 */
	private ExecutorService mqttExecutor;
	/**
	 * json 处理器
	 */
	private JsonAdapter jsonAdapter;
	/**
	 * 开启代理协议支持
	 */
	private boolean proxyProtocolOn = false;

	private MqttSerializer mqttSerializer;

	public String getName() {
		return name;
	}

	public MqttServerCreator name(String name) {
		this.name = name;
		return this;
	}


	public Long getHeartbeatTimeout() {
		return heartbeatTimeout;
	}

	public MqttServerCreator heartbeatTimeout(Long heartbeatTimeout) {
		this.heartbeatTimeout = heartbeatTimeout;
		return this;
	}

	public float getKeepaliveBackoff() {
		return keepaliveBackoff;
	}

	public MqttServerCreator keepaliveBackoff(float keepaliveBackoff) {
		if (keepaliveBackoff <= 0.5) {
			throw new IllegalArgumentException("keepalive backoff must greater than 0.5");
		}
		this.keepaliveBackoff = keepaliveBackoff;
		return this;
	}

	public int getReadBufferSize() {
		return readBufferSize;
	}

	public MqttServerCreator readBufferSize(int readBufferSize) {
		this.readBufferSize = readBufferSize;
		return this;
	}

	public int getMaxBytesInMessage() {
		return maxBytesInMessage;
	}

	public MqttServerCreator maxBytesInMessage(int maxBytesInMessage) {
		if (maxBytesInMessage < 1) {
			throw new IllegalArgumentException("maxBytesInMessage must be greater than 0.");
		}
		this.maxBytesInMessage = maxBytesInMessage;
		return this;
	}

	public IMqttServerAuthHandler getAuthHandler() {
		return authHandler;
	}

	public MqttServerCreator authHandler(IMqttServerAuthHandler authHandler) {
		this.authHandler = authHandler;
		return this;
	}

	public MqttServerCreator usernamePassword(String username, String password) {
		return authHandler(new DefaultMqttServerAuthHandler(username, password));
	}

	public IMqttServerUniqueIdService getUniqueIdService() {
		return uniqueIdService;
	}

	public MqttServerCreator uniqueIdService(IMqttServerUniqueIdService uniqueIdService) {
		this.uniqueIdService = uniqueIdService;
		return this;
	}

	public IMqttServerSubscribeValidator getSubscribeValidator() {
		return subscribeValidator;
	}

	public MqttServerCreator subscribeValidator(IMqttServerSubscribeValidator subscribeValidator) {
		this.subscribeValidator = subscribeValidator;
		return this;
	}

	public IMqttServerPublishPermission getPublishPermission() {
		return publishPermission;
	}

	public MqttServerCreator publishPermission(IMqttServerPublishPermission publishPermission) {
		this.publishPermission = publishPermission;
		return this;
	}

	public IMqttMessageDispatcher getMessageDispatcher() {
		return messageDispatcher;
	}

	public MqttServerCreator messageDispatcher(IMqttMessageDispatcher messageDispatcher) {
		this.messageDispatcher = messageDispatcher;
		return this;
	}

	public IMqttMessageStore getMessageStore() {
		return messageStore;
	}

	public MqttServerCreator messageStore(IMqttMessageStore messageStore) {
		this.messageStore = messageStore;
		return this;
	}

	public IMqttSessionManager getSessionManager() {
		return sessionManager;
	}

	public MqttServerCreator sessionManager(IMqttSessionManager sessionManager) {
		this.sessionManager = sessionManager;
		return this;
	}

	public IMqttSessionListener getSessionListener() {
		return sessionListener;
	}

	public MqttServerCreator sessionListener(IMqttSessionListener sessionListener) {
		this.sessionListener = sessionListener;
		return this;
	}

	public IMqttMessageListener getMessageListener() {
		return messageListener;
	}

	public MqttServerCreator messageListener(IMqttMessageListener messageListener) {
		this.messageListener = messageListener;
		return this;
	}

	public IMqttConnectStatusListener getConnectStatusListener() {
		return connectStatusListener;
	}

	public MqttServerCreator connectStatusListener(IMqttConnectStatusListener connectStatusListener) {
		this.connectStatusListener = connectStatusListener;
		return this;
	}

	public boolean isDebug() {
		return debug;
	}

	public MqttServerCreator debug() {
		this.debug = true;
		return this;
	}

	public int getMaxClientIdLength() {
		return maxClientIdLength;
	}

	public MqttServerCreator maxClientIdLength(int maxClientIdLength) {
		this.maxClientIdLength = maxClientIdLength;
		return this;
	}

	public String getNodeName() {
		return nodeName;
	}

	public MqttServerCreator nodeName(String nodeName) {
		this.nodeName = nodeName;
		return this;
	}

	public boolean isUseQueueSend() {
		return useQueueSend;
	}

	public MqttServerCreator useQueueSend(boolean useQueueSend) {
		this.useQueueSend = useQueueSend;
		return this;
	}

	public boolean isUseQueueDecode() {
		return useQueueDecode;
	}

	public MqttServerCreator useQueueDecode(boolean useQueueDecode) {
		this.useQueueDecode = useQueueDecode;
		return this;
	}

	public boolean isStatEnable() {
		return statEnable;
	}

	public MqttServerCreator statEnable() {
		return statEnable(true);
	}

	public MqttServerCreator statEnable(boolean enable) {
		this.statEnable = enable;
		return this;
	}

	public MqttServerCreator tioConfigCustomize(Consumer<TioServerConfig> tioConfigCustomize) {
		this.tioConfigCustomize = tioConfigCustomize;
		return this;
	}

	public MqttMessageInterceptors getMessageInterceptors() {
		return messageInterceptors;
	}

	public MqttServerCreator addInterceptor(IMqttMessageInterceptor interceptor) {
		this.messageInterceptors.add(interceptor);
		return this;
	}

	public MqttServerCreator taskService(TimerTaskService taskService) {
		this.taskService = taskService;
		return this;
	}

	public ExecutorService getMqttExecutor() {
		return mqttExecutor;
	}

	public MqttServerCreator mqttExecutor(ExecutorService mqttExecutor) {
		this.mqttExecutor = mqttExecutor;
		return this;
	}

	public JsonAdapter getJsonAdapter() {
		return jsonAdapter;
	}

	public MqttServerCreator jsonAdapter(JsonAdapter jsonAdapter) {
		this.jsonAdapter = JsonUtil.getJsonAdapter(jsonAdapter);
		return this;
	}

	public boolean isProxyProtocolEnabled() {
		return proxyProtocolOn;
	}

	public MqttServerCreator proxyProtocolEnable() {
		return proxyProtocolEnable(true);
	}

	public MqttServerCreator proxyProtocolEnable(boolean proxyProtocolOn) {
		this.proxyProtocolOn = proxyProtocolOn;
		return this;
	}

	public MqttSerializer getMqttSerializer() {
		return mqttSerializer;
	}

	public MqttServerCreator mqttSerializer(MqttSerializer mqttSerializer) {
		this.mqttSerializer = mqttSerializer;
		return this;
	}

	public MqttServerCreator enableMqtt(Function<MqttProtocolListener.Builder, MqttProtocolListener> function) {
		return addMqttProtocolListener(function.apply(MqttProtocolListener.mqttBuilder()));
	}

	public MqttServerCreator enableMqttSsl(Function<MqttProtocolListener.Builder, MqttProtocolListener> function) {
		return addMqttProtocolListener(function.apply(MqttProtocolListener.mqttSslBuilder()));
	}

	public MqttServerCreator enableMqttWs(Function<MqttProtocolListener.Builder, MqttProtocolListener> function) {
		return addMqttProtocolListener(function.apply(MqttProtocolListener.wsBuilder()));
	}

	public MqttServerCreator enableMqttWss(Function<MqttProtocolListener.Builder, MqttProtocolListener> function) {
		return addMqttProtocolListener(function.apply(MqttProtocolListener.wssBuilder()));
	}

	public MqttServerCreator enableMqttHttpApi(Function<MqttHttpApiListener.Builder, MqttHttpApiListener> function) {
		return addMqttProtocolListener(function.apply(MqttHttpApiListener.builder()));
	}

	private MqttServerCreator addMqttProtocolListener(IMqttProtocolListener listener) {
		boolean contains = this.listeners.contains(listener);
		if (contains) {
			String protocolName = listener.getProtocol().name();
			Node serverNode = listener.getServerNode();
			throw new IllegalStateException("Mqtt protocol:" + protocolName + " serverNode:" + serverNode + " already exists");
		}
		this.listeners.add(listener);
		return this;
	}

	public MqttServer build() {
		// 默认的节点名称，用于集群
		if (StrUtil.isBlank(this.nodeName)) {
			this.nodeName = StrUtil.getNanoId();
		}
		if (this.uniqueIdService == null) {
			this.uniqueIdService = new DefaultMqttServerUniqueIdServiceImpl();
		}
		if (this.messageDispatcher == null) {
			this.messageDispatcher = new DefaultMqttBrokerDispatcher();
		}
		if (this.sessionManager == null) {
			this.sessionManager = new InMemoryMqttSessionManager();
		}
		if (this.messageStore == null) {
			this.messageStore = new InMemoryMqttMessageStore();
		}
		if (this.connectStatusListener == null) {
			this.connectStatusListener = new DefaultMqttConnectStatusListener();
		}
		// taskService
		if (this.taskService == null) {
			this.taskService = new DefaultTimerTaskService(200L, 60);
		}
		// 业务线程池
		if (this.mqttExecutor == null) {
			this.mqttExecutor = ThreadUtils.getBizExecutor(ThreadUtils.MAX_POOL_SIZE_FOR_TIO);
		}
		// 序列化
		if (this.mqttSerializer == null) {
			this.mqttSerializer = new MqttJsonSerializer();
		}
		// 监听器为空，开启默认的 mqtt server
		if (this.listeners.isEmpty()) {
			this.listeners.add(MqttProtocolListener.mqttBuilder().build());
		}
		// AckService
		DefaultMqttServerProcessor serverProcessor = new DefaultMqttServerProcessor(this, this.taskService, mqttExecutor);
		// 1. 处理消息
		TioServerHandler handler = new MqttServerAioHandler(this, serverProcessor);
		// 2. t-io 监听
		TioServerListener listener = new MqttServerAioListener(this);
		// 3. t-io 配置
		TioServerConfig tioConfig = new TioServerConfig(this.name, handler, listener);
		tioConfig.setUseQueueDecode(this.useQueueDecode);
		tioConfig.setUseQueueSend(this.useQueueSend);
		tioConfig.setTaskService(this.taskService);
		tioConfig.statOn = this.statEnable;
		// 4. mqtt 消息最大长度，小于 1 则使用默认的，可通过 property tio.default.read.buffer.size 设置默认大小
		if (this.readBufferSize > 0) {
			tioConfig.setReadBufferSize(this.readBufferSize);
		}
		// 5. 是否开启代理协议
		tioConfig.enableProxyProtocol(this.proxyProtocolOn);
		// 6. 设置 t-io 心跳 timeout
		if (this.heartbeatTimeout != null) {
			tioConfig.setHeartbeatTimeout(this.heartbeatTimeout);
		}
		tioConfig.setHeartbeatBackoff(this.keepaliveBackoff);
		tioConfig.setHeartbeatMode(HeartbeatMode.LAST_RESP);
		if (this.debug) {
			tioConfig.debug = true;
		}
		// 自定义处理
		if (this.tioConfigCustomize != null) {
			this.tioConfigCustomize.accept(tioConfig);
		}
		// 配置 json
		this.jsonAdapter(JsonUtil.getJsonAdapter(getJsonAdapter()));
		// MqttServer
		MqttProtocolListeners listeners = new MqttProtocolListeners(this, tioConfig, this.listeners);
		MqttServer mqttServer = new MqttServer(this, tioConfig, listeners);
		// 如果是默认的消息转发器，设置 mqttServer
		if (this.messageDispatcher instanceof AbstractMqttMessageDispatcher) {
			((AbstractMqttMessageDispatcher) this.messageDispatcher).config(mqttServer);
		}
		return mqttServer;
	}

	public MqttServer start() {
		MqttServer mqttServer = this.build();
		mqttServer.start();
		return mqttServer;
	}
}
