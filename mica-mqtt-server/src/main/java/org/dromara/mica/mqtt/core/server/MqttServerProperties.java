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

/**
 * mqtt 5.0 服务端能力配置，用于在 CONNACK 中告知客户端。
 * <p>
 * 字段命名严格遵循 MQTT 5.0 spec 3.2.2.3，参考 codec 模块的
 * {@link org.dromara.mica.mqtt.codec.message.properties.MqttConnAckProperties}。
 * <p>
 * 默认值参考 spec 协议默认值或 mica-mqtt 历史行为：
 * <ul>
 *   <li>{@link #receiveMaximum} = 65535 (spec 默认)</li>
 *   <li>{@link #maximumPacketSize} = 268435456 (256MB，spec 默认)</li>
 *   <li>{@link #topicAliasMaximum} = 0 (spec 默认：不启用)</li>
 *   <li>{@link #maximumQos} = 2 (QoS2)</li>
 *   <li>{@link #subscriptionIdentifierAvailable} = false (运行时未回填订阅标识符前不宣告支持)</li>
 *   <li>其余 boolean 默认 true</li>
 * </ul>
 *
 * @author L.cm
 */
public class MqttServerProperties {

	/**
	 * Receive Maximum，服务端允许客户端同时处理的 QoS1/QoS2 未确认报文上限
	 */
	private int receiveMaximum = 65535;

	/**
	 * Maximum QoS，服务端支持的最大 QoS
	 */
	private int maximumQos = 2;

	/**
	 * Retain Available，服务端是否支持保留消息
	 */
	private boolean retainAvailable = true;

	/**
	 * Maximum Packet Size，服务端可处理的最大报文大小（字节）
	 */
	private int maximumPacketSize = 268435456;

	/**
	 * Topic Alias Maximum，服务端支持的最大主题别名数（0 表示不启用）
	 */
	private int topicAliasMaximum = 0;

	/**
	 * Wildcard Subscription Available，服务端是否支持通配符订阅
	 */
	private boolean wildcardSubscriptionAvailable = true;

	/**
	 * Shared Subscription Available，服务端是否支持共享订阅
	 */
	private boolean sharedSubscriptionAvailable = true;

	/**
	 * Subscription Identifier Available，服务端是否支持订阅标识符
	 */
	private boolean subscriptionIdentifierAvailable = false;
	/**
	 * Server Keep Alive，服务端接管心跳（秒），0 表示不接管。
	 * <p>
	 * 配置值会作为 CONNACK 的 Server Keep Alive 下发，客户端应使用该值替换 CONNECT 中的 Keep Alive。
	 * 仅在实际接管时才下发该字段。
	 */
	private int serverKeepAlive = 0;

	public int getReceiveMaximum() {
		return receiveMaximum;
	}

	public MqttServerProperties receiveMaximum(int receiveMaximum) {
		this.receiveMaximum = receiveMaximum;
		return this;
	}

	public int getMaximumQos() {
		return maximumQos;
	}

	public MqttServerProperties maximumQos(int maximumQos) {
		if (maximumQos < 0 || maximumQos > 2) {
			throw new IllegalArgumentException("maximumQos must be in [0, 2], got " + maximumQos);
		}
		this.maximumQos = maximumQos;
		return this;
	}

	public boolean isRetainAvailable() {
		return retainAvailable;
	}

	public MqttServerProperties retainAvailable(boolean retainAvailable) {
		this.retainAvailable = retainAvailable;
		return this;
	}

	public int getMaximumPacketSize() {
		return maximumPacketSize;
	}

	public MqttServerProperties maximumPacketSize(int maximumPacketSize) {
		this.maximumPacketSize = maximumPacketSize;
		return this;
	}

	public int getTopicAliasMaximum() {
		return topicAliasMaximum;
	}

	public MqttServerProperties topicAliasMaximum(int topicAliasMaximum) {
		this.topicAliasMaximum = topicAliasMaximum;
		return this;
	}

	public boolean isWildcardSubscriptionAvailable() {
		return wildcardSubscriptionAvailable;
	}

	public MqttServerProperties wildcardSubscriptionAvailable(boolean wildcardSubscriptionAvailable) {
		this.wildcardSubscriptionAvailable = wildcardSubscriptionAvailable;
		return this;
	}

	public boolean isSharedSubscriptionAvailable() {
		return sharedSubscriptionAvailable;
	}

	public MqttServerProperties sharedSubscriptionAvailable(boolean sharedSubscriptionAvailable) {
		this.sharedSubscriptionAvailable = sharedSubscriptionAvailable;
		return this;
	}

	public boolean isSubscriptionIdentifierAvailable() {
		return subscriptionIdentifierAvailable;
	}

	public MqttServerProperties subscriptionIdentifierAvailable(boolean subscriptionIdentifierAvailable) {
		this.subscriptionIdentifierAvailable = subscriptionIdentifierAvailable;
		return this;
	}

	public int getServerKeepAlive() {
		return serverKeepAlive;
	}

	public MqttServerProperties serverKeepAlive(int serverKeepAlive) {
		this.serverKeepAlive = serverKeepAlive;
		return this;
	}
}
