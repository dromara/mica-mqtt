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

package org.dromara.mica.mqtt.core.util;

import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.function.ObjectParamValueFunction;
import org.dromara.mica.mqtt.core.function.ParamValueFunction;
import org.dromara.mica.mqtt.core.function.ParamValueFunctions;
import org.dromara.mica.mqtt.core.function.TopicVarsParamValueFunction;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * 方法参数工具
 *
 * @author L.cm
 */
public class MethodParamUtil {

	/**
	 * 获取参数值函数
	 *
	 * @param method         方法
	 * @param topicTemplates 主题模板
	 * @param topicFilters   主题过滤器
	 * @param deserializer   反序列化
	 * @return ParamValueFunc[]
	 */
	public static ParamValueFunction[] getParamValueFunctions(Method method, String[] topicTemplates, String[] topicFilters, MqttDeserializer deserializer) {
		int parameterCount = method.getParameterCount();
		ParamValueFunction[] functions = new ParamValueFunction[parameterCount];
		Type[] parameterTypes = method.getGenericParameterTypes();
		for (int i = 0; i < parameterCount; i++) {
			Type parameterType = parameterTypes[i];
			if (parameterType == String.class) {
				functions[i] = ParamValueFunctions.Topic;
			} else if (parameterType instanceof ParameterizedType && isStringStringMap(parameterType)) {
				functions[i] = new TopicVarsParamValueFunction(topicTemplates, topicFilters);
			} else if (parameterType == MqttPublishMessage.class) {
				functions[i] = ParamValueFunctions.Message;
			} else if (parameterType == byte[].class) {
				functions[i] = ParamValueFunctions.Payload;
			} else if (parameterType == ByteBuffer.class) {
				functions[i] = ParamValueFunctions.ByteBuff;
			} else {
				functions[i] = new ObjectParamValueFunction(deserializer, parameterType);
			}
		}
		return functions;
	}

	/**
	 * 判断是否 Map String String
	 *
	 * @param parameterType parameterType
	 * @return 是否 Map String String
	 */
	private static boolean isStringStringMap(Type parameterType) {
		ParameterizedType parameterizedType = (ParameterizedType) parameterType;
		Type rawType = parameterizedType.getRawType();
		// 检查是否为 Map 类型
		if (rawType != Map.class) {
			return false;
		}
		// 获取泛型参数
		Type[] typeArguments = parameterizedType.getActualTypeArguments();
		// 检查键和值类型是否为 String
		return typeArguments[0].equals(String.class) && typeArguments[1].equals(String.class);
	}

}
