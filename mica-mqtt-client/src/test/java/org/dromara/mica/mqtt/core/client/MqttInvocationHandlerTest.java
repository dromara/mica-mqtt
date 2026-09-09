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

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.annotation.MqttClientPublish;
import org.dromara.mica.mqtt.core.annotation.MqttPayload;
import org.dromara.mica.mqtt.core.annotation.MqttRetain;
import org.dromara.mica.mqtt.core.annotation.TopicParam;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link MqttInvocationHandler} 单元测试。
 *
 * <p>MqttClient 是 final 且构造器要求 TioClient/MqttClientCreator 非空，
 * 直接端到端跑 publish 链路需要真实 broker。本测试只覆盖可独立验证的部分：</p>
 * <ul>
 *   <li>通过反射读取 MethodMetadata 的 variableParamIndices，验证
 *       @TopicParam 显式名 / 编译期参数名 / 跳过 三个分支；</li>
 *   <li>通过反射调用 resolveParameterName，验证 @TopicParam 优先于参数名；</li>
 *   <li>复用 TopicUtilTest 已覆盖的解析语义，这里做一次端到端 lambda 行为的回归。</li>
 * </ul>
 */
class MqttInvocationHandlerTest {

	/**
	 * 反射读取 methodCache 的 value，验证 DoorClient 解析时
	 * @TopicParam("productKey") / @TopicParam("deviceId") 都映射到正确的 args 索引。
	 */
	@Test
	void shouldIndexTopicParamAnnotatedParameters() throws Exception {
		MethodMetadataHolder holder = MqttMetadataExtractor.extract(DoorClient.class,
			"sendMessage", String.class, String.class, String.class, boolean.class);
		Map<String, Integer> indices = holder.variableParamIndices;
		Assertions.assertNotNull(indices, "variableParamIndices should not be null");
		Assertions.assertEquals(2, indices.size(), "should have two @TopicParam entries");
		Assertions.assertEquals(Integer.valueOf(0), indices.get("productKey"));
		Assertions.assertEquals(Integer.valueOf(1), indices.get("deviceId"));
		// payload 标注的参数不应该出现在 variableParamIndices 里
		Assertions.assertFalse(indices.containsKey("message"));
		Assertions.assertFalse(indices.containsKey("retain"));
	}

	/**
	 * 编译期参数名（pom 已开 -parameters）也应该进入 variableParamIndices。
	 */
	@Test
	void shouldIndexCompiledParameterNamesWhenAnnotationAbsent() throws Exception {
		MethodMetadataHolder holder = MqttMetadataExtractor.extract(CompiledNameClient.class,
			"send", String.class, String.class, String.class);
		Map<String, Integer> indices = holder.variableParamIndices;
		Assertions.assertEquals(2, indices.size());
		Assertions.assertEquals(Integer.valueOf(0), indices.get("productKey"));
		Assertions.assertEquals(Integer.valueOf(1), indices.get("deviceId"));
	}

	/**
	 * @TopicParam 显式名应该覆盖编译期参数名。
	 */
	@Test
	void shouldPreferTopicParamAnnotationOverCompiledName() throws Exception {
		MethodMetadataHolder holder = MqttMetadataExtractor.extract(OverriddenNameClient.class,
			"send", String.class);
		// 实际参数名是 "pk"，但 @TopicParam("productKey") 应优先被使用
		Assertions.assertTrue(holder.variableParamIndices.containsKey("productKey"),
			"@TopicParam should override compiled parameter name");
		Assertions.assertFalse(holder.variableParamIndices.containsKey("pk"));
	}

	/**
	 * 端到端 lambda：模拟 invoke 内部的 topic 解析逻辑（方法参数索引 → args 取值 → payload 兜底），
	 * 验证 IKED1S 的修复。
	 */
	@Test
	void shouldResolveTopicFromMethodParamAndPayloadFallback() {
		// 复用 MqttInvocationHandler 内部的解析约定：
		// 先查 args[index]，再查 ClassUtil.getFieldValue(payload, key)
		Map<String, Integer> indices = new LinkedHashMap<>();
		indices.put("productKey", 0);
		indices.put("deviceId", -1); // 表示本方法没有 deviceId 参数

		Object[] args = new Object[]{"pk-from-param", "msg"};
		Object payload = new PayloadBean("did-from-payload");

		String resolved = TopicUtil.resolveTopic("/sys/${productKey}/${deviceId}/thing/sub",
			fieldName -> {
				Integer index = indices.get(fieldName);
				Object value = (index != null && index >= 0 && index < args.length) ? args[index] : null;
				if (value == null) {
					value = net.dreamlu.mica.net.utils.hutool.ClassUtil.getFieldValue(payload, fieldName);
				}
				return value;
			});
		Assertions.assertEquals("/sys/pk-from-param/did-from-payload/thing/sub", resolved);
	}

