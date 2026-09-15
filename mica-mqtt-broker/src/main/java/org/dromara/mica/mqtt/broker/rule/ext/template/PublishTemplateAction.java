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
import org.dromara.mica.mqtt.broker.rule.action.ActionFactory;
import org.dromara.mica.mqtt.broker.rule.action.ActionRef;

import java.util.concurrent.atomic.AtomicReference;

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
 * <p>用户须在 broker 启动前注入 {@link PublishFunction}；未注入则抛错。
 *
 * @author L.cm
 */
public class PublishTemplateAction implements Action {

	private static final AtomicReference<PublishFunction> PUBLISHER = new AtomicReference<>();

	private final ActionRef ref;

	public PublishTemplateAction(ActionRef ref) {
		this.ref = ref;
	}

	public static void setPublisher(PublishFunction fn) {
		PUBLISHER.set(fn);
	}

	public static PublishFunction getPublisher() {
		return PUBLISHER.get();
	}

	@Override
	public String getName() {
		return ref.getName();
	}

	@Override
	public void send(RuleContext ctx) throws Exception {
		PublishFunction fn = PUBLISHER.get();
		if (fn == null) {
			throw new IllegalStateException("publish template requires PublishTemplateAction.setPublisher(...) before use");
		}
		String topicTemplate = ActionRefs.getString(ref, "topic");
		if (topicTemplate == null) {
			throw new IllegalArgumentException("publish template requires 'topic' prop");
		}
		Integer qosArg = ActionRefs.getInt(ref, "qos");
		int qos = qosArg == null
			? (ctx.getQos() == null ? 0 : ctx.getQos().value())
			: qosArg;
		boolean retain = Boolean.TRUE.equals(ActionRefs.getBoolean(ref, "retain"));
		String target = TemplateRenderer.render(topicTemplate, ctx);
		fn.publish(target, ctx.getPayload(), qos, retain);
	}

	@FunctionalInterface
	public interface PublishFunction {
		void publish(String topic, byte[] payload, int qos, boolean retain);
	}

	/**
	 * ActionFactory：注册 type=publish。
	 */
	public static class Factory implements ActionFactory {
		@Override
		public String getType() {
			return "publish";
		}

		@Override
		public Action create(ActionRef ref) {
			return new PublishTemplateAction(ref);
		}
	}
}