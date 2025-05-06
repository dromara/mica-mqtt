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

package org.dromara.mica.mqtt.spring.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.core.client.IMqttClientMessageListener;
import org.dromara.mica.mqtt.core.client.IMqttClientSession;
import org.dromara.mica.mqtt.core.util.TopicUtil;
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
 * MqttClient 订阅监听器
 *
 * @author L.cm
 */
@Slf4j
@RequiredArgsConstructor
public class MqttClientSubscribeDetector implements BeanPostProcessor {
	private final ApplicationContext applicationContext;

	@Override
	public Object postProcessAfterInitialization(@NonNull Object bean, String beanName) throws BeansException {
		Class<?> userClass = ClassUtils.getUserClass(bean);
		if (bean instanceof IMqttClientMessageListener) {
			// 1. 查找类上的 MqttClientSubscribe 注解
			processClassLevelSubscription(userClass,  bean);
		} else {
			// 2. 查找方法上的 MqttClientSubscribe 注解
			processMethodLevelSubscriptions(userClass, bean);
		}
		return bean;
	}

	/**
	 * 处理类上的 @MqttClientSubscribe 注解。
	 * 为实现了 IMqttClientMessageListener 接口的类自动注册 MQTT 订阅。
	 */
	protected void processClassLevelSubscription(Class<?> userClass, Object bean) {
		MqttClientSubscribe subscribe = AnnotationUtils.findAnnotation(userClass, MqttClientSubscribe.class);
		if (subscribe != null) {
			IMqttClientSession clientSession = getMqttClientSession(applicationContext, subscribe.clientTemplateBean());
			String[] topicFilters = getTopicFilters(applicationContext, subscribe.value());
			clientSession.addSubscriptionList(topicFilters, subscribe.qos(), (IMqttClientMessageListener) bean);
		}
	}

	/**
	 * 处理方法上的 @MqttClientSubscribe 注解。
	 * 为符合签名的方法自动注册 MQTT 订阅监听器。
	 */
	protected void processMethodLevelSubscriptions(Class<?> userClass, Object bean) {
		ReflectionUtils.doWithMethods(userClass, method -> {
			MqttClientSubscribe subscribe = AnnotationUtils.findAnnotation(method, MqttClientSubscribe.class);
			if (subscribe != null) {
				// 1. 校验必须为 public 和非 static 的方法
				int modifiers = method.getModifiers();
				if (Modifier.isStatic(modifiers)) {
					throw new IllegalArgumentException("@MqttClientSubscribe on method " + method + " must not static.");
				}
				if (!Modifier.isPublic(modifiers)) {
					throw new IllegalArgumentException("@MqttClientSubscribe on method " + method + " must public.");
				}
				// 2. 校验 method 入参数必须等于2
				int paramCount = method.getParameterCount();
				if (paramCount != 2) {
					throw new IllegalArgumentException("@MqttClientSubscribe on method " + method + " parameter count must equal to 2.");
				}
				// 3. 校验 method 入参类型必须为 String、ByteBuffer
				Class<?>[] parameterTypes = method.getParameterTypes();
				Class<?> topicParamType = parameterTypes[0];
				Class<?> payloadParamType = parameterTypes[1];
				if (String.class != topicParamType || byte[].class != payloadParamType) {
					throw new IllegalArgumentException("@MqttClientSubscribe on method " + method + " parameter type must String topic and byte[] payload.");
				}
				// 4. 订阅
				IMqttClientSession clientSession = getMqttClientSession(applicationContext, subscribe.clientTemplateBean());
				String[] topicFilters = getTopicFilters(applicationContext, subscribe.value());
				clientSession.addSubscriptionList(topicFilters, subscribe.qos(), (context, topic, message, payload) ->
						ReflectionUtils.invokeMethod(method, bean, topic, payload)
				);
			}
		}, ReflectionUtils.USER_DECLARED_METHODS);
	}

	/**
	 * 读取 IMqttClientSession
	 *
	 * @param applicationContext ApplicationContext
	 * @param beanName           beanName
	 * @return IMqttClientSession
	 */
	protected IMqttClientSession getMqttClientSession(ApplicationContext applicationContext, String beanName) {
		return applicationContext.getBean(beanName, MqttClientTemplate.class).getClientCreator().getClientSession();
	}

	protected String[] getTopicFilters(ApplicationContext applicationContext, String[] values) {
		// 1. 替换 Spring boot env 变量
		// 2. 替换订阅中的其他变量
		return Arrays.stream(values)
			.map(applicationContext.getEnvironment()::resolvePlaceholders)
			.map(TopicUtil::getTopicFilter)
			.toArray(String[]::new);
	}

}
