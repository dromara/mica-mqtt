/*
 * Copyright 2025-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.builder.MqttPublishBuilder;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.annotation.MqttClientPublish;
import org.dromara.mica.mqtt.core.annotation.MqttPayload;
import org.dromara.mica.mqtt.core.annotation.MqttRetain;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.tio.utils.hutool.CollUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * @author ChangJin Wei (魏昌进)
 */
public class MqttInvocationHandler<T extends IMqttClient> implements InvocationHandler {
	private final T mqttClient;
	private final ConcurrentMap<Method, MethodMetadata> methodCache;

	public MqttInvocationHandler(T mqttClient) {
		this.mqttClient = mqttClient;
		this.methodCache = new ConcurrentHashMap<>();
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		// 处理默认的 hashCode、equals 和 toString
		if (Object.class.equals(method.getDeclaringClass())) {
			return method.invoke(this, args);
		}
		// 其它代理方法
		MethodMetadata metadata = resolveMethod(method);

		Object payload = metadata.getPayloadIndex() >= 0 ? args[metadata.getPayloadIndex()] : null;
		boolean retain = metadata.getRetainIndex() >= 0 && Boolean.TRUE.equals(args[metadata.getRetainIndex()]);
		MqttProperties properties = metadata.getPropertiesIndex() >= 0
			? (MqttProperties) args[metadata.getPropertiesIndex()]
			: null;
		Consumer<MqttPublishBuilder> builder = metadata.getBuilderIndex() >= 0
			? (Consumer<MqttPublishBuilder>) args[metadata.getBuilderIndex()]
			: null;

		String topic = TopicUtil.resolveTopic(metadata.getMqttPublish().value(), payload);
		MqttQoS qos = metadata.getMqttPublish().qos();

		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("Resolved topic is null or empty");
		}
		MqttClient client = mqttClient.getMqttClient();
		if (builder == null) {
			return client.publish(topic, payload, qos, retain, properties);
		} else {
			return client.publish(topic, payload, qos, builder);
		}
	}

	private MethodMetadata resolveMethod(Method method) {
		return CollUtil.computeIfAbsent(methodCache, method, m -> {
			MqttClientPublish mqttPublish = m.getAnnotation(MqttClientPublish.class);
			if (mqttPublish == null) {
				throw new UnsupportedOperationException("Method not annotated with @MqttClientPublish");
			}

			Annotation[][] paramAnnotations = m.getParameterAnnotations();
			Class<?>[] paramTypes = m.getParameterTypes();

			int payloadIndex = -1;
			int retainIndex = -1;
			int propertiesIndex = -1;
			int builderIndex = -1;

			for (int i = 0; i < paramAnnotations.length; i++) {
				for (Annotation annotation : paramAnnotations[i]) {
					if (annotation instanceof MqttPayload) {
						payloadIndex = i;
					} else if (annotation instanceof MqttRetain) {
						retainIndex = i;
					}
				}
			}

			for (int i = 0; i < paramTypes.length; i++) {
				if (propertiesIndex == -1 && MqttProperties.class.isAssignableFrom(paramTypes[i])) {
					propertiesIndex = i;
				} else if (builderIndex == -1 && Consumer.class.isAssignableFrom(paramTypes[i])) {
					builderIndex = i;
				}
			}

			return new MethodMetadata(mqttPublish, payloadIndex, retainIndex, propertiesIndex, builderIndex);
		});
	}

	private static class MethodMetadata {

		private final MqttClientPublish mqttPublish;

		private final int payloadIndex;

		private final int retainIndex;

		private final int propertiesIndex;

		private final int builderIndex;

		MethodMetadata(MqttClientPublish mqttPublish,
					   int payloadIndex,
					   int retainIndex,
					   int propertiesIndex,
					   int builderIndex) {
			this.mqttPublish = mqttPublish;
			this.payloadIndex = payloadIndex;
			this.retainIndex = retainIndex;
			this.propertiesIndex = propertiesIndex;
			this.builderIndex = builderIndex;
		}

		public MqttClientPublish getMqttPublish() {
			return mqttPublish;
		}

		public int getPayloadIndex() {
			return payloadIndex;
		}

		public int getRetainIndex() {
			return retainIndex;
		}

		public int getPropertiesIndex() {
			return propertiesIndex;
		}

		public int getBuilderIndex() {
			return builderIndex;
		}
	}
}
