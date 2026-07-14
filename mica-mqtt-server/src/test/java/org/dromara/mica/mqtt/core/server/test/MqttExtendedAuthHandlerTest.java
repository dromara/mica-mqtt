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

package org.dromara.mica.mqtt.core.server.test;

import org.dromara.mica.mqtt.codec.codes.MqttAuthReasonCode;
import org.dromara.mica.mqtt.codec.message.properties.MqttAuthProperties;
import org.dromara.mica.mqtt.core.server.auth.IMqttServerExtendedAuthHandler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PR8：MQTT 5.0 扩展认证 handler 接口测试。
 *
 * <p>本测试覆盖 {@link IMqttServerExtendedAuthHandler} 接口契约与 {@link IMqttServerExtendedAuthHandler.AuthResult} 工厂方法，
 * 不需要 ChannelContext。具体的 mqttServer.sendAuth 行为属于 PR8 集成测试范围。
 *
 * @author L.cm
 */
class MqttExtendedAuthHandlerTest {

	// ----------------- AuthResult 工厂 -----------------

	@Test
	void authResultSuccessFactory() {
		MqttAuthProperties props = new MqttAuthProperties()
			.setAuthenticationMethod("SCRAM-SHA-256")
			.setAuthenticationData("server-signature".getBytes(StandardCharsets.UTF_8));
		IMqttServerExtendedAuthHandler.AuthResult result = IMqttServerExtendedAuthHandler.AuthResult.success(props);
		assertEquals(MqttAuthReasonCode.SUCCESS, result.getReasonCode());
		assertNotNull(result.getProperties());
		assertEquals("SCRAM-SHA-256", result.getProperties().getAuthenticationMethod());
	}

	@Test
	void authResultContinueAuthFactory() {
		MqttAuthProperties props = new MqttAuthProperties()
			.setAuthenticationMethod("SCRAM-SHA-256")
			.setAuthenticationData("server-nonce".getBytes(StandardCharsets.UTF_8));
		IMqttServerExtendedAuthHandler.AuthResult result = IMqttServerExtendedAuthHandler.AuthResult.continueAuth(props);
		assertEquals(MqttAuthReasonCode.CONTINUE_AUTHENTICATION, result.getReasonCode());
		assertNotNull(result.getProperties());
	}

	@Test
	void authResultConstructorAndAccessors() {
		MqttAuthProperties props = new MqttAuthProperties().setAuthenticationMethod("GS2-KRB5");
		IMqttServerExtendedAuthHandler.AuthResult result =
			new IMqttServerExtendedAuthHandler.AuthResult(MqttAuthReasonCode.RE_AUTHENTICATE, props);
		assertEquals(MqttAuthReasonCode.RE_AUTHENTICATE, result.getReasonCode());
		assertEquals("GS2-KRB5", result.getProperties().getAuthenticationMethod());
	}

	// ----------------- 多步 challenge/response 行为模拟 -----------------

	@Test
	void multiStepAuthFlowSimulation() {
		// 模拟 SCRAM-SHA-256 三步认证流程的内部状态机
		List<Step> trace = new ArrayList<>();
		// 客户端发 client-first
		MqttAuthProperties clientFirst = new MqttAuthProperties()
			.setAuthenticationMethod("SCRAM-SHA-256")
			.setAuthenticationData("n,,n=user,r=client-nonce".getBytes(StandardCharsets.UTF_8));
		// 服务端回 server-first（CONTINUE）
		IMqttServerExtendedAuthHandler.AuthResult serverFirst =
			IMqttServerExtendedAuthHandler.AuthResult.continueAuth(new MqttAuthProperties()
				.setAuthenticationMethod("SCRAM-SHA-256")
				.setAuthenticationData("r=client-nonce,server-nonce,s=c2FsdA==,i=4096".getBytes(StandardCharsets.UTF_8)));
		trace.add(new Step("client", clientFirst, null));
		trace.add(new Step("server", null, serverFirst));
		// 客户端发 client-final
		MqttAuthProperties clientFinal = new MqttAuthProperties()
			.setAuthenticationMethod("SCRAM-SHA-256")
			.setAuthenticationData("c=biws,r=client-nonce,server-nonce,p=client-proof".getBytes(StandardCharsets.UTF_8));
		// 服务端回 server-final（SUCCESS）
		IMqttServerExtendedAuthHandler.AuthResult serverFinal =
			IMqttServerExtendedAuthHandler.AuthResult.success(new MqttAuthProperties()
				.setAuthenticationMethod("SCRAM-SHA-256")
				.setAuthenticationData("v=server-signature".getBytes(StandardCharsets.UTF_8)));
		trace.add(new Step("client", clientFinal, null));
		trace.add(new Step("server", null, serverFinal));
		// 验证 trace 长度与流程
		assertEquals(4, trace.size());
		assertEquals(MqttAuthReasonCode.CONTINUE_AUTHENTICATION, trace.get(1).server.getReasonCode());
		assertEquals(MqttAuthReasonCode.SUCCESS, trace.get(3).server.getReasonCode());
		// Authentication Method 在 4 个步骤中保持一致
		assertEquals("SCRAM-SHA-256", trace.get(0).client.getAuthenticationMethod());
		assertEquals("SCRAM-SHA-256", trace.get(3).server.getProperties().getAuthenticationMethod());
	}

	// ----------------- 业务方 handler 实现示例 -----------------

	@Test
	void businessHandlerImplementsContract() {
		// 业务方实现 IMqttServerExtendedAuthHandler 做简单回显（生产环境应做真正的 challenge/response）
		IMqttServerExtendedAuthHandler echoHandler = (context, clientId, reasonCode, properties) -> {
			// 简单回显：method + reason code 透传
			MqttAuthProperties outProps = new MqttAuthProperties()
				.setAuthenticationMethod(properties.getAuthenticationMethod())
				.setAuthenticationData(properties.getAuthenticationData());
			return reasonCode == MqttAuthReasonCode.CONTINUE_AUTHENTICATION
				? IMqttServerExtendedAuthHandler.AuthResult.continueAuth(outProps)
				: IMqttServerExtendedAuthHandler.AuthResult.success(outProps);
		};
		// 调用 handler
		MqttAuthProperties in = new MqttAuthProperties()
			.setAuthenticationMethod("MOCK")
			.setAuthenticationData("hello".getBytes(StandardCharsets.UTF_8));
		IMqttServerExtendedAuthHandler.AuthResult outContinue = echoHandler.onAuth(
			null, "client1", MqttAuthReasonCode.CONTINUE_AUTHENTICATION, in);
		assertEquals(MqttAuthReasonCode.CONTINUE_AUTHENTICATION, outContinue.getReasonCode());
		assertEquals("MOCK", outContinue.getProperties().getAuthenticationMethod());
		IMqttServerExtendedAuthHandler.AuthResult outSuccess = echoHandler.onAuth(
			null, "client1", MqttAuthReasonCode.SUCCESS, in);
		assertEquals(MqttAuthReasonCode.SUCCESS, outSuccess.getReasonCode());
	}

	// ----------------- helper -----------------

	private static class Step {
		final String actor;
		final MqttAuthProperties client;
		final IMqttServerExtendedAuthHandler.AuthResult server;

		Step(String actor, MqttAuthProperties client, IMqttServerExtendedAuthHandler.AuthResult server) {
			this.actor = actor;
			this.client = client;
			this.server = server;
		}
	}
}
