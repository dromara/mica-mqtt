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

package org.dromara.mica.mqtt.broker.rule.ext.template;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 告警事件。
 *
 * @author L.cm
 */
public class AlertEvent {

	private final long ts;
	private final String severity;
	private final String title;
	private final String message;
	private final List<String> tags;
	private final String dedupeKey;
	private final Map<String, Object> extra;

	public AlertEvent(long ts,
					  String severity,
					  String title,
					  String message,
					  List<String> tags,
					  String dedupeKey,
					  Map<String, Object> extra) {
		this.ts = ts;
		this.severity = severity;
		this.title = title;
		this.message = message;
		this.tags = tags == null ? Collections.emptyList() : tags;
		this.dedupeKey = dedupeKey;
		this.extra = extra == null ? Collections.emptyMap() : extra;
	}

	public long getTs() { return ts; }
	public String getSeverity() { return severity; }
	public String getTitle() { return title; }
	public String getMessage() { return message; }
	public List<String> getTags() { return tags; }
	public String getDedupeKey() { return dedupeKey; }
	public Map<String, Object> getExtra() { return extra; }
}