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

import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Tio;
import net.dreamlu.mica.net.core.TioConfig;
import net.dreamlu.mica.net.core.stat.vo.StatVo;
import net.dreamlu.mica.net.server.TioServerConfig;
import net.dreamlu.mica.net.server.task.ServerHeartbeatTask;
import net.dreamlu.mica.net.utils.hutool.StrUtil;
import net.dreamlu.mica.net.utils.mica.IntPair;
import net.dreamlu.mica.net.utils.thread.ThreadUtils;
import net.dreamlu.mica.net.utils.page.Page;
import net.dreamlu.mica.net.utils.page.PageUtils;
import net.dreamlu.mica.net.utils.timer.TimerTask;
import net.dreamlu.mica.net.utils.timer.TimerTaskService;
import org.dromara.mica.mqtt.codec.MqttCodecUtil;
import org.dromara.mica.mqtt.codec.MqttDecoder;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.codes.MqttDisconnectReasonCode;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttDisconnectBuilder;
import org.dromara.mica.mqtt.codec.message.properties.MqttDisconnectProperties;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttProperty;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.dromara.mica.mqtt.core.common.MqttPendingPublish;
import org.dromara.mica.mqtt.core.common.TopicFilter;
import org.dromara.mica.mqtt.core.serializer.MqttSerializer;
import org.dromara.mica.mqtt.core.server.enums.MessageType;
import org.dromara.mica.mqtt.core.server.listener.MqttProtocolListeners;
import org.dromara.mica.mqtt.core.server.model.ClientInfo;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.PublishBacklogEntry;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.session.IMqttSessionManager;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * mqtt 服务端
 *
 * @author L.cm
 * @author ChangJin Wei (魏昌进)
 */
public final class MqttServer {
	private static final Logger logger = LoggerFactory.getLogger(MqttServer.class);
	private final MqttServerCreator serverCreator;
	private final TioServerConfig serverConfig;
	private final TimerTaskService taskService;
	private final MqttProtocolListeners listeners;
	private final IMqttSessionManager sessionManager;
	private final IMqttMessageStore messageStore;
	private final MqttSerializer mqttSerializer;

	MqttServer(MqttServerCreator serverCreator,
	           TioServerConfig serverConfig,
	           MqttProtocolListeners listeners) {
		this.serverCreator = serverCreator;
		this.serverConfig = serverConfig;
		this.taskService = serverConfig.getTaskService();
		this.listeners = listeners;
		this.sessionManager = serverCreator.getSessionManager();
		this.messageStore = serverCreator.getMessageStore();
		this.mqttSerializer = serverCreator.getMqttSerializer();
	}

	public static MqttServerCreator create() {
		return new MqttServerCreator();
	}

	/**
	 * 获取 ServerTioConfig
	 *
	 * @return the serverTioConfig
	 */
	public TioServerConfig getServerConfig() {
		return this.serverConfig;
	}

	/**
	 * 获取 mqtt 配置
	 *
	 * @return MqttServerCreator
	 */
	public MqttServerCreator getServerCreator() {
		return serverCreator;
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, Object payload) {
		return publish(clientId, topic, payload, MqttQoS.QOS0);
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param qos      MqttQoS
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, Object payload, MqttQoS qos) {
		return publish(clientId, topic, payload, qos, false);
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param retain   是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, Object payload, boolean retain) {
		return publish(clientId, topic, payload, MqttQoS.QOS0, retain);
	}

	/**
	 * 向客户端发送 AUTH 报文（MQTT 5.0 扩展认证，spec 3.15）。
	 * <p>
	 * 可在以下场景使用：
	 * <ul>
	 *     <li>CONNECT 后服务端发起 challenge：reason code = CONTINUE_AUTHENTICATION (0x18)；</li>
	 *     <li>已建立会话后服务端发起 REAUTHENTICATE：reason code = RE_AUTHENTICATE (0x19)；</li>
	 *     <li>响应客户端 AUTH：reason code 由 {@link org.dromara.mica.mqtt.core.server.handler.MqttAuthHandler} 内部处理。</li>
	 * </ul>
	 *
	 * @param context     ChannelContext
	 * @param authMessage 已构建的 AUTH 报文
	 * @return 是否发送成功
	 */
	public boolean sendAuth(ChannelContext context, org.dromara.mica.mqtt.codec.message.MqttMessage authMessage) {
		if (context == null || context.isClosed()) {
			return false;
		}
		return Tio.send(context, authMessage);
	}

