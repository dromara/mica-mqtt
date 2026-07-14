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

package org.dromara.mica.mqtt.core.server.auth;

import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.codec.codes.MqttAuthReasonCode;
import org.dromara.mica.mqtt.codec.message.properties.MqttAuthProperties;

/**
 * MQTT 5.0 扩展认证（Enhanced Authentication）处理（spec 3.15 / 4.12）。
 *
 * <p>与 {@link IMqttServerAuthHandler}（CONNECT 时用户名/密码基础认证）不同，本接口处理
 * AUTH 报文级别的 challenge/response 流程，例如 SCRAM、Kerberos、TLS-PSK 等。
 *
 * <p>典型调用流程：
 * <ol>
 *     <li>客户端在 CONNECT 的 properties 中携带 {@code Authentication Method}（必填）与
 *         {@code Authentication Data}（可选，初始 challenge 响应）。</li>
 *     <li>服务端可在 CONNACK 之前发送 AUTH 报文带 reason code 0x18（CONTINUE_AUTHENTICATION）继续 challenge。</li>
 *     <li>双方通过若干次 AUTH 报文 + 0x18/0x19 交互，最终以 0x00（SUCCESS）结束。</li>
 *     <li>已建立会话后，服务端可在任意时刻发起 REAUTHENTICATE（reason code 0x19）。</li>
 * </ol>
 *
 * <p>本骨架仅暴露核心流程接入点；具体认证方法（SCRAM/Kerberos/...）由业务方实现。
 *
 * @author L.cm
 */
@FunctionalInterface
public interface IMqttServerExtendedAuthHandler {

	/**
	 * 处理收到的 AUTH 报文（来自客户端）。
	 * <p>
	 * 返回结果（{@link AuthResult}）会被服务端用 AUTH 报文回发给客户端。
	 * 返回 {@code null} 表示本次不响应（业务方在需要异步或静默时使用）。
	 *
	 * @param context    ChannelContext
	 * @param clientId   客户端 ID（CONNECT 时已分配）
	 * @param reasonCode 入站 AUTH 报文的 reason code
	 * @param properties 入站 AUTH 报文的 properties（Authentication Method / Data / ...）
	 * @return {@link AuthResult} 包含响应 reason code 与 properties；返回 {@code null} 表示不响应
	 */
	AuthResult onAuth(ChannelContext context, String clientId, MqttAuthReasonCode reasonCode, MqttAuthProperties properties);

	/**
	 * AUTH 响应结果。
	 */
	class AuthResult {
		private final MqttAuthReasonCode reasonCode;
		private final MqttAuthProperties properties;

		public AuthResult(MqttAuthReasonCode reasonCode, MqttAuthProperties properties) {
			this.reasonCode = reasonCode;
			this.properties = properties;
		}

		/**
		 * 创建一个 SUCCESS 响应。
		 */
		public static AuthResult success(MqttAuthProperties properties) {
			return new AuthResult(MqttAuthReasonCode.SUCCESS, properties);
		}

		/**
		 * 创建一个 CONTINUE_AUTHENTICATION 响应。
		 */
		public static AuthResult continueAuth(MqttAuthProperties properties) {
			return new AuthResult(MqttAuthReasonCode.CONTINUE_AUTHENTICATION, properties);
		}

		public MqttAuthReasonCode getReasonCode() {
			return reasonCode;
		}

		public MqttAuthProperties getProperties() {
			return properties;
		}
	}

}
