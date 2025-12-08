/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.MqttQoS;

/**
 * Model the SubscriptionOption used in Subscribe MQTT v5 packet
 *
 * @author netty
 */
public final class MqttSubscriptionOption {

	private final MqttQoS qos;
	private final boolean noLocal;
	private final boolean retainAsPublished;
	private final RetainedHandlingPolicy retainHandling;

	public MqttSubscriptionOption(MqttQoS qos,
								  boolean noLocal,
								  boolean retainAsPublished,
								  RetainedHandlingPolicy retainHandling) {
		this.qos = qos;
		this.noLocal = noLocal;
		this.retainAsPublished = retainAsPublished;
		this.retainHandling = retainHandling;
	}

	public static MqttSubscriptionOption onlyFromQos(MqttQoS qos) {
		return new MqttSubscriptionOption(qos, false, false, RetainedHandlingPolicy.SEND_AT_SUBSCRIBE);
	}

	public MqttQoS qos() {
		return qos;
	}

	public boolean isNoLocal() {
		return noLocal;
	}

	public boolean isRetainAsPublished() {
		return retainAsPublished;
	}

	public RetainedHandlingPolicy retainHandling() {
		return retainHandling;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		MqttSubscriptionOption that = (MqttSubscriptionOption) o;

		if (noLocal != that.noLocal) {
			return false;
		}
		if (retainAsPublished != that.retainAsPublished) {
			return false;
		}
		if (qos != that.qos) {
			return false;
		}
		return retainHandling == that.retainHandling;
	}

	@Override
	public int hashCode() {
		int result = qos.hashCode();
		result = 31 * result + (noLocal ? 1 : 0);
		result = 31 * result + (retainAsPublished ? 1 : 0);
		result = 31 * result + retainHandling.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "SubscriptionOption[" +
			"qos=" + qos +
			", noLocal=" + noLocal +
			", retainAsPublished=" + retainAsPublished +
			", retainHandling=" + retainHandling +
			']';
	}

	/**
	 * 保留处理政策
	 */
	public enum RetainedHandlingPolicy {
		/**
		 * 订阅发送
		 */
		SEND_AT_SUBSCRIBE((byte) 0),
		/**
		 * 如果还没有订阅，请发送
		 */
		SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS((byte) 1),
		/**
		 * 请勿发送订阅
		 */
		DONT_SEND_AT_SUBSCRIBE((byte) 2);

		private final byte value;

		RetainedHandlingPolicy(byte value) {
			this.value = value;
		}

		public static RetainedHandlingPolicy valueOf(int value) {
			switch (value) {
				case 0:
					return SEND_AT_SUBSCRIBE;
				case 1:
					return SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS;
				case 2:
					return DONT_SEND_AT_SUBSCRIBE;
				default:
					throw new IllegalArgumentException("invalid RetainedHandlingPolicy: " + value);
			}
		}

		public byte value() {
			return value;
		}
	}
}
