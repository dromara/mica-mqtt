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

import java.util.Map;

/**
 * 告警通知器的 SPI 工厂。
 * <p>
 * 用户实现本接口并注册到
 * {@code META-INF/services/org.dromara.mica.mqtt.broker.rule.ext.template.AlertNotifierFactory}，
 * 即可把告警推送到钉钉 / Slack / PagerDuty 等通道。
 * </p>
 *
 * @author L.cm
 */
public interface AlertNotifierFactory {

	/**
	 * @return 通知通道类型标识
	 */
	String getType();

	/**
	 * 创建通知器实例。
	 *
	 * @param props 通知器配置（URL、token 等）
	 * @return 通知器实例
	 */
	AlertNotifier create(Map<String, String> props);
}
