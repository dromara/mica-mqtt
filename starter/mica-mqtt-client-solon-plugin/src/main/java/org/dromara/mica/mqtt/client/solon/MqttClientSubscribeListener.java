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

package org.dromara.mica.mqtt.client.solon;

import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.tio.core.ChannelContext;
import org.tio.utils.mica.ExceptionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;

/**
 * MqttClientSubscribe 注解订阅监听
 *
 * @author L.cm
 */
@Slf4j
public class MqttClientSubscribeListener implements IMqttClientMessageListener {
	private final Object bean;
	private final Method method;
	private final int paramCount;
	private final Type[] parameterTypes;
	private final MqttDeserializer deserializer;

	public MqttClientSubscribeListener(Object bean, Method method, MqttDeserializer deserializer) {
		this.bean = bean;
		this.method = method;
		this.paramCount = method.getParameterCount();
		this.parameterTypes = method.getGenericParameterTypes();
		this.deserializer = deserializer;
	}

	@Override
	public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
		// 获取方法参数
		Object[] args = getMethodParameters(topic, message, payload);
		// 方法调用
		try {
			method.invoke(bean, args);
		} catch (Throwable e) {
			throw ExceptionUtils.unchecked(e);
		}
	}

	/**
	 * 获取反射参数
	 *
	 * @param topic          topic
	 * @param message        message
	 * @param payload        payload
	 * @return Object array
	 */
	protected Object[] getMethodParameters(String topic, MqttPublishMessage message, byte[] payload) {
		Object[] parameters = new Object[paramCount];
		for (int i = 0; i < paramCount; i++) {
			Type parameterType = parameterTypes[i];
			if (parameterType == String.class) {
				parameters[i] = topic;
			} else if (parameterType == MqttPublishMessage.class) {
				parameters[i] = message;
			} else if (parameterType == byte[].class) {
				parameters[i] = payload;
			} else if (parameterType == ByteBuffer.class) {
				parameters[i] = ByteBuffer.wrap(payload);
			} else {
				parameters[i] = deserializer.deserialize(payload, parameterType);
			}
		}
		return parameters;
	}
}
