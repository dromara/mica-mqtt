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

package org.dromara.mica.mqtt.broker.rule.action;

import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.ext.template.TemplateRenderer;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.client.MqttClient;
import org.dromara.mica.mqtt.core.client.MqttClientCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 内置 MqttAction：复用 mica-mqtt-client 转发到另一台 MQTT broker。
 *
 * @author L.cm
 */
public class MqttAction implements Action, AutoCloseable {
	private static final Logger logger = LoggerFactory.getLogger(MqttAction.class);

	private final String name;
	private final MqttClient client;
	private final String topicTemplate;
	private final MqttQoS qos;
	private final boolean retain;
	private final AtomicBoolean closed = new AtomicBoolean(false);

	public MqttAction(String name, MqttClient client,
					String topicTemplate, MqttQoS qos, boolean retain) {
		this.name = name == null || name.isEmpty() ? "mqtt" : name;
		this.client = client;
		this.topicTemplate = topicTemplate;
		this.qos = qos == null ? MqttQoS.QOS0 : qos;
		this.retain = retain;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void send(RuleContext ctx) {
		if (closed.get()) {
			throw new IllegalStateException("MqttAction is closed: " + name);
		}
		String targetTopic = renderTopic(topicTemplate, ctx);
		if (targetTopic == null || targetTopic.isEmpty()) {
			targetTopic = ctx.getTopic();
		}
		boolean ok = client.publish(targetTopic, ctx.getPayload(), qos, ctx.isRetain() && retain);
		if (!ok) {
			throw new IllegalStateException(
				"MqttAction publish failed: " + name + " topic=" + targetTopic);
		}
	}

	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			try {
				client.stop();
			} catch (Exception e) {
				logger.warn("Failed to stop mqtt client for action {}", name, e);
			}
		}
	}

	/**
	 * 渲染目标 topic 模板。
	 * <p>
	 * 复用 {@link TemplateRenderer}，使内置 mqtt action 与扩展模板 action 支持同一组占位符
	 * （{@code {topic}}、{@code {clientId}}、{@code {rule.name}}、{@code {topicSegments[i]}}）。
	 * </p>
	 *
	 * @param template topic 模板；为空表示沿用原始 topic
	 * @param ctx      当前规则上下文
	 * @return 渲染后的 topic
	 */
	private static String renderTopic(String template, RuleContext ctx) {
		if (template == null || template.isEmpty()) {
			return ctx.getTopic();
		}
		return TemplateRenderer.render(template, ctx);
	}

	/**
	 * 用于 {@link MqttActionFactory} 物化客户端连接。
	 * <p>
	 * 必填属性在创建期校验，避免把错误推迟到首条消息触发时才暴露。
	 * </p>
	 *
	 * @param ref action 配置
	 * @return 已连接的 mqtt 客户端
	 */
	public static MqttClient buildClient(ActionRef ref) {
		String host = ref.getString("host");
		if (host == null || host.trim().isEmpty()) {
			throw new IllegalArgumentException(
				"mqtt action requires a non-empty 'host' property: " + ref.getName());
		}
		int port = ref.getInt("port", 1883);
		String clientId = ref.getString("clientId");
		String username = ref.getString("username");
		String password = ref.getString("password");
		boolean ssl = ref.getBoolean("ssl", false);
		MqttClientCreator creator = MqttClient.create()
			.ip(host)
			.port(port)
			.clientId(clientId == null ? "rule-forwarder-" + System.nanoTime() : clientId)
			.username(username)
			.password(password)
			.reconnect(true);
		if (ssl) {
			creator.useSsl();
		}
		return creator.connectSync();
	}
}
