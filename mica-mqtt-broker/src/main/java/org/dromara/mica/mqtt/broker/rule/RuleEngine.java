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
import org.dromara.mica.mqtt.broker.rule.action.RuleActionExecutor;
import org.dromara.mica.mqtt.broker.rule.codec.PayloadCodec;
import org.dromara.mica.mqtt.broker.rule.codec.PayloadCodecRegistry;
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
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 规则引擎入口：监听 RuleManager 变更，把每条 rule 挂到 MqttFunctionManager。
 *
 * @author L.cm
 */
public class RuleEngine {

	private static final Logger logger = LoggerFactory.getLogger(RuleEngine.class);

	/**
	 * 解码后的 payload 在 {@code RuleContext.attributes} 中的键名。
	 */
	public static final String PAYLOAD_ATTR = RuleContext.PAYLOAD_ATTR;

	private final RuleManager ruleManager;
	private final MqttFunctionManager functionManager;
	private final RuleMetrics metrics;
	private final ActionRegistry actionRegistry;
	private final MatcherRegistry matcherRegistry;
	private final PayloadCodecRegistry codecRegistry;
	private final RuleActionExecutor actionExecutor;
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
		this(ruleManager, functionManager, metrics, new RuleActionExecutor());
	}

	/**
	 * 构造。
	 *
	 * @param ruleManager     规则管理器
	 * @param functionManager 函数监听管理器（broker 内共享）
	 * @param metrics         指标（可空，使用默认）
	 * @param actionExecutor  action 执行线程池；由本引擎负责关闭
	 */
	public RuleEngine(RuleManager ruleManager,
					  MqttFunctionManager functionManager,
					  RuleMetrics metrics,
					  RuleActionExecutor actionExecutor) {
		this.ruleManager = ruleManager;
		this.functionManager = functionManager;
		this.metrics = metrics == null ? new RuleMetricsRecorder() : metrics;
		this.actionRegistry = ruleManager.getActionRegistry();
		this.matcherRegistry = ruleManager.getMatcherRegistry();
		this.codecRegistry = ruleManager.getCodecRegistry();
		this.actionExecutor = actionExecutor == null ? new RuleActionExecutor() : actionExecutor;
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
		// 最后关闭 action 线程池：此时已无新任务提交，等待在途任务收尾
		actionExecutor.close();
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
	 * @return action 执行线程池
	 */
	public RuleActionExecutor getActionExecutor() {
		return actionExecutor;
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
	 * 单条规则的 function 监听器。
	 * <p>
	 * IO 线程只做「构造上下文 + 提交任务」，解码、匹配、actions 全部在
	 * action 线程池内串行执行：既保证 actions 的声明顺序与 {@code stopOnError}
	 * 语义，又不会让慢后端（HTTP / MQTT）阻塞 t-io 的 IO 线程。
	 * </p>
	 */
	private final class RuleFunctionListener implements IMqttFunctionMessageListener {
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
			// 当前正在执行的 action 名：供超时看门狗记录失败指标
			AtomicReference<String> currentAction = new AtomicReference<>(null);
			// 取消标志：看门狗超时后置位，链内据此跳过剩余 action
			AtomicBoolean cancelled = new AtomicBoolean(false);
			Future<?> future = actionExecutor.submit(() -> executeRule(ctx, currentAction, cancelled));
			scheduleTimeout(ctx, currentAction, cancelled, future);
		}

		/**
		 * 执行一条规则：解码 → 匹配 → 顺序执行 actions。
		 * <p>
		 * 该方法运行在 action 线程池线程上。
		 * </p>
		 */
		private void executeRule(RuleContext ctx, AtomicReference<String> currentAction,
								 AtomicBoolean cancelled) {
			try {
				decodePayload(ctx);
				if (matcher != null && !matcher.matches(ctx)) {
					return;
				}
				runActions(ctx, currentAction, cancelled);
			} catch (Exception e) {
				logger.error("rule {} execution failed", rule.getId(), e);
			}
		}

		/**
		 * 按声明顺序执行 actions。
		 */
		private void runActions(RuleContext ctx, AtomicReference<String> currentAction,
								AtomicBoolean cancelled) {
			for (Action action : actions) {
				// 超时后剩余 action 不再执行（中断标志可能被 action 吞掉，故同时看取消标志）
				if (cancelled.get() || Thread.currentThread().isInterrupted()) {
					logger.warn("rule {} cancelled, remaining actions skipped", rule.getId());
					return;
				}
				currentAction.set(action.getName());
				long start = System.nanoTime();
				try {
					action.send(ctx);
					metrics.recordSuccess(rule.getId(), action.getName(), costMs(start));
				} catch (InterruptedException e) {
					// 规则被超时中断：失败已由看门狗记录
					logger.warn("rule {} action {} interrupted", rule.getId(), action.getName());
					return;
				} catch (Exception e) {
					metrics.recordFailure(rule.getId(), action.getName(), costMs(start));
					logger.error("rule {} action {} failed", rule.getId(), action.getName(), e);
					if (rule.isStopOnError()) {
						return;
					}
				}
			}
		}

		/**
		 * 按规则声明的 codec 解码 payload，结果缓存在上下文中供 matcher / action 复用。
		 * <p>
		 * 每条消息每条规则只解码一次；解码失败时保留原始字节，不影响后续 action。
		 * </p>
		 */
		private void decodePayload(RuleContext ctx) {
			String codecType = rule.getCodecType();
			if (codecType == null || codecType.isEmpty()) {
				return;
			}
			PayloadCodec codec = codecRegistry.get(codecType);
			if (codec == null) {
				logger.warn("rule {} references unknown codec: {}", rule.getId(), codecType);
				return;
			}
			byte[] payload = ctx.getPayload();
			if (payload == null || payload.length == 0) {
				return;
			}
			try {
				Object decoded = codec.decode(payload);
				if (decoded != null) {
					ctx.attr(PAYLOAD_ATTR, decoded);
				}
			} catch (Exception e) {
				logger.warn("rule {} codec {} decode failed", rule.getId(), codecType, e);
			}
		}
	}

	/**
	 * 规则超时看门狗：超时未完成则中断规则任务并记录一次失败。
	 *
	 * @param ctx           规则上下文
	 * @param currentAction 当前执行中的 action 名
	 * @param cancelled     取消标志，超时后置位
	 * @param future        规则任务
	 */
	private void scheduleTimeout(RuleContext ctx, AtomicReference<String> currentAction,
								 AtomicBoolean cancelled, Future<?> future) {
		long timeoutMs = ctx.getRule().getTimeoutMs();
		if (timeoutMs <= 0) {
			return;
		}
		String ruleId = ctx.getRule().getId();
		actionExecutor.schedule(() -> {
			if (future.isDone()) {
				return;
			}
			cancelled.set(Boolean.TRUE);
			future.cancel(true);
			String actionName = currentAction.get();
			metrics.recordFailure(ruleId, actionName == null ? "-" : actionName, timeoutMs);
			logger.error("rule {} timeout after {}ms, action {} cancelled",
				ruleId, timeoutMs, actionName == null ? "-" : actionName);
		}, timeoutMs);
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
