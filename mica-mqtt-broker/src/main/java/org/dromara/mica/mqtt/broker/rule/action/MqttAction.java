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

import net.dreamlu.mica.net.utils.hutool.StrUtil;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
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

	private static String renderTopic(String template, RuleContext ctx) {
		if (template == null || template.isEmpty()) {
			return ctx.getTopic();
		}
		String result = StrUtil.replace(template, "{clientId}", safe(ctx.getClientId()));
		result = StrUtil.replace(result, "{topic}", safe(ctx.getTopic()));
		return result;
	}

	private static String safe(String s) {
		return s == null ? "" : s;
	}

	/**
	 * 用于 {@link MqttActionFactory} 物化客户端连接。
	 */
	public static MqttClient buildClient(ActionRef ref) {
		String host = ref.getString("host");
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