	/**
	 * 发布消息
	 *
	 * @param clientId clientId
	 * @param topic    topic
	 * @param payload  消息体
	 * @param qos      MqttQoS
	 * @param retain   是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, Object payload, MqttQoS qos, boolean retain) {
		return publish(clientId, topic, payload, qos, retain, null);
	}

	/**
	 * 发布消息给指定客户端，支持传入 MQTT 5.0 属性
	 *
	 * @param clientId   clientId
	 * @param topic      topic
	 * @param payload    消息体
	 * @param qos        MqttQoS
	 * @param retain     是否在服务器上保留消息
	 * @param properties MQTT 5.0 属性
	 * @return 是否发送成功
	 */
	public boolean publish(String clientId, String topic, Object payload, MqttQoS qos, boolean retain, MqttProperties properties) {
		// 校验 topic
		TopicUtil.validateTopicName(topic);
		// 存储保留消息
		if (retain) {
			IntPair<String> retainPair = TopicUtil.retainTopicName(topic);
			int timeOut = retainPair.getKey();
			if (timeOut < 0) {
				logger.error("MqttPublishMessage topic {} 不符合 $retain/${ttl}/topic 规则.", topic);
				return false;
			}
			topic = retainPair.getValue();
			this.saveRetainMessage(topic, timeOut, qos, payload);
		}
		// 获取 context
		ChannelContext context = Tio.getByBsId(getServerConfig(), clientId);
		if (context == null || context.isClosed()) {
			logger.warn("Mqtt Topic:{} publish to clientId:{} ChannelContext is null may be disconnected.", topic, clientId);
			return false;
		}
		Byte subMqttQoS = sessionManager.searchSubscribe(topic, clientId);
		if (subMqttQoS == null) {
			logger.warn("Mqtt Topic:{} publish but clientId:{} not subscribed.", topic, clientId);
			return false;
		}
		return publish(context, clientId, topic, payload, qos, subMqttQoS, retain, properties);
	}

	/**
	 * 直接发布消息
	 *
	 * @param context    ChannelContext
	 * @param clientId   clientId
	 * @param topic      topic
	 * @param payload    消息体
	 * @param qos        MqttQoS
	 * @param subMqttQoS MqttQoS
	 * @param retain     是否在服务器上保留消息
	 * @param properties MqttProperties
	 * @return 是否发送成功
	 */
	public boolean publish(ChannelContext context, String clientId, String topic, Object payload,
	                       MqttQoS qos, int subMqttQoS, boolean retain, MqttProperties properties
	) {
		// qos 降级处理，按订阅降级
		MqttQoS mqttQoS = qos.value() > subMqttQoS ? MqttQoS.valueOf(subMqttQoS) : qos;
		// 判断是否高版本 qos
		boolean isHighLevelQoS = MqttQoS.QOS1 == mqttQoS || MqttQoS.QOS2 == mqttQoS;
		// 消息id
		int messageId = isHighLevelQoS ? sessionManager.getPacketId(clientId) : -1;
		byte[] newPayload = payload instanceof byte[] ? (byte[]) payload : mqttSerializer.serialize(payload);
		MqttPublishMessage message = MqttPublishMessage.builder()
			.topicName(topic)
			.payload(newPayload)
			.qos(mqttQoS)
			.retained(retain)
			.messageId(messageId)
			.properties(properties)
			.build();
		// 先启动高 qos 的重试
		if (isHighLevelQoS) {
			MqttPendingPublish pendingPublish = new MqttPendingPublish(message, qos);
			if (!sessionManager.tryAddPendingPublish(clientId, messageId, pendingPublish)) {
				PublishBacklogEntry entry = new PublishBacklogEntry(topic, newPayload, qos, subMqttQoS, retain, properties);
				sessionManager.addPendingPublishBacklog(clientId, entry);
				if (logger.isDebugEnabled()) {
					logger.debug("MQTT Topic:{} qos:{} publish backloged (Receive Maximum exceeded) clientId:{} pending:{} limit:{} backlog:{}",
						topic, mqttQoS, clientId,
						sessionManager.getPendingPublishCount(clientId),
						sessionManager.getClientReceiveMaximum(clientId),
						sessionManager.getPendingPublishBacklogSize(clientId));
				}
				return true;
			}
			pendingPublish.startPublishRetransmissionTimer(taskService, context);
		}
		// 发送消息
		boolean result = Tio.send(context, message);
		logger.debug("MQTT Topic:{} qos:{} retain:{} publish clientId:{} result:{}", topic, qos, retain, clientId, result);
		return result;
	}

