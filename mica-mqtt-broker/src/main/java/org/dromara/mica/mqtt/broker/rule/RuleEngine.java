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

package org.dromara.mica.mqtt.broker.rule;

import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.broker.rule.matcher.RuleMatcher;
import org.dromara.mica.mqtt.broker.rule.metrics.RuleMetrics;
import org.dromara.mica.mqtt.broker.rule.metrics.RuleMetricsRecorder;
import org.dromara.mica.mqtt.broker.rule.sink.Sink;
import org.dromara.mica.mqtt.broker.rule.sink.SinkRef;
import org.dromara.mica.mqtt.broker.rule.store.RuleEvent;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.server.func.IMqttFunctionMessageListener;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 规则引擎入口：监听 RuleManager 变更，把每条 rule 挂到 MqttFunctionManager。
 *
 * @author L.cm
 */
public class RuleEngine {

	private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);

	private final RuleManager ruleManager;
	private final MqttFunctionManager functionManager;
	private final RuleMetrics metrics;
	private final ConcurrentMap<String, RuleFunctionListener> listenerMap = new ConcurrentHashMap<>();

	/**
	 * 构造。
	 *
	 * @param ruleManager     规则管理器
	 * @param functionManager 函数监听管理器（broker 内共享）
	 * @param metrics         指标（可空，使用默认）
	 */
	public RuleEngine(RuleManager ruleManager,
					  MqttFunctionManager functionManager,
					  RuleMetrics metrics) {
		this.ruleManager = ruleManager;
		this.functionManager = functionManager;
		this.metrics = metrics == null ? new RuleMetricsRecorder() : metrics;
	}

	/**
	 * 启动：执行 RuleManager.start()，事件回调会自动把 rule 挂到 functionManager。
	 */
	public void start() {
		ruleManager.start();
	}

	/**
	 * 停止：摘掉所有挂载。
	 */
	public void stop() {
		for (RuleFunctionListener fn : listenerMap.values()) {
			Rule r = fn.getRule();
			functionManager.unregister(r.getTopicFilter(), fn);
		}
		listenerMap.clear();
		ruleManager.stop();
	}

	public RuleMetrics getMetrics() {
		return metrics;
	}

	public MqttFunctionManager getFunctionManager() {
		return functionManager;
	}

	public RuleManager getRuleManager() {
		return ruleManager;
	}

	/**
	 * 由装配流程调用，注册到 RuleManager。
	 */
	public void attach() {
		ruleManager.addListener(this::onRuleEvent);
	}

	private void onRuleEvent(RuleEvent evt) {
		switch (evt.getType()) {
			case ADDED:
			case UPDATED: {
				Rule rule = evt.getRule();
				String oldId = evt.getOldRuleId();
				if (oldId != null && !oldId.equals(rule.getId())) {
					RuleFunctionListener old = listenerMap.remove(oldId);
					if (old != null) {
						functionManager.unregister(old.getRule().getTopicFilter(), old);
					}
				}
				RuleFunctionListener prev = listenerMap.get(rule.getId());
				if (prev != null) {
					functionManager.unregister(prev.getRule().getTopicFilter(), prev);
				}
				RuleFunctionListener fn = new RuleFunctionListener(rule, ruleManager, metrics);
				listenerMap.put(rule.getId(), fn);
				functionManager.register(rule.getTopicFilter(), fn);
				if (logger.isDebugEnabled()) {
					logger.debug("rule {} attached to {}", rule.getId(), rule.getTopicFilter());
				}
				break;
			}
			case REMOVED: {
				RuleFunctionListener fn = listenerMap.remove(evt.getRuleId());
				if (fn != null) {
					functionManager.unregister(fn.getRule().getTopicFilter(), fn);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("rule {} detached", evt.getRuleId());
				}
				break;
			}
			default:
				break;
		}
	}

	/**
	 * 单条规则的 function 监听器，在 broker IO 线程上同步执行 sinks。
	 */
	private static final class RuleFunctionListener implements IMqttFunctionMessageListener {
		private final Rule rule;
		private final RuleManager ruleManager;
		private final RuleMetrics metrics;

		RuleFunctionListener(Rule rule, RuleManager ruleManager, RuleMetrics metrics) {
			this.rule = rule;
			this.ruleManager = ruleManager;
			this.metrics = metrics;
		}

		Rule getRule() {
			return rule;
		}

		@Override
		public void onMessage(ChannelContext context, String clientId, String topic,
							  MqttQoS qos, MqttPublishMessage message) {
			if (!rule.isEnabled()) {
				return;
			}
			Map<String, String> headers = extractHeaders(message);
			RuleChannelInfo channelInfo = new RuleChannelInfo(
				context != null && context.getClientNode() != null ? context.getClientNode().getIp() : null,
				context != null && context.getClientNode() != null ? context.getClientNode().getPort() : 0,
				context != null && context.getServerNode() != null ? context.getServerNode().toString() : null
			);
			RuleContext ctx = new RuleContext(
				context, channelInfo, clientId, topic, qos,
				message.getPayload(), message.fixedHeader().isRetain(), headers, rule
			);
			RuleMatcher matcher = ruleManager.getMatcherRegistry()
				.get(rule.getMatcherType(), rule.getMatcherProps());
			if (matcher != null && !matcher.matches(ctx)) {
				return;
			}
			boolean stopped = false;
			for (SinkRef ref : rule.getSinks()) {
				if (stopped) {
					break;
				}
				Sink sink = ruleManager.getSinkRegistry().materialize(ref);
				long start = System.nanoTime();
				try {
					sink.send(ctx);
					metrics.recordSuccess(rule.getId(), sink.getName(), costMs(start));
				} catch (Exception e) {
					metrics.recordFailure(rule.getId(), sink.getName(), costMs(start));
					logger.error("rule {} sink {} failed", rule.getId(), sink.getName(), e);
					if (rule.isStopOnError()) {
						stopped = true;
					}
				}
			}
		}
	}

	private static Map<String, String> extractHeaders(MqttPublishMessage message) {
		Map<String, String> headers = new HashMap<>();
		try {
			if (message.getProperties() != null) {
				message.getProperties().getUserProperties()
					.forEach((up) -> headers.put(up.value().key, up.value().value));
			}
		} catch (Exception ignore) {
			// 旧协议没有 properties，忽略
		}
		return headers;
	}

	private static long costMs(long startNanos) {
		return Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
	}
}
