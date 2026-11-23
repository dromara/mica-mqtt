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
import org.dromara.mica.mqtt.codec.MqttQoS;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则执行上下文。
 *
 * @author L.cm
 */
public final class RuleContext {

	/**
	 * 解码后的 payload 在 {@link #getAttributes()} 中的键名。
	 */
	public static final String PAYLOAD_ATTR = "payload";

	private final ChannelContext channelContext;
	private final RuleChannelInfo channel;
	private final String clientId;
	private final String topic;
	private final MqttQoS qos;
	private final byte[] payload;
	private final boolean retain;
	private final Map<String, String> headers;
	private final Rule rule;
	private final Map<String, Object> attributes;

	public RuleContext(ChannelContext channelContext,
					   RuleChannelInfo channel,
					   String clientId,
					   String topic,
					   MqttQoS qos,
					   byte[] payload,
					   boolean retain,
					   Map<String, String> headers,
					   Rule rule) {
		this.channelContext = channelContext;
		this.channel = channel;
		this.clientId = clientId;
		this.topic = topic;
		this.qos = qos;
		this.payload = payload;
		this.retain = retain;
		this.headers = headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(headers));
		this.rule = rule;
		// actions 在 action 线程池线程上执行，用并发 Map 承载 action 之间的中间结果
		this.attributes = new ConcurrentHashMap<>();
	}

	public ChannelContext getChannelContext() {
		return channelContext;
	}

	public RuleChannelInfo getChannel() {
		return channel;
	}

	public String getClientId() {
		return clientId;
	}

	public String getTopic() {
		return topic;
	}

	public MqttQoS getQos() {
		return qos;
	}

	public byte[] getPayload() {
		return payload;
	}

	public boolean isRetain() {
		return retain;
	}

	public Map<String, String> getHeaders() {
		return headers;
	}

	public Rule getRule() {
		return rule;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public Object attr(String key) {
		return attributes.get(key);
	}

	public RuleContext attr(String key, Object value) {
		attributes.put(key, value);
		return this;
	}
}
