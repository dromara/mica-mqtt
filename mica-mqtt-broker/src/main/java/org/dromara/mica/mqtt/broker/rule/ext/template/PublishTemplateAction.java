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

import org.dromara.mica.mqtt.broker.rule.RuleContext;
import org.dromara.mica.mqtt.broker.rule.action.Action;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;

/**
 * publish 模板：把消息重发到 broker 内另一 topic。
 *
 * <p>YAML 配置：
 * <pre>
 * - type: publish
 *   name: reformat
 *   props:
 *     topic: "v2/{topicSegments[2]}"
 *     qos: 1
 *     retain: false
 * </pre>
 *
 * <p>{@link PublishFunction} 由装配流程通过 {@link TemplateServices} 注入，无需用户手动设置。
 *
 * @author L.cm
 */
public class PublishTemplateAction implements Action {

	private final ActionRef ref;
	private final PublishFunction publisher;
	private final String topicTemplate;
	private final Integer qosProp;
	private final boolean retain;

	public PublishTemplateAction(ActionRef ref, PublishFunction publisher) {
		this.ref = ref;
		this.publisher = publisher;
		this.topicTemplate = requireTopic(ref);
		this.qosProp = ref.getInt("qos");
		this.retain = Boolean.TRUE.equals(ref.getBoolean("retain"));
	}

	private static String requireTopic(ActionRef ref) {
		String topic = ref.getString("topic");
		if (topic == null || topic.isEmpty()) {
			throw new IllegalArgumentException("publish action requires 'topic' prop");
		}
		return topic;
	}

	@Override
	public String getName() {
		return ref.getName();
	}

	@Override
	public void send(RuleContext ctx) {
		if (publisher == null) {
			throw new IllegalStateException("publish action is not wired with a PublishFunction");
		}
		int qos = qosProp != null ? qosProp : (ctx.getQos() == null ? 0 : ctx.getQos().value());
		publisher.publish(TemplateRenderer.render(topicTemplate, ctx), ctx.getPayload(), qos, retain);
	}

	@FunctionalInterface
	public interface PublishFunction {
		void publish(String topic, byte[] payload, int qos, boolean retain);
	}

	/**
	 * ActionFactory：注册 type=publish。
	 */
	public static class Factory implements TemplateActionFactory {
		private volatile TemplateServices services;

		@Override
		public String getType() {
			return "publish";
		}

		@Override
		public void setTemplateServices(TemplateServices services) {
			this.services = services;
		}

		@Override
		public Action create(ActionRef ref) {
			TemplateServices current = services;
			return new PublishTemplateAction(ref, current == null ? null : current.getPublisher());
		}
	}
}
