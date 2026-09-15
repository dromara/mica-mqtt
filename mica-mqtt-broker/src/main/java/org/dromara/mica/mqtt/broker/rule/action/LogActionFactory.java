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

/**
 * {@link LogAction} 工厂。
 *
 * @author L.cm
 */
public class LogActionFactory implements ActionFactory {

	public static final String TYPE = "log";

	@Override
	public String getType() {
		return TYPE;
	}

	@Override
	public Action create(ActionRef ref) {
		String name = ref.getName();
		Object level = ref.getProps().get("level");
		return new LogAction(name, level == null ? null : level.toString());
	}
}
