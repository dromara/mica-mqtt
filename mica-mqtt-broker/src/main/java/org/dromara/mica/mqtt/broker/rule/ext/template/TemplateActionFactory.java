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

import org.dromara.mica.mqtt.broker.rule.action.ActionFactory;

/**
 * 需要外部依赖装配的模板 action 工厂。
 * <p>
 * 模板 action（{@code publish} / {@code store} / {@code alert} / {@code webhook}）依赖
 * broker 运行期对象，而 SPI 要求工厂提供无参构造，因此由装配流程在服务端构建完成后
 * 调用 {@link #setTemplateServices(TemplateServices)} 注入。
 * </p>
 *
 * @author L.cm
 */
public interface TemplateActionFactory extends ActionFactory {

	/**
	 * 注入模板 action 的外部依赖。
	 *
	 * @param services 依赖集合，非空
	 */
	void setTemplateServices(TemplateServices services);
}
