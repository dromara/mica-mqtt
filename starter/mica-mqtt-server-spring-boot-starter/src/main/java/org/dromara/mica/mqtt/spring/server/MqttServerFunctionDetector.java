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
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.core.annotation.MqttServerFunction;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.server.func.IMqttFunctionMessageListener;
import org.dromara.mica.mqtt.core.server.func.MqttFunctionManager;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Mqtt 服务端消息处理
 *
 * @author L.cm
 */
@Slf4j
@RequiredArgsConstructor
public class MqttServerFunctionDetector implements BeanPostProcessor {
	private final ApplicationContext applicationContext;
	private final MqttFunctionManager functionManager;

	@Override
	public Object postProcessAfterInitialization(@NonNull Object bean, String beanName) throws BeansException {
		Class<?> userClass = ClassUtils.getUserClass(bean);
		// 1. 查找类上的 MqttServerFunction 注解
		if (bean instanceof IMqttFunctionMessageListener) {
			MqttServerFunction subscribe = AnnotationUtils.findAnnotation(userClass, MqttServerFunction.class);
			if (subscribe != null) {
				String[] topicFilters = getTopicFilters(applicationContext, subscribe.value());
				functionManager.register(topicFilters, (IMqttFunctionMessageListener) bean);
			}
		} else {
			// 2. 查找方法上的 MqttServerFunction 注解
			ReflectionUtils.doWithMethods(userClass, method -> {
				MqttServerFunction subscribe = AnnotationUtils.findAnnotation(method, MqttServerFunction.class);
				if (subscribe != null) {
					// 1. 校验必须为 public 和非 static 的方法
					int modifiers = method.getModifiers();
					if (Modifier.isStatic(modifiers)) {
						throw new IllegalArgumentException("@MqttServerFunction on method " + method + " must not static.");
					}
					if (!Modifier.isPublic(modifiers)) {
						throw new IllegalArgumentException("@MqttServerFunction on method " + method + " must public.");
					}
					// 2. 校验 method 入参数必须等于2
					int paramCount = method.getParameterCount();
					if (paramCount < 2 || paramCount > 6) {
						throw new IllegalArgumentException("@MqttServerFunction on method " + method + " parameter count must 2 ~ 6.");
					}
					// 3. topic 信息
					String[] topicTemplates = subscribe.value();
					String[] topicFilters = getTopicFilters(applicationContext, topicTemplates);
					// 4. 自定义的反序列化，支持 Spring bean 或者 无参构造器初始化
					Class<? extends MqttDeserializer> deserialized = subscribe.deserialize();
					@SuppressWarnings("unchecked")
					MqttDeserializer deserializer = getMqttDeserializer((Class<MqttDeserializer>) deserialized);
					// 5. 监听器
					MqttServerFunctionListener functionListener = new MqttServerFunctionListener(bean, method, deserializer);
					// 6. 注册监听器
					functionManager.register(topicFilters, functionListener);
				}
			}, ReflectionUtils.USER_DECLARED_METHODS);
		}
		return bean;
	}

	/**
	 * 获取解码器
	 *
	 * @param deserializerType deserializerType
	 * @return 解码器
	 */
	protected MqttDeserializer getMqttDeserializer(Class<MqttDeserializer> deserializerType) {
		return applicationContext.getBeanProvider(deserializerType)
			.getIfAvailable(() -> BeanUtils.instantiateClass(deserializerType));
	}

	/**
	 * 解析 Spring boot env 变量
	 *
	 * @param applicationContext ApplicationContext
	 * @param values             values
	 * @return topic array
	 */
	private static String[] getTopicFilters(ApplicationContext applicationContext, String[] values) {
		// 1. 替换 Spring boot env 变量
		// 2. 替换订阅中的其他变量
		return Arrays.stream(values)
			.map(applicationContext.getEnvironment()::resolvePlaceholders)
			.map(TopicUtil::getTopicFilter)
			.toArray(String[]::new);
	}

}
