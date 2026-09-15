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

package org.dromara.mica.mqtt.broker.rule.action;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link HttpAction} 工厂。
 *
 * @author L.cm
 */
public class HttpActionFactory implements ActionFactory {

	public static final String TYPE = "http";

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public Action create(ActionRef ref) {
		String name = ref.getName();
		String url = MqttAction.stringProp(ref, "url");
		if (url == null || url.isEmpty()) {
			throw new IllegalArgumentException("HttpAction requires 'url' prop");
		}
		String method = MqttAction.stringProp(ref, "method");
		String contentType = MqttAction.stringProp(ref, "contentType");
		int timeoutMs = MqttAction.intProp(ref, "timeoutMs", 3000);

		Map<String, String> headers = null;
		Object h = ref.getProps().get("headers");
		if (h instanceof Map) {
			Map<?, ?> raw = (Map<?, ?>) h;
			headers = new HashMap<>(raw.size());
			for (Map.Entry<?, ?> e : raw.entrySet()) {
				if (e.getKey() != null) {
					headers.put(e.getKey().toString(),
						e.getValue() == null ? "" : e.getValue().toString());
				}
			}
		}
		return new HttpAction(name, url, method, contentType, timeoutMs, headers);
	}
}
