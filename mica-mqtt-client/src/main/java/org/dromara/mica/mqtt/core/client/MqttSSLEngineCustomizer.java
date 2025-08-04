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

import org.tio.core.ssl.SSLEngineCustomizer;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.util.ArrayList;
import java.util.List;

/**
 * mqtt ssl 自定义配置
 *
 * @author L.cm
 */
public class MqttSSLEngineCustomizer implements SSLEngineCustomizer {
	/**
	 * ip 或域名
	 */
	private final String host;
	/**
	 * 端点识别算法，默认 null，生产环境建议配置成 HTTPS，支持：HTTPS/LDAPS/null
	 */
	private final String identificationAlgorithm;

	public MqttSSLEngineCustomizer(String host) {
		this(host, null);
	}

	public MqttSSLEngineCustomizer(String host, String identificationAlgorithm) {
		this.host = host;
		this.identificationAlgorithm = identificationAlgorithm;
	}

	@Override
	public void customize(SSLEngine engine) {
		// SNI 支持
		SSLParameters sslParameters = engine.getSSLParameters();
		List<SNIServerName> sniHostNames = new ArrayList<>(1);
		sniHostNames.add(new SNIHostName(host));
		sslParameters.setServerNames(sniHostNames);
		// 端点识别算法
		if (identificationAlgorithm != null) {
			sslParameters.setEndpointIdentificationAlgorithm(identificationAlgorithm);
		}
		engine.setSSLParameters(sslParameters);
	}

}