	/**
	 * PR7（P1.7）：从 clientId 的 backlog 队列中取出一条待发送 PUBLISH 并重新走 {@link #publish} 路径。
	 * <p>
	 * 在 PUBACK/PUBCOMP 处理线程上调用：释放 in-flight 配额后，循环尝试发送 backlog 里的下一条
	 * （可能正好空出多个配额，因此用 while 直到 Receive Maximum 重新填满或 backlog 空）。
	 * 不会无限循环：每条新发出的 PUBLISH 会重新占用一个 in-flight 配额。
	 *
	 * @param context ChannelContext（用于在 publish 中重新构造 ChannelContext 路径）
	 * @param clientId clientId
	 */
	public void drainPublishBacklog(ChannelContext context, String clientId) {
		// 循环直到 Receive Maximum 满或 backlog 空。防御性设置上限避免极端情况下死循环。
		int maxIterations = sessionManager.getClientReceiveMaximum(clientId);
		if (maxIterations < 1) {
			maxIterations = 1;
		}
		int sent = 0;
		while (sent < maxIterations && !isReceiveMaximumExceeded(clientId)) {
			PublishBacklogEntry entry = sessionManager.pollPendingPublishBacklog(clientId);
			if (entry == null) {
				break;
			}
			try {
				publish(context, clientId,
					entry.getTopic(), entry.getPayload(),
					entry.getQos(), entry.getSubMqttQoS(), entry.isRetain(), entry.getProperties());
			} catch (Throwable e) {
				logger.error("Mqtt server drainPublishBacklog clientId:{} topic:{} error.", clientId, entry.getTopic(), e);
			}
			sent++;
		}
		if (logger.isDebugEnabled() && sent > 0) {
			logger.debug("Mqtt server drainPublishBacklog clientId:{} sent:{} remaining:{}",
				clientId, sent, sessionManager.getPendingPublishBacklogSize(clientId));
		}
	}

	private boolean isReceiveMaximumExceeded(String clientId) {
		int receiveMaximum = sessionManager.getClientReceiveMaximum(clientId);
		if (receiveMaximum < 1) {
			logger.warn("MQTT publish blocked due to invalid Receive Maximum clientId:{} receiveMaximum:{}", clientId, receiveMaximum);
			return true;
		}
		int pendingCount = sessionManager.getPendingPublishCount(clientId);
		return pendingCount >= receiveMaximum;
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, Object payload) {
		return publishAll(topic, payload, MqttQoS.QOS0);
	}

	/**
	 * 发布消息
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, Object payload, MqttQoS qos) {
		return publishAll(topic, payload, qos, false);
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, Object payload, boolean retain) {
		return publishAll(topic, payload, MqttQoS.QOS0, retain);
	}

	/**
	 * 发布消息给所以的在线设备
	 *
	 * @param topic   topic
	 * @param payload 消息体
	 * @param qos     MqttQoS
	 * @param retain  是否在服务器上保留消息
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, Object payload, MqttQoS qos, boolean retain) {
		return publishAll(topic, payload, qos, retain, null);
	}

	/**
	 * 发布消息给所有订阅者，支持传入 MQTT 5.0 属性
	 *
	 * @param topic      topic
	 * @param payload    消息体
	 * @param qos        MqttQoS
	 * @param retain     是否在服务器上保留消息
	 * @param properties MQTT 5.0 属性
	 * @return 是否发送成功
	 */
	public boolean publishAll(String topic, Object payload, MqttQoS qos, boolean retain, MqttProperties properties) {
		// 校验 topic
		TopicUtil.validateTopicName(topic);
		// 存储保留消息
		if (retain) {
			IntPair<String> retainPair = TopicUtil.retainTopicName(topic);
			int timeOut = retainPair.getKey();
			if (timeOut < 0) {
				logger.error("MqttPublishMessage topic {} 不符合 $retain/${ttl}/topic 规则.", topic);
				return false;
			}
			topic = retainPair.getValue();
			this.saveRetainMessage(topic, timeOut, qos, payload, properties);
		}
		// 查找订阅该 topic 的客户端
		List<Subscribe> subscribeList = sessionManager.searchSubscribe(topic);
		if (subscribeList.isEmpty()) {
			logger.debug("Mqtt Topic:{} publishAll but subscribe client list is empty.", topic);
			return false;
		}
		for (Subscribe subscribe : subscribeList) {
			String clientId = subscribe.getClientId();
			ChannelContext context = Tio.getByBsId(getServerConfig(), clientId);
			if (context == null || context.isClosed()) {
				logger.warn("Mqtt Topic:{} publish to clientId:{} channel is null may be disconnected.", topic, clientId);
				continue;
			}
			// MQTT 5.0 Subscription Identifier（spec 3.3.2.3.5 / 3.8.4）：
			// 当本次订阅携带了 subscriptionId（> 0）时，需要在 PUBLISH 的 properties 中回带该 id。
			// 注意：每个 clientId 可能匹配多条订阅（多个 topic filter），仅当当前匹配的 subscribe
			// 有 subscriptionId 时才回带；多条订阅同时命中时取第一条有 id 的即可。
			MqttProperties effectiveProperties = resolveSubscriptionProperties(context, properties, subscribe.getSubscriptionId());
			publish(context, clientId, topic, payload, qos, subscribe.getMqttQoS(), false, effectiveProperties);
		}
		return true;
	}

