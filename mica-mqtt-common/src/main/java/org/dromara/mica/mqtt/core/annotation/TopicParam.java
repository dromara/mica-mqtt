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

package org.dromara.mica.mqtt.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 {@link MqttClientPublish} 接口方法参数上，用于把该参数暴露为
 * topic 模板占位符 <code>${value}</code> 的数据源。
 *
 * <p>示例：
 * <pre><code>
 * &#64;MqttClient
 * public interface DoorClient {
 *
 *     &#64;MqttClientPublish("/sys/${productKey}/${deviceId}/thing/sub")
 *     void sendMessage(&#64;TopicParam("productKey") String productKey,
 *                      &#64;TopicParam("deviceId") String deviceId,
 *                      &#64;MqttPayload String message);
 * }
 * </code></pre>
 *
 * <p>占位符解析顺序：先按 {@link TopicParam} 显式声明的 name 取值，
 * 缺失时回退到 {@link MqttPayload} 标注的 bean / Map 字段；
 * 仍缺失时原样保留 <code>${name}</code> 以便定位解析失败。
 *
 * @author L.cm
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface TopicParam {

	/**
	 * 占位符名称，对应 topic 模板中的 <code>${value}</code>。
	 *
	 * @return 变量名
	 */
	String value();
}
