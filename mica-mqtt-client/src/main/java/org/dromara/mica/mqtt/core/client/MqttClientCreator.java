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

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.*;
import org.dromara.mica.mqtt.core.serializer.MqttJsonSerializer;
import org.dromara.mica.mqtt.core.serializer.MqttSerializer;
import org.tio.client.ReconnConf;
import org.tio.client.TioClient;
import org.tio.client.TioClientConfig;
import org.tio.client.intf.TioClientHandler;
import org.tio.client.intf.TioClientListener;
import org.tio.client.task.HeartbeatTimeoutStrategy;
import org.tio.core.Node;
import org.tio.core.TioConfig;
import org.tio.core.ssl.SslConfig;
import org.tio.core.task.HeartbeatMode;
import org.tio.utils.hutool.StrUtil;
import org.tio.utils.thread.ThreadUtils;
import org.tio.utils.thread.pool.SynThreadPoolExecutor;
import org.tio.utils.timer.DefaultTimerTaskService;
import org.tio.utils.timer.TimerTaskService;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * mqtt 客户端构造器
 *
 * @author L.cm
 * @author ChangJin Wei (魏昌进)
 */
public final class MqttClientCreator {
	/**
	 * 默认的心跳超时
	 */
	public static final int DEFAULT_KEEP_ALIVE_SECS = 60;
	/**
	 * 名称
	 */
	private String name = "Mica-Mqtt-Client";
	/**
	 * ip，可为空，默认为 127.0.0.1
	 */
	private String ip = "127.0.0.1";
	/**
	 * 端口，默认：1883
	 */
	private int port = 1883;
	/**
	 * 超时时间，t-io 配置，可为 null，默认为：5秒
	 */
	private Integer timeout;
	/**
	 * 接收数据的 buffer size，默认：8k
	 */
	private int readBufferSize = MqttConstant.DEFAULT_MAX_READ_BUFFER_SIZE;
	/**
	 * 消息解析最大 bytes 长度，默认：8092
	 */
	private int maxBytesInMessage = MqttConstant.DEFAULT_MAX_BYTES_IN_MESSAGE;
	/**
	 * mqtt 3.1 会校验此参数为 23，为了减少问题设置成了 64
	 */
	private int maxClientIdLength = MqttConstant.DEFAULT_MAX_CLIENT_ID_LENGTH;
	/**
	 * Keep Alive (s)，如果用户不希望框架层面做心跳相关工作，请把此值设为0或负数
	 */
	private int keepAliveSecs = DEFAULT_KEEP_ALIVE_SECS;
	/**
	 * 心跳检测模式，默认：最后请求时间
	 */
	private HeartbeatMode heartbeatMode = HeartbeatMode.LAST_REQ;
	/**
	 * 心跳超时策略，默认：发送 ping
	 */
	private HeartbeatTimeoutStrategy heartbeatTimeoutStrategy = HeartbeatTimeoutStrategy.PING;
	/**
	 * SSL配置
	 */
	private SslConfig sslConfig;
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
	 * 客户端 id，默认：随机生成
	 */
	private String clientId;
	/**
	 * mqtt 协议，默认：MQTT_5
	 */
	private MqttVersion version = MqttVersion.MQTT_5;
	/**
	 * 用户名
	 */
	private String username = null;
	/**
	 * 密码
	 */
	private String password = null;
	/**
	 * 清除会话
	 * <p>
	 * false 表示如果订阅的客户机断线了，那么要保存其要推送的消息，如果其重新连接时，则将这些消息推送。
	 * true 表示消除，表示客户机是第一次连接，消息所以以前的连接信息。
	 * </p>
	 */
	private boolean cleanStart = true;
	/**
	 * mqtt 5.0 session 有效期，单位秒
	 */
	private Integer sessionExpiryIntervalSecs;
	/**
	 * 遗嘱消息
	 */
	private MqttWillMessage willMessage;
	/**
	 * mqtt5 properties
	 */
	private MqttProperties properties;
	/**
	 * 连接监听器
	 */
	private IMqttClientConnectListener connectListener;
	/**
	 * 全局订阅
	 */
	private Set<MqttTopicSubscription> globalSubscribe;
	/**
	 * 全局消息监听器
	 */
	private IMqttClientGlobalMessageListener globalMessageListener;
	/**
	 * 客户端 session
	 */
	private IMqttClientSession clientSession;
	/**
	 * 是否开启监控，默认：false 不开启，节省内存
	 */
	private boolean statEnable = false;
	/**
	 * debug
	 */
	private boolean debug = false;
	/**
	 * tioExecutor
	 */
	private SynThreadPoolExecutor tioExecutor;
	/**
	 * groupExecutor
	 */
	private ExecutorService groupExecutor;
	/**
	 * mqttExecutor
	 */
	private ExecutorService mqttExecutor;
	/**
	 * taskService
	 */
	private TimerTaskService taskService;
	/**
	 * TioConfig 自定义配置
	 */
	private Consumer<TioConfig> tioConfigCustomize;