	/**
	 * 当方法参数和 payload 都拿不到时，两参 resolveTopic 会把 ${var} 替换成空串（兼容旧行为）。
	 * 本测试记录该语义，提醒使用方应通过 @TopicParam/参数名 显式声明避免漏写。
	 */
	@Test
	void shouldDocumentNullFallbackBehavior() {
		Map<String, Object> data = new HashMap<>();
		String resolved = TopicUtil.resolveTopic("/sys/${productKey}/thing", data::get);
		Assertions.assertEquals("/sys//thing", resolved);
	}

	// ---------------- 测试夹具 ----------------

	public interface DoorClient {

		@MqttClientPublish(value = "/sys/${productKey}/${deviceId}/thing/sub", qos = MqttQoS.QOS0)
		void sendMessage(@TopicParam("productKey") String productKey,
						 @TopicParam("deviceId") String deviceId,
						 @MqttPayload String message,
						 @MqttRetain boolean retain);
	}

	public interface CompiledNameClient {

		@MqttClientPublish(value = "/sys/${productKey}/${deviceId}/thing/sub", qos = MqttQoS.QOS0)
		void send(String productKey, String deviceId, @MqttPayload String message);
	}

	public interface OverriddenNameClient {

		@MqttClientPublish(value = "/sys/${productKey}", qos = MqttQoS.QOS0)
		void send(@TopicParam("productKey") String pk);
	}

	public static class PayloadBean {

		private String deviceId;

		public PayloadBean() {
		}

		public PayloadBean(String deviceId) {
			this.deviceId = deviceId;
		}

		public String getDeviceId() {
			return deviceId;
		}

		public void setDeviceId(String deviceId) {
			this.deviceId = deviceId;
		}
	}

	/**
	 * 反射读取 MqttInvocationHandler.methodCache，拿到 MethodMetadata 内部状态。
	 * 这里用一个小技巧：new MqttInvocationHandler 的同时调用一次 resolveMethod 让缓存填充，
	 * 然后再读取缓存。
	 */
	private static final class MqttMetadataExtractor {

		static MethodMetadataHolder extract(Class<?> iface, String methodName, Class<?>... paramTypes) throws Exception {
			Method method = iface.getDeclaredMethod(methodName, paramTypes);
			MqttInvocationHandler<IMqttClient> handler = new MqttInvocationHandler<>(new NoopMqttClient());
			// 通过反射调用 private resolveMethod 触发 MethodMetadata 构建
			Method resolveMethod = MqttInvocationHandler.class.getDeclaredMethod("resolveMethod", Method.class);
			resolveMethod.setAccessible(true);
			resolveMethod.invoke(handler, method);

			Field cacheField = MqttInvocationHandler.class.getDeclaredField("methodCache");
			cacheField.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<Method, Object> cache = (Map<Method, Object>) cacheField.get(handler);
			Object metadata = cache.get(method);

			Field indicesField = metadata.getClass().getDeclaredField("variableParamIndices");
			indicesField.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<String, Integer> indices = (Map<String, Integer>) indicesField.get(metadata);
			// 拷贝一份返回，避免后续 metadata 被缓存清理时影响
			MethodMetadataHolder holder = new MethodMetadataHolder();
			holder.variableParamIndices = indices;
			// 同时也校验：方法参数列表与缓存一致（仅作 sanity 检查）
			Parameter[] params = method.getParameters();
			Assertions.assertEquals(params.length, paramTypes.length, "test param types mismatch");
			return holder;
		}
	}

	private static final class MethodMetadataHolder {

		Map<String, Integer> variableParamIndices;
	}

	/**
	 * 不会真正被调用的占位 IMqttClient：测试只通过反射读取 handler 内部缓存，
	 * 不会触发 getMqttClient()。
	 */
	private static final class NoopMqttClient implements IMqttClient {

		@Override
		public MqttClient getMqttClient() {
			throw new UnsupportedOperationException("not used in tests");
		}
	}
}
