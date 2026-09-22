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

import net.dreamlu.mica.net.utils.hutool.ClassUtil;
import net.dreamlu.mica.net.utils.hutool.CollUtil;
import net.dreamlu.mica.net.utils.hutool.StrUtil;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.codec.message.builder.MqttPublishBuilder;
import org.dromara.mica.mqtt.codec.properties.MqttProperties;
import org.dromara.mica.mqtt.core.annotation.MqttClientPublish;
import org.dromara.mica.mqtt.core.annotation.MqttPayload;
import org.dromara.mica.mqtt.core.annotation.MqttRetain;
import org.dromara.mica.mqtt.core.annotation.TopicParam;
import org.dromara.mica.mqtt.core.util.TopicUtil;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @author ChangJin Wei (魏昌进)
 */
public class MqttInvocationHandler<T extends IMqttClient> implements InvocationHandler {
	private static final Object[] EMPTY_ARGS = new Object[0];
	private static final int ALLOWED_MODES = MethodHandles.Lookup.PUBLIC
		| MethodHandles.Lookup.PRIVATE
		| MethodHandles.Lookup.PROTECTED
		| MethodHandles.Lookup.PACKAGE;
	private static final Constructor<MethodHandles.Lookup> LOOKUP_CONSTRUCTOR;
	private static final Method PRIVATE_LOOKUP_IN_METHOD;

	static {
		Method privateLookupInMethod;
		try {
			privateLookupInMethod = MethodHandles.class.getMethod(
				"privateLookupIn", Class.class, MethodHandles.Lookup.class
			);
		} catch (NoSuchMethodException e) {
			privateLookupInMethod = null;
		}
		PRIVATE_LOOKUP_IN_METHOD = privateLookupInMethod;

		Constructor<MethodHandles.Lookup> lookupConstructor = null;
		if (PRIVATE_LOOKUP_IN_METHOD == null) {
			try {
				lookupConstructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
				lookupConstructor.setAccessible(true);
			} catch (NoSuchMethodException e) {
				throw new IllegalStateException("No compatible default method invoker found", e);
			}
		}
		LOOKUP_CONSTRUCTOR = lookupConstructor;
	}