	/**
	 * Delivers a clustered publish to local normal subscribers without storing the
	 * retained message again. Retain As Published is evaluated per subscription.
	 */
	public boolean deliverLocal(String topic, Object payload, MqttQoS qos,
							 boolean publishedRetain, MqttProperties properties) {
		Message message = new Message();
		message.setMessageType(MessageType.UP_STREAM);
		message.setTopic(topic);
		message.setPayload(payload instanceof byte[]
			? (byte[]) payload
			: mqttSerializer.serialize(payload));
		message.setQos(qos.value());
		message.setRetain(publishedRetain);
		message.setProperties(properties);
		message.setTimestamp(System.currentTimeMillis());
		return deliverLocal(message);
	}

	/**
	 * 把一条已发布的消息投递给本地订阅者。
	 * <p>
	 * 语义与本节点直接发布保持一致：
	 * <ul>
	 *     <li>遵循 No Local（规范 3.8.3.1）：订阅者即发布者时跳过；</li>
	 *     <li>MQTT 5.0 属性重写（规范 3.3.2.3）：过期丢弃、剥离 Topic Alias 与
	 *         Subscription Identifier、递减 Message Expiry Interval；</li>
	 *     <li>订阅者离线时按会话持久性入离线队列，重连后回放。</li>
	 * </ul>
	 * 集群跨节点转发走的就是这条路径：不做这三件事，
	 * 跨节点的消息会绕过 No Local、带着过期的有效期、并丢掉离线订阅者的消息。
	 *
	 * @param message 已发布的消息快照
	 * @return 是否有在线订阅者收到消息
	 */
	public boolean deliverLocal(Message message) {
		if (message == null || message.getTopic() == null) {
			return false;
		}
		String topic = message.getTopic();
		List<Subscribe> subscribeList = sessionManager.searchSubscribe(topic);
		if (subscribeList == null || subscribeList.isEmpty()) {
			return false;
		}
		MqttProperties properties = rewriteProperties(topic, message.getProperties(),
			message.getPublishReceivedAt(), message.getTimestamp());
		if (properties == null) {
			// 消息已过期，整条丢弃
			return false;
		}
		MqttQoS qos = MqttQoS.valueOf(message.getQos());
		String publisherClientId = message.getFromClientId();
		boolean delivered = false;
		for (Subscribe subscribe : subscribeList) {
			String clientId = subscribe.getClientId();
			// MQTT 5.0 No Local（规范 3.8.3.1）: 订阅者即发布者时跳过
			if (subscribe.isNoLocal() && clientId.equals(publisherClientId)) {
				logger.debug("Mqtt Topic:{} skip forwarding to clientId:{} due to No Local flag", topic, clientId);
				continue;
			}
			ChannelContext context = Tio.getByBsId(getServerConfig(), clientId);
			if (context == null || context.isClosed()) {
				// 离线订阅者不再直接丢弃：持久会话按 QoS 进入离线队列，重连后回放
				queueOfflineMessage(copyForOffline(message), clientId, subscribe.getMqttQoS());
				continue;
			}
			boolean retain = message.isRetain() && subscribe.isRetainAsPublished();
			MqttProperties effectiveProperties = resolveSubscriptionProperties(
				context, properties, subscribe.getSubscriptionId());
			delivered |= publish(context, clientId, topic, message.getPayload(), qos,
				subscribe.getMqttQoS(), retain, effectiveProperties);
		}
		return delivered;
	}

