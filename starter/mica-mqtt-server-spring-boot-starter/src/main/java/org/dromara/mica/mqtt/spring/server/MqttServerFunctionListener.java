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

package org.dromara.mica.mqtt.spring.server;

import lombok.RequiredArgsConstructor;
import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.server.func.IMqttFunctionMessageListener;
import org.springframework.util.ReflectionUtils;
import org.tio.core.ChannelContext;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * mqtt 服务端函数消息监听器
 *
 * @author L.cm
 */
@RequiredArgsConstructor
class MqttServerFunctionListener implements IMqttFunctionMessageListener {
	private final Object bean;
	private final Method method;
	private final MqttDeserializer deserializer;

	@Override
	public void onMessage(ChannelContext context, String clientId, String topic, MqttQoS qoS, MqttPublishMessage message) {
		// 处理参数
		Object[] methodParameters = getMethodParameters(method, context, topic, message, message.payload());
		// 反射调用
		ReflectionUtils.invokeMethod(method, bean, methodParameters);
	}

	/**
	 * 获取反射参数
	 *
	 * @param method  Method
	 * @param topic   topic
	 * @param message message
	 * @param payload payload
	 * @return Object array
	 */
	private Object[] getMethodParameters(Method method, ChannelContext context,
										 String topic, MqttPublishMessage message, byte[] payload) {
		int paramCount = method.getParameterCount();
		Class<?>[] parameterTypes = method.getParameterTypes();
		Object[] parameters = new Object[paramCount];
		for (int i = 0; i < parameterTypes.length; i++) {
			Class<?> parameterType = parameterTypes[i];
			if (parameterType == ChannelContext.class) {
				parameters[i] = context;
			} else if (parameterType == String.class) {
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
