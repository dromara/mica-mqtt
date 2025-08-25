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
import org.dromara.mica.mqtt.core.function.ParamValueFunction;
import org.dromara.mica.mqtt.core.util.MethodParamUtil;
import org.tio.core.ChannelContext;
import org.tio.utils.mica.ExceptionUtils;

import java.lang.reflect.Method;

/**
 * MqttClientSubscribe 注解订阅监听
 *
 * @author L.cm
 */
@Slf4j
public class MqttClientSubscribeListener implements IMqttClientMessageListener {
	private final Object bean;
	private final Method method;
	private final ParamValueFunction[] paramValueFunctions;

	public MqttClientSubscribeListener(Object bean, Method method, String[] topicTemplates, String[] topicFilters, MqttDeserializer deserializer) {
		this.bean = bean;
		this.method = method;
		this.paramValueFunctions = MethodParamUtil.getParamValueFunctions(method, topicTemplates, topicFilters, deserializer);
	}

	@Override
	public void onMessage(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
		// 获取方法参数
		Object[] args = getMethodParameters(context, topic, message, payload);
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
	 * @param context context
	 * @param topic   topic
	 * @param message message
	 * @param payload payload
	 * @return Object array
	 */
	protected Object[] getMethodParameters(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
		int length = paramValueFunctions.length;
		Object[] parameters = new Object[length];
		for (int i = 0; i < length; i++) {
			ParamValueFunction function = paramValueFunctions[i];
			parameters[i] = function.getValue(context, topic, message, payload);
		}
		return parameters;
	}

}
