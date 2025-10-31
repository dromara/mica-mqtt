/*
 * Copyright (c) 2019-2029, Dreamlu 卢春梦 (596392912@qq.com & www.dreamlu.net).
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

package org.dromara.mica.mqtt.aliyun;

import org.tio.utils.mica.DigestUtils;

import java.util.Objects;

/**
 * 阿里云 mqtt 签名方式
 *
 * @author L.cm
 */
public class MqttSign {
	/**
	 * 用户名
	 */
	private final String username;
	/**
	 * 密码
	 */
	private final String password;
	/**
	 * 客户端id
	 */
	private final String clientId;

	public MqttSign(String productKey, String deviceName, String deviceSecret) {
		Objects.requireNonNull(productKey, "productKey is null");
		Objects.requireNonNull(deviceName, "deviceName is null");
		Objects.requireNonNull(deviceSecret, "deviceSecret is null");
		this.username = deviceName + '&' + productKey;
		String timestamp = Long.toString(System.currentTimeMillis());
		this.password = getPassword(productKey, deviceName, deviceSecret, timestamp);
		this.clientId = getClientId(productKey, deviceName, timestamp);
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

	public String getClientId() {
		return clientId;
	}

	private static String getPassword(String productKey, String deviceName, String deviceSecret, String timestamp) {
		String plainPwd = "clientId" + productKey + '.' + deviceName + "deviceName" +
			deviceName + "productKey" + productKey + "timestamp" + timestamp;
		return hmacSha256(plainPwd, deviceSecret);
	}

	private static String getClientId(String productKey, String deviceName, String timestamp) {
		return productKey + '.' + deviceName + "|timestamp=" + timestamp + ",_v=paho-java-1.0.0,securemode=2,signmethod=hmacsha256|";
	}

	private static String hmacSha256(String plainText, String key) {
		if (plainText == null || key == null) {
			return null;
		}
		return DigestUtils.hmacSha256Hex(plainText, key);
	}

}
