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

package org.dromara.mica.mqtt.huawei;

import org.tio.utils.mica.DigestUtils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 华为云 mqtt 签名方式
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

	public MqttSign(String deviceId, String deviceSecret) {
		Objects.requireNonNull(deviceId, "deviceId is null");
		Objects.requireNonNull(deviceSecret, "deviceSecret is null");
		this.username = deviceId;
		String timestamp = getTimeStamp();
		this.password = getPassword(deviceSecret, timestamp);
		this.clientId = getClientId(deviceId, timestamp);
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

	private static String getPassword(String deviceSecret, String timestamp) {
		return hmacSha256(deviceSecret, timestamp);
	}

	private static String getClientId(String deviceId, String timestamp) {
		return deviceId + "_0_0_" + timestamp;
	}

	/***
	 * 要求：10位数字
	 */
	private static String getTimeStamp() {
		return ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
	}

	/***
	 * 调用sha256算法进行哈希
	 */
	private static String hmacSha256(String message, String tStamp) {
		return DigestUtils.hmacSha256Hex(message, tStamp);
	}

}