	/**
	 * 序列化
	 */
	private MqttSerializer mqttSerializer;

	public String getName() {
		return name;
	}

	public String getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}

	public Integer getTimeout() {
		return timeout;
	}

	public int getReadBufferSize() {
		return readBufferSize;
	}

	public int getMaxBytesInMessage() {
		return maxBytesInMessage;
	}

	public int getMaxClientIdLength() {
		return maxClientIdLength;
	}

	public int getKeepAliveSecs() {
		return keepAliveSecs;
	}

	public HeartbeatMode getHeartbeatMode() {
		return heartbeatMode;
	}

	public HeartbeatTimeoutStrategy getHeartbeatTimeoutStrategy() {
		return heartbeatTimeoutStrategy;
	}

	public SslConfig getSslConfig() {
		return sslConfig;
	}

	public boolean isReconnect() {
		return reconnect;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public long getReInterval() {
		return reInterval;
	}

	public int getReSubscribeBatchSize() {
		return reSubscribeBatchSize;
	}

	public String getClientId() {
		return clientId;
	}

	public MqttVersion getVersion() {
		return version;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public boolean isCleanStart() {
		return cleanStart;
	}

	public Integer getSessionExpiryIntervalSecs() {
		return sessionExpiryIntervalSecs;
	}

	public MqttWillMessage getWillMessage() {
		return willMessage;
	}

	public MqttProperties getProperties() {
		return properties;
	}

	public IMqttClientConnectListener getConnectListener() {
		return connectListener;
	}

	public Set<MqttTopicSubscription> getGlobalSubscribe() {
		return globalSubscribe;
	}

	public IMqttClientGlobalMessageListener getGlobalMessageListener() {
		return globalMessageListener;
	}

	public IMqttClientSession getClientSession() {
		return clientSession;
	}

	public boolean isStatEnable() {
		return statEnable;
	}

	public boolean isDebug() {
		return debug;
	}

	public SynThreadPoolExecutor getTioExecutor() {
		return tioExecutor;
	}

	public ExecutorService getGroupExecutor() {
		return groupExecutor;
	}

	public ExecutorService getMqttExecutor() {
		return mqttExecutor;
	}

	public TimerTaskService getTaskService() {
		return taskService;
	}

	public MqttSerializer getMqttSerializer() {
		return mqttSerializer;
	}

	public MqttClientCreator name(String name) {
		this.name = name;
		return this;
	}

	public MqttClientCreator ip(String ip) {
		this.ip = ip;
		return this;
	}

	public MqttClientCreator port(int port) {
		this.port = port;
		return this;
	}

	public MqttClientCreator timeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	public MqttClientCreator readBufferSize(int readBufferSize) {
		this.readBufferSize = readBufferSize;
		return this;
	}

	public MqttClientCreator maxBytesInMessage(int maxBytesInMessage) {
		this.maxBytesInMessage = maxBytesInMessage;
		return this;
	}

	public MqttClientCreator maxClientIdLength(int maxClientIdLength) {
		this.maxClientIdLength = maxClientIdLength;
		return this;
	}

	public MqttClientCreator keepAliveSecs(int keepAliveSecs) {
		this.keepAliveSecs = keepAliveSecs;
		return this;
	}

	public MqttClientCreator heartbeatMode(HeartbeatMode heartbeatMode) {
		this.heartbeatMode = heartbeatMode;
		return this;
	}

	public MqttClientCreator heartbeatTimeoutStrategy(HeartbeatTimeoutStrategy heartbeatTimeoutStrategy) {
		this.heartbeatTimeoutStrategy = heartbeatTimeoutStrategy;
		return this;
	}

	public MqttClientCreator useSsl() {
		return sslConfig(SslConfig.forClient());
	}

	public MqttClientCreator useSsl(String trustStoreFile, String trustPassword) {
		return sslConfig(SslConfig.forClient(trustStoreFile, trustPassword));
	}

	public MqttClientCreator useSsl(String keyStoreFile, String keyPasswd, String trustStoreFile, String trustPassword) {
		return sslConfig(SslConfig.forClient(keyStoreFile, keyPasswd, trustStoreFile, trustPassword));
	}

	public MqttClientCreator useSsl(InputStream trustStoreInputStream, String trustPassword) {
		return sslConfig(SslConfig.forClient(trustStoreInputStream, trustPassword));
	}

	public MqttClientCreator useSsl(InputStream keyStoreInputStream, String keyPasswd, InputStream trustStoreInputStream, String trustPassword) {
		return sslConfig(SslConfig.forClient(keyStoreInputStream, keyPasswd, trustStoreInputStream, trustPassword));
	}

	public MqttClientCreator sslConfig(SslConfig sslConfig) {
		this.sslConfig = sslConfig;
		return this;
	}

	public MqttClientCreator reconnect(boolean reconnect) {
		this.reconnect = reconnect;
		return this;
	}

	public MqttClientCreator retryCount(int retryCount) {
		this.retryCount = retryCount;
		return this;
	}

	public MqttClientCreator reInterval(long reInterval) {
		this.reInterval = reInterval;
		return this;
	}

	public MqttClientCreator reSubscribeBatchSize(int reSubscribeBatchSize) {
		this.reSubscribeBatchSize = reSubscribeBatchSize;
		return this;
	}

	public MqttClientCreator clientId(String clientId) {
		this.clientId = clientId;
		return this;
	}

	public MqttClientCreator version(MqttVersion version) {
		this.version = version;
		return this;
	}

	public MqttClientCreator username(String username) {
		this.username = username;
		return this;
	}

	public MqttClientCreator password(String password) {
		this.password = password;
		return this;
	}

	public MqttClientCreator cleanStart(boolean cleanStart) {
		this.cleanStart = cleanStart;
		return this;
	}

	public MqttClientCreator sessionExpiryIntervalSecs(Integer sessionExpiryIntervalSecs) {
		this.sessionExpiryIntervalSecs = sessionExpiryIntervalSecs;
		return this;
	}

	public MqttClientCreator willMessage(MqttWillMessage willMessage) {
		this.willMessage = willMessage;
		return this;
	}

	public MqttClientCreator willMessage(Consumer<MqttWillMessage.Builder> consumer) {
		MqttWillMessage.Builder builder = MqttWillMessage.builder();
		consumer.accept(builder);
		return willMessage(builder.build());
	}

	public MqttClientCreator properties(MqttProperties properties) {
		this.properties = properties;
		return this;
	}

	public MqttClientCreator connectListener(IMqttClientConnectListener connectListener) {
		this.connectListener = connectListener;
		return this;
	}

	public MqttClientCreator globalSubscribe(String... topics) {
		Objects.requireNonNull(topics, "globalSubscribe topics is null.");
		List<MqttTopicSubscription> subscriptionList = Arrays.stream(topics)
			.map(MqttTopicSubscription::new)
			.collect(Collectors.toList());
		return globalSubscribe(subscriptionList);
	}

	public MqttClientCreator globalSubscribe(MqttTopicSubscription... topics) {
		Objects.requireNonNull(topics, "globalSubscribe topics is null.");
		return globalSubscribe(Arrays.asList(topics));
	}

	public MqttClientCreator globalSubscribe(List<MqttTopicSubscription> topicList) {
		Objects.requireNonNull(topicList, "globalSubscribe topicList is null.");
		if (this.globalSubscribe == null) {
			this.globalSubscribe = new HashSet<>(topicList);
		} else {
			this.globalSubscribe.addAll(topicList);
		}
		return this;
	}

	public MqttClientCreator globalMessageListener(IMqttClientGlobalMessageListener globalMessageListener) {
		this.globalMessageListener = globalMessageListener;
		return this;
	}

	public MqttClientCreator clientSession(IMqttClientSession clientSession) {
		this.clientSession = clientSession;
		return this;
	}

	public MqttClientCreator statEnable() {
		return statEnable(true);
	}

	public MqttClientCreator statEnable(boolean enable) {
		this.statEnable = enable;
		return this;
	}

	public MqttClientCreator debug() {
		return debug(true);
	}

	public MqttClientCreator debug(boolean debug) {
		this.debug = debug;
		return this;
	}

	public MqttClientCreator tioExecutor(SynThreadPoolExecutor tioExecutor) {
		this.tioExecutor = tioExecutor;
		return this;
	}

	public MqttClientCreator groupExecutor(ExecutorService groupExecutor) {
		this.groupExecutor = groupExecutor;
		return this;
	}

	public MqttClientCreator mqttExecutor(ExecutorService mqttExecutor) {
		this.mqttExecutor = mqttExecutor;
		return this;
	}

	public MqttClientCreator bizThreadPoolSize(int poolSize) {
		if (poolSize <= 0) {
			throw new IllegalArgumentException("poolSize must be greater than zero.");
		}
		return mqttExecutor(ThreadUtils.getBizExecutor(poolSize));
	}

	public MqttClientCreator taskService(TimerTaskService taskService) {
		this.taskService = taskService;
		return this;
	}

	public MqttClientCreator tioConfigCustomize(Consumer<TioConfig> tioConfigCustomize) {
		this.tioConfigCustomize = tioConfigCustomize;
		return this;
	}

	public MqttClientCreator mqttJsonSerializer(MqttSerializer mqttSerializer) {
		this.mqttSerializer = mqttSerializer;
		return this;
	}

	public MqttClientCreator newCreator() {
		return new MqttClientCreator()
			.name(this.name)
			.ip(this.ip)
			.port(this.port)
			.timeout(this.timeout)
			.readBufferSize(this.readBufferSize)
			.maxBytesInMessage(this.maxBytesInMessage)
			.maxClientIdLength(this.maxClientIdLength)
			.keepAliveSecs(this.keepAliveSecs)
			.sslConfig(this.sslConfig)
			.reconnect(this.reconnect)
			.reInterval(this.reInterval)
			.retryCount(this.retryCount)
			.reSubscribeBatchSize(this.reSubscribeBatchSize)
			.clientId(this.clientId)
			.version(this.version)
			.username(this.username)
			.password(this.password)
			.cleanStart(this.cleanStart)
			.sessionExpiryIntervalSecs(this.sessionExpiryIntervalSecs)
			.willMessage(this.willMessage)
			.connectListener(this.connectListener)
			.statEnable(this.statEnable)
			.debug(this.debug);
	}

	private MqttClient build() {
		// 1. clientId 为空，生成默认的 clientId
		if (StrUtil.isBlank(this.clientId)) {
			// 默认为：MICA-MQTT- 前缀和 36进制的纳秒数
			this.clientId("MICA-MQTT-" + Long.toString(System.nanoTime(), 36));
		}
		// 2. 客户端 session
		if (this.clientSession == null) {
			this.clientSession = new DefaultMqttClientSession();
		}
		// tioExecutor
		if (this.tioExecutor == null) {
			this.tioExecutor = ThreadUtils.getTioExecutor(3);
		}
		// groupExecutor
		if (this.groupExecutor == null) {
			this.groupExecutor = ThreadUtils.getGroupExecutor(2);
		}
		// mqttExecutor
		if (this.mqttExecutor == null) {
			this.mqttExecutor = ThreadUtils.getBizExecutor(Math.max(2, ThreadUtils.CORE_POOL_SIZE));
		}
		// taskService
		if (this.taskService == null) {
			this.taskService = new DefaultTimerTaskService();
		}
		// heartbeatMode
		if (this.heartbeatMode == null) {
			this.heartbeatMode = HeartbeatMode.LAST_REQ;
		}
		if (this.mqttSerializer == null) {
			this.mqttSerializer = new MqttJsonSerializer();
		}
		IMqttClientProcessor processor = new DefaultMqttClientProcessor(this);
		// 4. 初始化 mqtt 处理器
		TioClientHandler clientAioHandler = new MqttClientAioHandler(this, processor);
		TioClientListener clientAioListener = new MqttClientAioListener(this);
		// 5. 重连配置
		ReconnConf reconnConf = null;
		if (this.reconnect) {
			reconnConf = new ReconnConf(this.reInterval, this.retryCount);
		}
		// 6. tioConfig
		TioClientConfig clientConfig = new TioClientConfig(clientAioHandler, clientAioListener, reconnConf, tioExecutor, groupExecutor);
		clientConfig.setName(this.name);
		// 7. 心跳超时时间
		clientConfig.setHeartbeatTimeout(TimeUnit.SECONDS.toMillis(this.keepAliveSecs));
		// 设置心跳检测模式为 LAST_REQ，keepAliveSecs 周期内，最后发送的时间差
		clientConfig.setHeartbeatMode(this.heartbeatMode);
		clientConfig.setHeartbeatTimeoutStrategy(this.heartbeatTimeoutStrategy);
		// 8. mqtt 消息最大长度，小于 1 则使用默认的，可通过 property tio.default.read.buffer.size 设置默认大小
		if (this.readBufferSize > 0) {
			clientConfig.setReadBufferSize(this.readBufferSize);
		}
		// 9. ssl 证书设置
		if (this.sslConfig != null) {
			clientConfig.setSslConfig(this.sslConfig);
			// 内置 ssl 自定义配置，对 SNI 的支持
			if (this.sslConfig.getSslEngineCustomizer() == null) {
				this.sslConfig.setSslEngineCustomizer(new MqttSSLEngineCustomizer(ip));
			}
		}
		// 10. 是否开启监控
		clientConfig.statOn = this.statEnable;
		if (this.debug) {
			clientConfig.debug = true;
		}
		// 11. 自定义处理
		if (this.tioConfigCustomize != null) {
			this.tioConfigCustomize.accept(clientConfig);
		}
		// 绑定 clientId 到 context 上，可以 context.getId() 获取
		clientConfig.setTioUuid(new MqttClientId(this));
		// 12. tioClient
		try {
			TioClient tioClient = new TioClient(clientConfig);
			return new MqttClient(tioClient, this);
		} catch (Exception e) {
			throw new IllegalStateException("Mica mqtt client start fail.", e);
		}
	}

	/**
	 * 默认异步连接
	 *
	 * @return TioClient
	 */
	public MqttClient connect() {
		return this.build().start(false);
	}

	/**
	 * 同步连接
	 *
	 * @return TioClient
	 */
	public MqttClient connectSync() {
		return this.build().start(true);
	}

	/**
	 * 连接测试
	 *
	 * @return MqttConnectReasonCode
	 */
	public MqttConnectReasonCode connectTest() {
		return connectTest(3, TimeUnit.SECONDS);
	}

	/**
	 * 连接测试
	 *
	 * @param timeout  timeout
	 * @param timeUnit TimeUnit
	 * @return MqttConnectReasonCode
	 */
	public MqttConnectReasonCode connectTest(long timeout, TimeUnit timeUnit) {
		// 1. clientId 为空，生成默认的 clientId
		if (StrUtil.isBlank(this.clientId)) {
			// 默认为：MICA-MQTT- 前缀和 36进制的纳秒数
			this.clientId("MICA-MQTT-" + Long.toString(System.nanoTime(), 36));
		}
		CompletableFuture<MqttConnectReasonCode> future = new CompletableFuture<>();
		IMqttClientProcessor processor = new MqttClientConnectTestProcessor(future);
		// 2. 初始化 mqtt 处理器
		TioClientHandler clientAioHandler = new MqttClientAioHandler(this, processor);
		TioClientListener clientAioListener = new MqttClientAioListener(this);
		// 3. tioConfig
		TioClientConfig tioConfig = new TioClientConfig(clientAioHandler, clientAioListener);
		tioConfig.setName(this.name);
		// 4. 心跳超时时间，关闭心跳检测
		tioConfig.setHeartbeatTimeout(0);
		TioClient tioClient;
		try {
			tioClient = new TioClient(tioConfig);
			tioClient.asyncConnect(new Node(this.getIp(), this.getPort()));
		} catch (Exception e) {
			throw new IllegalStateException("Mica mqtt client start fail.", e);
		}
		try {
			return future.get(timeout, timeUnit);
		} catch (Exception e) {
			// 超时，一般为服务器不可用
			return MqttConnectReasonCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;
		} finally {
			tioClient.stop();
		}
	}

}
