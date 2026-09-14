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

package org.dromara.mica.mqtt.broker.rule.sink;

import net.dreamlu.mica.net.utils.hutool.StrUtil;
import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * 内置 LogSink：打印 clientId / topic / qos / payload 到 SLF4J。
 *
 * @author L.cm
 */
public class LogSink implements Sink {

	private static final Logger logger = LoggerFactory.getLogger(LogSink.class);

	private final String name;
	private final String level;

	public LogSink(String name, String level) {
		this.name = name == null || name.isEmpty() ? "log" : name;
		this.level = level == null || level.isEmpty() ? "info" : level.toLowerCase();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void send(RuleContext ctx) {
		String payload = previewPayload(ctx);
		String format = "rule {} clientId={} topic={} qos={} retain={} payload={}";
		switch (level) {
			case "debug":
				logger.debug(format, ctx.getRule().getId(),
					ctx.getClientId(), ctx.getTopic(),
					ctx.getQos(), ctx.isRetain() ? 1 : 0, payload);
				break;
			case "warn":
				logger.warn(format, ctx.getRule().getId(),
					ctx.getClientId(), ctx.getTopic(),
					ctx.getQos(), ctx.isRetain() ? 1 : 0, payload);
				break;
			case "error":
				logger.error(format, ctx.getRule().getId(),
					ctx.getClientId(), ctx.getTopic(),
					ctx.getQos(), ctx.isRetain() ? 1 : 0, payload);
				break;
			default:
				logger.info(format, ctx.getRule().getId(),
					ctx.getClientId(), ctx.getTopic(),
					ctx.getQos(), ctx.isRetain() ? 1 : 0, payload);
				break;
		}
	}

	private static String previewPayload(RuleContext ctx) {
		byte[] payload = ctx.getPayload();
		if (payload == null || payload.length == 0) {
			return "";
		}
		int preview = Math.min(payload.length, 256);
		String text = new String(payload, 0, preview, StandardCharsets.UTF_8);
		if (payload.length > 256) {
			text = text + "...(truncated)";
		}
		return StrUtil.replace(text, "\n", "\\n");
	}
}