	/**
	 * 订阅者离线时按会话持久性决定入队或丢弃。
	 * <p>
	 * QoS1/2 消息始终尝试入队（由 {@link IMqttSessionManager} 判定会话是否持久、容量与 TTL）；
	 * QoS0 消息默认丢弃，可通过 {@code mqueueStoreQos0} 打开。
	 *
	 * @param message            离线消息快照
	 * @param subscriberClientId 订阅者 clientId
	 * @param subMqttQoS         订阅 QoS
	 * @return 是否入队成功
	 */
	public boolean queueOfflineMessage(Message message, String subscriberClientId, int subMqttQoS) {
		// spec 4.3.2：实际投递 QoS 取发布 QoS 与订阅 QoS 的较小值
		int qosValue = Math.min(message.getQos(), subMqttQoS);
		if (qosValue == 0 && !serverCreator.isMqueueStoreQos0()) {
			logger.debug("Mqtt Topic:{} subscriber clientId:{} offline, QoS0 message dropped.",
				message.getTopic(), subscriberClientId);
			return false;
		}
		message.setQos(qosValue);
		if (sessionManager.addOfflineMessage(subscriberClientId, message)) {
			logger.debug("Mqtt Topic:{} subscriber clientId:{} offline, message queued qos:{}.",
				message.getTopic(), subscriberClientId, qosValue);
			return true;
		}
		logger.debug("Mqtt Topic:{} subscriber clientId:{} offline and session not persistent, message dropped.",
			message.getTopic(), subscriberClientId);
		return false;
	}

