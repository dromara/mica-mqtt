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
import net.dreamlu.mica.net.core.Node;
import org.dromara.mica.mqtt.broker.rule.action.Action;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;
import org.dromara.mica.mqtt.broker.rule.action.ActionRegistry;
import org.dromara.mica.mqtt.broker.rule.matcher.MatcherRegistry;
import org.dromara.mica.mqtt.broker.rule.matcher.RuleMatcher;
import org.dromara.mica.mqtt.broker.rule.metrics.RuleMetrics;
import org.dromara.mica.mqtt.broker.rule.metrics.RuleMetricsRecorder;
import org.dromara.mica.mqtt.broker.rule.store.RuleEvent;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.server.func.IMqttFunctionMessageListener;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private final ActionRegistry actionRegistry;
	private final MatcherRegistry matcherRegistry;
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
		this.actionRegistry = ruleManager.getActionRegistry();
		this.matcherRegistry = ruleManager.getMatcherRegistry();
	}

	/**
	 * 启动：执行 RuleManager.start()，事件回调会自动把 rule 挂到 functionManager。
	 */
	public void start() {
		ruleManager.start();
	}

	/**
	 * 停止：摘掉所有挂载并释放 action 资源（MqttClient / HTTP 连接等）。
	 * <p>
	 * 由 broker 的关闭流程通过 {@code MqttServerCreator#addShutdownHook} 调用。
	 * </p>
	 */
	public void stop() {
		for (RuleFunctionListener fn : listenerMap.values()) {
			functionManager.unregister(fn.getRule().getTopicFilter(), fn);
		}
		listenerMap.clear();
		// RuleManager.stop() 内部会 actionRegistry.clear()，释放全部 action
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
				// 先摘掉旧的挂载，再挂新的；旧的 action 在下面按引用并集统一回收
				RuleFunctionListener prev = listenerMap.remove(rule.getId());
				if (prev != null) {
					functionManager.unregister(prev.getRule().getTopicFilter(), prev);
				}
				// 预物化 matcher 与 action：热路径不再做 registry 查找与工厂调用，
				// 配置错误（缺 url/topic 等）也会在挂载期暴露而不是首条消息
				RuleMatcher matcher = matcherRegistry.get(rule.getMatcherType(), rule.getMatcherProps());
				List<Action> actions = materializeActions(rule);
				RuleFunctionListener fn = new RuleFunctionListener(rule, matcher, actions, metrics);
				listenerMap.put(rule.getId(), fn);
				functionManager.register(rule.getTopicFilter(), fn);
				releaseUnusedActions();
				logger.debug("rule {} attached to {}", rule.getId(), rule.getTopicFilter());
				break;
			}
			case REMOVED: {
				RuleFunctionListener fn = listenerMap.remove(evt.getRuleId());
				if (fn != null) {
					functionManager.unregister(fn.getRule().getTopicFilter(), fn);
				}
				// 删除规则后回收其 action（MqttAction 持有 MqttClient，不回收会常驻）
				releaseUnusedActions();
				logger.debug("rule {} detached", evt.getRuleId());
				break;
			}
			default:
				break;
		}
	}

	private List<Action> materializeActions(Rule rule) {
		List<ActionRef> refs = rule.getActions();
		if (refs.isEmpty()) {
			return Collections.emptyList();
		}
		List<Action> actions = new ArrayList<>(refs.size());
		for (ActionRef ref : refs) {
			actions.add(actionRegistry.materialize(ref));
		}
		return actions;
	}

	/**
	 * 回收不再被任何存活规则引用的 action。
	 */
	private void releaseUnusedActions() {
		Set<ActionRef> retained = new LinkedHashSet<>();
		for (RuleFunctionListener fn : listenerMap.values()) {
			retained.addAll(fn.getRule().getActions());
		}
		actionRegistry.retainAll(retained);
	}

	/**
	 * 单条规则的 function 监听器，在 broker IO 线程上同步执行 actions。
	 */
	private static final class RuleFunctionListener implements IMqttFunctionMessageListener {
		private final Rule rule;
		private final RuleMatcher matcher;
		private final List<Action> actions;
		private final RuleMetrics metrics;

		RuleFunctionListener(Rule rule, RuleMatcher matcher, List<Action> actions, RuleMetrics metrics) {
			this.rule = rule;
			this.matcher = matcher;
			this.actions = actions;
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
			Node clientNode = context == null ? null : context.getClientNode();
			Node serverNode = context == null ? null : context.getServerNode();
			RuleChannelInfo channelInfo = new RuleChannelInfo(
				clientNode == null ? null : clientNode.getIp(),
				clientNode == null ? 0 : clientNode.getPort(),
				serverNode == null ? null : serverNode.toString()
			);
			RuleContext ctx = new RuleContext(
				context, channelInfo, clientId, topic, qos,
				message.getPayload(), message.fixedHeader().isRetain(), headers, rule
			);
			if (matcher != null && !matcher.matches(ctx)) {
				return;
			}
			boolean stopped = false;
			for (Action action : actions) {
				if (stopped) {
					break;
				}
				long start = System.nanoTime();
				try {
					action.send(ctx);
					metrics.recordSuccess(rule.getId(), action.getName(), costMs(start));
				} catch (Exception e) {
					metrics.recordFailure(rule.getId(), action.getName(), costMs(start));
					logger.error("rule {} action {} failed", rule.getId(), action.getName(), e);
					if (rule.isStopOnError()) {
						stopped = true;
					}
				}
			}
		}
	}

	/**
	 * 提取 MQTT 5.0 User Property 作为 headers。
	 * <p>
	 * {@code MqttPublishMessage.getProperties()} 内部使用
	 * {@code MqttProperties.withEmptyDefaults(...)}，对 MQTT 3.x 报文返回空属性集合，
	 * 因此无需 try/catch 兜底。
	 * </p>
	 */
	private static Map<String, String> extractHeaders(MqttPublishMessage message) {
		Map<String, String> headers = message.getProperties().getUserPropertiesMap();
		return headers.isEmpty() ? Collections.emptyMap() : headers;
	}

	private static long costMs(long startNanos) {
		return Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
	}
}
