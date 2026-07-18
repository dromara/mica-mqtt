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

import org.dromara.mica.mqtt.codec.message.builder.MqttPublishBuilder;
import org.dromara.mica.mqtt.codec.properties.IntegerProperty;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.codec.properties.MqttPropertyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * MQTT 5.0 客户端 Topic Alias 自动维护（PR10 / spec 3.3.2.3.4）。
 *
 * <p>维护 {@code topic <-> alias} 双向映射。在 publish 前调用 {@link #apply} 自动：
 * <ol>
 *     <li>如果 topic 已有 alias，置 topic 为空字符串并把 alias 写入 properties（节省带宽）；</li>
 *     <li>如果 topic 尚无 alias，分配一个新 alias（在 [1, max] 范围），保留 topic 不变；</li>
 *     <li>如果业务方已在 properties 中显式设置 TopicAlias，优先尊重之并同步映射。</li>
 * </ol>
 *
 * <p>典型调用：
 * <pre>{@code
 *   MqttPublishBuilder builder = MqttPublishMessage.builder()
 *       .topicName("sensors/temperature/living-room/very-long-path")
 *       .payload(data)
 *       .qos(MqttQoS.QOS1);
 *   topicAliasManager.apply(builder, mqtt5Properties);
 *   client.publish(builder);
 * }</pre>
 *
 * <p>spec 3.3.2.3.4：客户端 Topic Alias 上限由服务端 CONNACK 中的 {@code Topic Alias Maximum} 决定；
 * 缺省 0 表示禁用。每次新连接建立后，本实现会先禁用 Topic Alias，再按 CONNACK 宣告值更新上限。
 *
 * @author L.cm
 */
public class MqttTopicAliasManager {
	private static final Logger logger = LoggerFactory.getLogger(MqttTopicAliasManager.class);
	/**
	 * MQTT 规范默认 Topic Alias 上限；服务端未在 CONNACK 中宣告时必须禁用。
	 */
	public static final int DEFAULT_MAX_TOPIC_ALIAS = 0;
	/**
	 * alias 槽位临时占位符（用于原子分配）。
	 */
	private static final String RESERVED = "\u0000__reserved__";

	private final ConcurrentMap<String, Integer> topicToAlias = new ConcurrentHashMap<>();
	private final ConcurrentMap<Integer, String> aliasToTopic = new ConcurrentHashMap<>();
	/**
	 * spec 3.3.2.3.4：Topic Alias 合法值 1 ~ 0xFFFF。0 用作"未使用 alias"标记。
	 */
	private static final int MIN_ALIAS = 1;
	private volatile int maxAlias;

	public MqttTopicAliasManager() {
		this(DEFAULT_MAX_TOPIC_ALIAS);
	}

	/**
	 * @param maxAlias 客户端允许的 Topic Alias 上限（来自服务端 CONNACK 的 Topic Alias Maximum）。
	 */
	public MqttTopicAliasManager(int maxAlias) {
		if (maxAlias < 0 || maxAlias > 0xFFFF) {
			throw new IllegalArgumentException("maxAlias must be in [0, 65535], got " + maxAlias);
		}
		this.maxAlias = maxAlias;
	}

	/**
	 * 把 topic 替换为 alias（如果已注册）。
	 * <p>
	 * 调用方应在 {@code MqttPublishBuilder.build()} 之前调用本方法。
	 * <p>
	 * 三种情况：
	 * <ol>
	 *     <li>业务方已在 properties 中显式设置 {@code TopicAlias} → 同步 topic -> alias 映射；</li>
	 *     <li>topic 已在映射中（已注册过 alias）→ 把 topic 置空串，properties 加 {@code TopicAlias}；</li>
	 *     <li>topic 未在映射中 → 分配新 alias（如果未达上限），保留 topic 不变；</li>
	 * </ol>
	 *
	 * @param builder   PublishBuilder
	 * @param properties PUBLISH properties
	 * @return 是否使用了 alias（true 表示已替换 topic 为空串）
	 */
	public boolean apply(MqttPublishBuilder builder, MqttProperties properties) {
		String topic = builder.getTopicName();
		if (topic == null || topic.isEmpty()) {
			return false;
		}
		// spec 3.3.2.3.4: Topic Alias = 0 表示"未使用 alias"
		Integer existingAlias = properties.getPropertyValue(MqttPropertyType.TOPIC_ALIAS);
		if (existingAlias != null) {
			// 业务方已显式设置 alias（无论 0 或 > 0）→ 同步映射或保留 topic
			if (existingAlias > 0) {
				registerAlias(topic, existingAlias);
			}
			return false;
		}
		// 查询 topic 已有 alias？
		Integer existing = topicToAlias.get(topic);
		if (existing != null) {
			// 已注册 → 替换 topic 为空串，properties 加 TopicAlias
			builder.topicName("");
			ensureProperty(properties, MqttPropertyType.TOPIC_ALIAS, existing);
			if (logger.isDebugEnabled()) {
				logger.debug("Topic alias applied - topic:'{}' alias:{}", topic, existing);
			}
			return true;
		}
		// 未注册 → 尝试分配新 alias（atomic：putIfAbsent 占用 alias 槽位后再 registerAlias）
		int newAlias = allocateAndReserve();
		if (newAlias <= 0) {
			// 已达上限，保留 topic
			if (logger.isDebugEnabled()) {
				logger.debug("Topic alias not allocated (max reached) - topic:{} max:{}", topic, maxAlias);
			}
			return false;
		}
		registerAlias(topic, newAlias);
		// The first PUBLISH must carry both the topic name and Topic Alias so the
		// server can establish the mapping before an empty-topic PUBLISH uses it.
		ensureProperty(properties, MqttPropertyType.TOPIC_ALIAS, newAlias);
		// 防御：清理可能存在的"同 topic 旧 alias"残留（并发场景下另一个线程可能先用其他 alias 注册过该 topic）
		cleanStaleAliasesForTopic(topic, newAlias);
		if (logger.isDebugEnabled()) {
			logger.debug("Topic alias registered - topic:'{}' alias:{}", topic, newAlias);
		}
		// 第一次注册保留 topic 不变，让服务端记录映射
		return false;
	}

	/**
	 * 清理 topic 的"非主 alias"残留（并发 race 保护）。
	 * <p>场景：两个线程并发 apply 同一 topic，第二个 thread 后 registerAlias 时
	 * aliasToTopic 可能还有"旧 alias → topic"残留，应清理。
	 */
	private void cleanStaleAliasesForTopic(String topic, int currentAlias) {
		// 简单遍历 + 条件删除；性能可接受（一般 alias 数量 < 100）
		aliasToTopic.entrySet().removeIf(entry -> {
			int alias = entry.getKey();
			String t = entry.getValue();
			return alias != currentAlias && topic.equals(t);
		});
	}

	/**
	 * 原子地分配并占用一个 alias 槽位。
	 * <p>使用 {@link ConcurrentMap#putIfAbsent} 原子地"先占位"再"registerAlias"，
	 * 避免 {@code allocateAlias} 与 {@code registerAlias} 之间的 TOCTOU 竞态。
	 *
	 * @return 分配的 alias；返回 0 表示已达上限
	 */
	private int allocateAndReserve() {
		if (topicToAlias.size() >= maxAlias) {
			return 0;
		}
		for (int candidate = MIN_ALIAS; candidate <= maxAlias; candidate++) {
			// 临时占位：aliasToTopic.putIfAbsent 返回 null 表示该 slot 之前无人占用，可由当前线程占用
			String existing = aliasToTopic.putIfAbsent(candidate, RESERVED);
			if (existing == null) {
				// 成功占位 → 由后续 registerAlias 用真实 topic 覆盖 RESERVED
				return candidate;
			}
		}
		return 0;
	}

	/**
	 * 注册 topic -> alias 映射（业务方显式调用或内部使用）。
	 */
	public void registerAlias(String topic, int alias) {
		if (topic == null || topic.isEmpty() || alias < 1) {
			return;
		}
		// 防御：如果 alias 已被别的 topic 使用，清理旧映射
		String oldTopic = aliasToTopic.put(alias, topic);
		if (oldTopic != null && !oldTopic.equals(topic) && !RESERVED.equals(oldTopic)) {
			topicToAlias.remove(oldTopic);
		}
		topicToAlias.put(topic, alias);
	}

	/**
	 * 取消注册：删除 topic 的 alias 映射。
	 */
	public void unregister(String topic) {
		Integer alias = topicToAlias.remove(topic);
		if (alias != null) {
			aliasToTopic.remove(alias, topic);
		}
	}

	/**
	 * 清除所有映射。
	 */
	public void clear() {
		topicToAlias.clear();
		aliasToTopic.clear();
	}

	/**
	 * 更新当前连接允许使用的 Topic Alias 上限并清空旧连接的映射。
	 *
	 * @param maxAlias 服务端在 CONNACK 中宣告的 Topic Alias Maximum；0 表示禁用
	 */
	public void reset(int maxAlias) {
		if (maxAlias < 0 || maxAlias > 0xFFFF) {
			throw new IllegalArgumentException("maxAlias must be in [0, 65535], got " + maxAlias);
		}
		// 先禁用，避免并发 publish 在清理两张映射表期间复用旧连接状态。
		this.maxAlias = 0;
		clear();
		this.maxAlias = maxAlias;
	}

	/**
	 * 获取 topic 的 alias；未注册返回 null。
	 */
	public Integer getAlias(String topic) {
		return topicToAlias.get(topic);
	}

	/**
	 * 获取 alias 对应的 topic；未注册返回 null。
	 */
	public String getTopic(int alias) {
		return aliasToTopic.get(alias);
	}

	/**
	 * 已注册的 topic 数量。
	 */
	public int size() {
		return topicToAlias.size();
	}

	/**
	 * 分配新 alias（在 [1, maxAlias] 范围内），返回 0 表示已达上限。
	 * <p>注意：mica-mqtt 的 {@link #apply} 内部使用 {@link #allocateAndReserve} 保证原子性；
	 * 本 protected 方法保留作为可重写 hook，业务方可自定义分配策略。
	 * <p>重写本方法时，业务方需自行保证并发安全（建议使用 {@link ConcurrentMap#putIfAbsent}）。
	 */
	protected int allocateAlias() {
		if (topicToAlias.size() >= maxAlias) {
			return 0;
		}
		for (int candidate = MIN_ALIAS; candidate <= maxAlias; candidate++) {
			if (!aliasToTopic.containsKey(candidate)) {
				return candidate;
			}
		}
		return 0;
	}

	/**
	 * 客户端允许的 Topic Alias 上限（来自服务端 CONNACK 的 Topic Alias Maximum）。
	 */
	public int getMaxAlias() {
		return maxAlias;
	}

	private static void ensureProperty(MqttProperties properties, MqttPropertyType type, int value) {
		// 防御性：如果已有同 propertyId 的值，保留最新的
		properties.add(new IntegerProperty(type, value));
	}
}