	/**
	 * MQTT 5.0 属性重写（规范 3.3.2.3）。
	 * <p>
	 * 规则：
	 * <ul>
	 *     <li>检查 Message Expiry Interval，若已过期则返回 null 表示丢弃消息；</li>
	 *     <li>移除 Topic Alias（服务端转发时不携带发布者的 Topic Alias）；</li>
	 *     <li>移除 Subscription Identifier（发布者的订阅标识符不转发给订阅者）；</li>
	 *     <li>递减 Message Expiry Interval 为剩余有效时间。</li>
	 * </ul>
	 *
	 * @param topic             topic
	 * @param properties        原始 MQTT 5.0 属性
	 * @param publishReceivedAt 消息到达 broker 的时间戳，可为 {@code null}
	 * @param timestamp         消息时间戳
	 * @return 重写后的属性；null 表示消息已过期，应丢弃
	 */
	public MqttProperties rewriteProperties(String topic, MqttProperties properties,
											Long publishReceivedAt, long timestamp) {
		if (properties == null || properties.isEmpty()) {
			return properties;
		}
		long receivedAt = publishReceivedAt == null ? timestamp : publishReceivedAt;
		long elapsedSeconds = (System.currentTimeMillis() - receivedAt) / 1000;
		// 检查消息过期
		Integer expiryInterval = properties.getPropertyValue(MqttPropertyType.MESSAGE_EXPIRY_INTERVAL);
		long remaining = -1;
		if (expiryInterval != null) {
			remaining = expiryInterval - elapsedSeconds;
			if (remaining <= 0) {
				logger.debug("Mqtt Topic:{} message expired, skip forwarding", topic);
				return null;
			}
		}
		// O(1) 属性查找判断是否需要重写，避免不必要的遍历和对象分配
		boolean needsRewrite = properties.getProperty(MqttPropertyType.TOPIC_ALIAS) != null
			|| properties.getProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER) != null
			|| (expiryInterval != null && elapsedSeconds > 0);
		if (!needsRewrite) {
			return properties;
		}
		MqttProperties newProperties = new MqttProperties();
		for (MqttProperty property : properties.listAll()) {
			int propertyId = property.propertyId();
			// MQTT 5.0 规范: 服务端转发时不携带发布者的 Topic Alias 和 Subscription Identifier
			if (propertyId == MqttPropertyType.TOPIC_ALIAS.value()
				|| propertyId == MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value()) {
				continue;
			}
			if (propertyId == MqttPropertyType.MESSAGE_EXPIRY_INTERVAL.value()
				&& expiryInterval != null && elapsedSeconds > 0) {
				// remaining 已在上方计算且保证 > 0
				newProperties.add(new IntegerProperty(propertyId, (int) remaining));
			} else {
				newProperties.add(property);
			}
		}
		return newProperties;
	}

	/**
	 * 复制一份离线消息快照：不同订阅者的实际投递 QoS 可能不同，不能共用同一个对象。
	 */
	private static Message copyForOffline(Message source) {
		Message copy = new Message();
		copy.setMessageType(MessageType.UP_STREAM);
		copy.setNode(source.getNode());
		copy.setId(source.getId());
		copy.setFromClientId(source.getFromClientId());
		copy.setFromUsername(source.getFromUsername());
		copy.setTopic(source.getTopic());
		copy.setPayload(source.getPayload());
		copy.setQos(source.getQos());
		copy.setRetain(false);
		copy.setDup(false);
		copy.setPeerHost(source.getPeerHost());
		copy.setTimestamp(source.getTimestamp());
		copy.setPublishReceivedAt(source.getPublishReceivedAt());
		copy.setProperties(source.getProperties());
		return copy;
	}

	/** Delivers to one already-selected subscriber without mutating retained state. */
	public boolean deliverLocal(String clientId, String topic, Object payload, MqttQoS qos,
							 int subscriptionQos, boolean retain, MqttProperties properties) {
		ChannelContext context = Tio.getByBsId(getServerConfig(), clientId);
		if (context == null || context.isClosed()) {
			return false;
		}
		int subscriptionId = 0;
		List<Subscribe> subscriptions = sessionManager.getSubscriptions(clientId);
		if (subscriptions != null) {
			for (Subscribe subscribe : subscriptions) {
				if (subscribe.getTopicFilter() != null
					&& new TopicFilter(subscribe.getTopicFilter()).match(topic)) {
					subscriptionId = subscribe.getSubscriptionId();
					break;
				}
			}
		}
		MqttProperties effectiveProperties = resolveSubscriptionProperties(
			context, properties, subscriptionId);
		return publish(context, clientId, topic, payload, qos, subscriptionQos, retain, effectiveProperties);
	}

	/**
	 * 合并 subscriptionId 到 properties。
	 * <p>
	 * 规则：
	 * <ul>
	 *     <li>若 {@code subscriptionId <= 0}，原样返回 properties；</li>
	 *     <li>否则把 {@code Subscription Identifier} 属性附加到 properties 副本中，避免修改入参被其它 clientId 共享。</li>
	 * </ul>
	 *
	 * @param context        ChannelContext
	 * @param source         入参 properties（可能为 {@code null}）
	 * @param subscriptionId 当前匹配的订阅关联的 Subscription Identifier
	 * @return 合并后的 properties
	 */
	private MqttProperties resolveSubscriptionProperties(ChannelContext context, MqttProperties source, int subscriptionId) {
		if (subscriptionId <= 0) {
			return source;
		}
		// spec 3.3.2.3.5：subscriptionId 仅对 MQTT 5 客户端有意义
		if (!MqttCodecUtil.isMqtt5(context)) {
			return source;
		}
		MqttProperties merged = new MqttProperties();
		if (source != null && !source.isEmpty()) {
			source.listAll().forEach(merged::add);
		}
		merged.add(new IntegerProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER, subscriptionId));
		return merged;
	}

	/**
	 * 服务端主动断开 mqtt 连接，mqtt5.0
	 *
	 * @param clientId clientId
	 * @return 是否成功
	 */
	public boolean disconnect(String clientId) {
		return disconnect(clientId, MqttDisconnectReasonCode.NORMAL, null);
	}

	/**
	 * 服务端主动断开 mqtt 连接，mqtt5.0
	 *
	 * @param clientId clientId
	 * @param reasonCode 断开原因码
	 * @param properties MQTT 5.0 DISCONNECT properties
	 * @return 是否成功
	 */
	public boolean disconnect(String clientId, MqttDisconnectReasonCode reasonCode, MqttDisconnectProperties properties) {
		return disconnect(getChannelContext(clientId), clientId, reasonCode, properties);
	}

	/**
	 * 服务端主动断开 mqtt 连接
	 *
	 * @return 是否成功
	 */
	private boolean disconnect(ChannelContext channelContext, String clientId) {
		return disconnect(channelContext, clientId, MqttDisconnectReasonCode.NORMAL, null);
	}

	private boolean disconnect(ChannelContext channelContext, String clientId, MqttDisconnectReasonCode reasonCode, MqttDisconnectProperties properties) {
		if (channelContext == null || clientId == null) {
			logger.error("Mqtt server disconnect channelContext:{} or clientId:{} is null.", channelContext, clientId);
			return false;
		}
		// 仅仅 mqtt5.0 支持服务端主动断开
		if (!MqttCodecUtil.isMqtt5(channelContext)) {
			logger.error("Mqtt server disconnect clientId:{} mqtt version:{} not support.", clientId, MqttCodecUtil.getMqttVersion(channelContext));
			return false;
		}
		MqttMessage disconnectMessage = new MqttDisconnectBuilder()
			.reasonCode(reasonCode)
			.properties(properties == null ? MqttProperties.NO_PROPERTIES : properties.getProperties())
			.build();
		boolean result = Tio.bSend(channelContext, disconnectMessage);
		if (result) {
			// 设置正常断开的标识
			channelContext.setBizStatus(true);
			Tio.remove(channelContext, "Mqtt DisConnect");
		} else {
			logger.error("Mqtt DisConnect send result: false");
		}
		return result;
	}

	/**
	 * 发送消息到客户端
	 *
	 * @param topic   topic
	 * @param message Message
	 * @return 是否成功
	 */
	public boolean sendToClient(String topic, Message message) {
		// 客户端id
		String clientId = message.getClientId();
		MqttQoS mqttQoS = MqttQoS.valueOf(message.getQos());
		MqttProperties properties = resolveProperties(message);
		if (StrUtil.isBlank(clientId)) {
			return publishAll(topic, message.getPayload(), mqttQoS, message.isRetain(), properties);
		} else {
			return publish(clientId, topic, message.getPayload(), mqttQoS, message.isRetain(), properties);
		}
	}

	/**
	 * 解析消息的 MQTT 5.0 属性，优先取 {@link Message#getProperties()}，
	 * 集群传输场景下属性已被序列化为 {@link Message#getPropertiesBytes()}，需要按需反序列化。
	 *
	 * @param message Message
	 * @return MQTT 5.0 属性，可能为 {@code null}
	 */
	private static MqttProperties resolveProperties(Message message) {
		MqttProperties properties = message.getProperties();
		if (properties != null && !properties.isEmpty()) {
			return properties;
		}
		byte[] propertiesBytes = message.getPropertiesBytes();
		if (propertiesBytes != null && propertiesBytes.length > 0) {
			return MqttDecoder.decodeProperties(propertiesBytes);
		}
		return null;
	}

	/**
	 * 存储保留消息
	 *
	 * @param topic   topic
	 * @param mqttQoS MqttQoS
	 * @param payload ByteBuffer
	 */
	private void saveRetainMessage(String topic, int timeout, MqttQoS mqttQoS, Object payload) {
		saveRetainMessage(topic, timeout, mqttQoS, payload, null);
	}

	/**
	 * 存储保留消息，附带 MQTT 5.0 属性
	 *
	 * @param topic      topic
	 * @param mqttQoS    MqttQoS
	 * @param payload    ByteBuffer
	 * @param properties MQTT 5.0 属性
	 */
	private void saveRetainMessage(String topic, int timeout, MqttQoS mqttQoS, Object payload, MqttProperties properties) {
		Message retainMessage = new Message();
		retainMessage.setTopic(topic);
		retainMessage.setQos(mqttQoS.value());
		retainMessage.setPayload(payload instanceof byte[] ? (byte[]) payload : mqttSerializer.serialize(payload));
		retainMessage.setMessageType(MessageType.DOWN_STREAM);
		// 将保留消息标记成 false，避免后续下发时再次存储
		retainMessage.setRetain(false);
		retainMessage.setDup(false);
		retainMessage.setTimestamp(System.currentTimeMillis());
		retainMessage.setNode(serverCreator.getNodeName());
		if (properties != null && !properties.isEmpty()) {
			retainMessage.setProperties(properties);
		}
		this.messageStore.addRetainMessage(topic, timeout, retainMessage);
	}

	/**
	 * 获取客户端信息
	 *
	 * @param clientId clientId
	 * @return ClientInfo
	 */
	public ClientInfo getClientInfo(String clientId) {
		ChannelContext context = Tio.getByBsId(this.getServerConfig(), clientId);
		if (context == null) {
			return null;
		}
		return ClientInfo.form(serverCreator, context, ClientInfo::new);
	}

	/**
	 * 获取客户端信息
	 *
	 * @param context ChannelContext
	 * @return ClientInfo
	 */
	public ClientInfo getClientInfo(ChannelContext context) {
		return ClientInfo.form(serverCreator, context, ClientInfo::new);
	}

	/**
	 * 获取所有的客户端
	 *
	 * @return 客户端列表
	 */
	public List<ClientInfo> getClients() {
		return getClients(this.serverCreator, this.getServerConfig());
	}

	/**
	 * 分页获取所有的客户端
	 *
	 * @param serverCreator MqttServerCreator
	 * @param tioConfig     TioConfig
	 * @return 客户端列表
	 */
	public static List<ClientInfo> getClients(MqttServerCreator serverCreator, TioConfig tioConfig) {
		return Tio.getAll(tioConfig)
			.stream()
			.map(context -> ClientInfo.form(serverCreator, context, ClientInfo::new))
			.collect(Collectors.toList());
	}

	/**
	 * 分页获取所有的客户端
	 *
	 * @param pageIndex pageIndex，默认为 1
	 * @param pageSize  pageSize，默认为所有
	 * @return 分页
	 */
	public Page<ClientInfo> getClients(Integer pageIndex, Integer pageSize) {
		return getClients(this.serverCreator, this.getServerConfig(), pageIndex, pageSize);
	}

	/**
	 * 分页获取所有的客户端
	 *
	 * @param serverCreator MqttServerCreator
	 * @param tioConfig     TioConfig
	 * @param pageIndex     pageIndex，默认为 1
	 * @param pageSize      pageSize，默认为所有
	 * @return 分页
	 */
	public static Page<ClientInfo> getClients(MqttServerCreator serverCreator, TioConfig tioConfig, Integer pageIndex, Integer pageSize) {
		return PageUtils.fromSet(Tio.getAll(tioConfig), pageIndex, pageSize, context -> ClientInfo.form(serverCreator, context, ClientInfo::new));
	}

	/**
	 * 获取统计数据
	 *
	 * @return StatVo
	 */
	public StatVo getStat() {
		return serverConfig.getStat();
	}

	/**
	 * 获取客户端订阅情况
	 *
	 * @param clientId clientId
	 * @return 订阅集合
	 */
	public List<Subscribe> getSubscriptions(String clientId) {
		return serverCreator.getSessionManager().getSubscriptions(clientId);
	}

	/**
	 * 添加定时任务
	 *
	 * @param command runnable
	 * @param delay   delay
	 * @return TimerTask
	 */
	public TimerTask schedule(Runnable command, long delay) {
		return schedule(command, delay, null);
	}

	/**
	 * 添加定时任务
	 *
	 * @param command  runnable
	 * @param delay    delay
	 * @param executor 用于自定义线程池，处理耗时业务
	 * @return TimerTask
	 */
	public TimerTask schedule(Runnable command, long delay, Executor executor) {
		return this.taskService.addTask((systemTimer -> new TimerTask(delay) {
			@Override
			public void run() {
				try {
					// 1. 再次添加 任务
					systemTimer.add(this);
					// 2. 执行任务
					if (executor == null) {
						command.run();
					} else {
						executor.execute(command);
					}
				} catch (Exception e) {
					logger.error("Mqtt server schedule error", e);
				}
			}
		}));
	}

	/**
	 * 添加定时任务，注意：如果抛出异常，会终止后续任务，请自行处理异常
	 *
	 * @param command runnable
	 * @param delay   delay
	 * @return TimerTask
	 */
	public TimerTask scheduleOnce(Runnable command, long delay) {
		return scheduleOnce(command, delay, null);
	}

	/**
	 * 添加定时任务，注意：如果抛出异常，会终止后续任务，请自行处理异常
	 *
	 * @param command  runnable
	 * @param delay    delay
	 * @param executor 用于自定义线程池，处理耗时业务
	 * @return TimerTask
	 */
	public TimerTask scheduleOnce(Runnable command, long delay, Executor executor) {
		return this.taskService.addTask((systemTimer -> new TimerTask(delay) {
			@Override
			public void run() {
				try {
					if (executor == null) {
						command.run();
					} else {
						executor.execute(command);
					}
				} catch (Exception e) {
					logger.error("Mqtt server schedule once error", e);
				}
			}
		}));
	}

	/**
	 * 获取 ChannelContext
	 *
	 * @param clientId clientId
	 * @return ChannelContext
	 */
	public ChannelContext getChannelContext(String clientId) {
		return Tio.getByBsId(getServerConfig(), clientId);
	}

	/**
	 * 服务端主动断开连接
	 *
	 * @param clientId clientId
	 */
	public void close(String clientId) {
		Tio.remove(getChannelContext(clientId), "Mqtt server close this connects.");
	}

	/**
	 * 启动服务
	 *
	 * @return 是否启动
	 */
	public boolean start() {
		// 1. 启动 task
		this.taskService.start();
		// 2. 启动心跳检测
		this.taskService.addTask(systemTimer -> new ServerHeartbeatTask(systemTimer, serverConfig));
		// 3. 启动监听器
		listeners.start();
		return true;
	}

	/**
	 * 停止服务
	 *
	 * @return 是否停止
	 */
	public boolean stop() {
		// 先执行上层扩展的关闭钩子（如规则引擎的 action 资源），确保它们在
		// 监听器与业务线程池关闭之前释放
		for (Runnable hook : serverCreator.getShutdownHooks()) {
			try {
				hook.run();
			} catch (Throwable e) {
				logger.error("MqttServer shutdown hook error.", e);
			}
		}
		// 停止服务
		boolean result = listeners.stop();
		// 优雅停止 mqtt 工作线程
		ExecutorService mqttExecutor = serverCreator.getMqttExecutor();
		result &= ThreadUtils.shutdownExecutor(mqttExecutor, serverCreator.getShutdownTimeoutSec(), "mqttExecutor");
		try {
			sessionManager.clean();
		} catch (Throwable e) {
			logger.error("MqttServer stop session clean error.", e);
		}
		try {
			messageStore.clean();
		} catch (Throwable e) {
			logger.error("MqttServer stop message store clean error.", e);
		}
		return result;
	}

}