	private final T mqttClient;
	private final Map<Method, MqttMethodInvoker> methodCache;

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
		return cachedInvoker(method).invoke(proxy, method, args, mqttClient);
	}

	private MqttMethodInvoker cachedInvoker(Method method) throws Throwable {
		try {
			return CollUtil.computeIfAbsent(methodCache, method, m -> {
				if (m.isDefault()) {
					try {
						return new DefaultMethodInvoker(getMethodHandle(m));
					} catch (ReflectiveOperationException e) {
						throw new IllegalStateException("Failed to get default method handle: " + m, e);
					}
				}
				return new PlainMethodInvoker(resolveMethod(m));
			});
		} catch (RuntimeException e) {
			Throwable cause = e.getCause();
			if (cause instanceof ReflectiveOperationException) {
				throw cause;
			}
			throw e;
		}
	}

	private static MethodHandle getMethodHandle(Method method) throws ReflectiveOperationException {
		if (PRIVATE_LOOKUP_IN_METHOD == null) {
			return getMethodHandleJava8(method);
		}
		return getMethodHandleJava9(method);
	}

	private static MethodHandle getMethodHandleJava9(Method method) throws ReflectiveOperationException {
		Class<?> declaringClass = method.getDeclaringClass();
		return ((MethodHandles.Lookup) PRIVATE_LOOKUP_IN_METHOD.invoke(null, declaringClass, MethodHandles.lookup()))
			.findSpecial(declaringClass, method.getName(),
				MethodType.methodType(method.getReturnType(), method.getParameterTypes()), declaringClass);
	}

	private static MethodHandle getMethodHandleJava8(Method method) throws ReflectiveOperationException {
		Class<?> declaringClass = method.getDeclaringClass();
		return LOOKUP_CONSTRUCTOR.newInstance(declaringClass, ALLOWED_MODES)
			.findSpecial(declaringClass, method.getName(),
				MethodType.methodType(method.getReturnType(), method.getParameterTypes()), declaringClass);
	}

	private MethodMetadata resolveMethod(Method m) {
		MqttClientPublish mqttPublish = m.getAnnotation(MqttClientPublish.class);
		if (mqttPublish == null) {
			throw new UnsupportedOperationException("Method not annotated with @MqttClientPublish");
		}

		Annotation[][] paramAnnotations = m.getParameterAnnotations();
		Class<?>[] paramTypes = m.getParameterTypes();
		Parameter[] parameters = m.getParameters();

		int payloadIndex = -1;
		int retainIndex = -1;
		int propertiesIndex = -1;
		int builderIndex = -1;
		Map<String, Integer> variableParamIndices = new LinkedHashMap<>();

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

		// 记录可作为占位符数据源的方法参数。
		// 1) 优先使用 @TopicParam 显式声明的变量名；
		// 2) 缺失时回退到编译期参数名（需开启 -parameters / <parameters>true</parameters>）；
		// 3) 都没拿到就跳过，避免拿到 arg0/arg1 后把 ${productKey} 静默替换成 arg0。
		for (int i = 0; i < parameters.length; i++) {
			if (i == payloadIndex || i == retainIndex || i == propertiesIndex || i == builderIndex) {
				continue;
			}
			// 参数名
			String paramName = resolveParameterName(parameters[i]);
			if (StrUtil.isNotBlank(paramName)) {
				variableParamIndices.put(paramName, i);
			}
		}

		return new MethodMetadata(mqttPublish, payloadIndex, retainIndex, propertiesIndex, builderIndex, variableParamIndices);
	}

	/**
	 * 解析参数的占位符名称：先看 @TopicParam 显式声明，没有时回退到编译期参数名。
	 * 都没有时返回 null，由调用方跳过该参数。
	 *
	 * @param parameter 方法参数
	 * @return 变量名
	 */
	private static String resolveParameterName(Parameter parameter) {
		TopicParam topicParam = parameter.getAnnotation(TopicParam.class);
		if (topicParam != null) {
			String name = topicParam.value();
			if (StrUtil.isNotBlank(name)) {
				return name;
			}
		}
		if (parameter.isNamePresent()) {
			String name = parameter.getName();
			if (StrUtil.isNotBlank(name)) {
				return name;
			}
		}
		return null;
	}

	private interface MqttMethodInvoker {

		Object invoke(Object proxy, Method method, Object[] args, IMqttClient mqttClient) throws Throwable;
	}

	private static class PlainMethodInvoker implements MqttMethodInvoker {

		private final MethodMetadata metadata;

		PlainMethodInvoker(MethodMetadata metadata) {
			this.metadata = metadata;
		}

		@SuppressWarnings("unchecked")
		@Override
		public Object invoke(Object proxy, Method method, Object[] args, IMqttClient mqttClient) {
			Object payload = metadata.getPayloadIndex() >= 0 ? args[metadata.getPayloadIndex()] : null;
			boolean retain = metadata.getRetainIndex() >= 0 && Boolean.TRUE.equals(args[metadata.getRetainIndex()]);
			MqttProperties properties = metadata.getPropertiesIndex() >= 0
				? (MqttProperties) args[metadata.getPropertiesIndex()]
				: null;
			Consumer<MqttPublishBuilder> builder = metadata.getBuilderIndex() >= 0
				? (Consumer<MqttPublishBuilder>) args[metadata.getBuilderIndex()]
				: null;

			// 按需取值：先把方法参数按 ${var} 查一次，未匹配再回退到 payload 字段。
			String topic = TopicUtil.resolveTopic(metadata.getMqttPublish().value(), (fieldName) -> {
				Integer index = metadata.getVariableParamIndices().get(fieldName);
				Object value = (index != null && args != null && index < args.length) ? args[index] : null;
				if (value == null) {
					value = ClassUtil.getFieldValue(payload, fieldName);
				}
				return value;
			});

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
	}

	private static class DefaultMethodInvoker implements MqttMethodInvoker {

		private final MethodHandle methodHandle;

		DefaultMethodInvoker(MethodHandle methodHandle) {
			this.methodHandle = methodHandle;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args, IMqttClient mqttClient) throws Throwable {
			return methodHandle.bindTo(proxy).invokeWithArguments(normalizeArgs(args));
		}
	}

	private static Object[] normalizeArgs(Object[] args) {
		return (args == null) ? EMPTY_ARGS : args;
	}

	private static class MethodMetadata {

		private final MqttClientPublish mqttPublish;

		private final int payloadIndex;

		private final int retainIndex;

		private final int propertiesIndex;

		private final int builderIndex;

		private final Map<String, Integer> variableParamIndices;

		MethodMetadata(MqttClientPublish mqttPublish,
					   int payloadIndex,
					   int retainIndex,
					   int propertiesIndex,
					   int builderIndex,
					   Map<String, Integer> variableParamIndices) {
			this.mqttPublish = mqttPublish;
			this.payloadIndex = payloadIndex;
			this.retainIndex = retainIndex;
			this.propertiesIndex = propertiesIndex;
			this.builderIndex = builderIndex;
			this.variableParamIndices = variableParamIndices;
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

		public Map<String, Integer> getVariableParamIndices() {
			return variableParamIndices;
		}
	}
}
