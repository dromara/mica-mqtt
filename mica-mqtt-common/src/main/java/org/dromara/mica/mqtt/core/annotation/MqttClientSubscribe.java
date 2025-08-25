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

import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.deserialize.MqttJsonDeserializer;

import java.lang.annotation.*;

/**
 * 客户端订阅注解
 *
 * @author L.cm
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MqttClientSubscribe {

	/**
	 * 默认的客户端模板 bean 名称
	 */
	String DEFAULT_CLIENT_TEMPLATE_BEAN = "mqttClientTemplate";

	/**
	 * 订阅的 topic filter
	 *
	 * @return topic filter
	 */
	String[] value();

	/**
	 * 订阅的 qos
	 *
	 * @return MqttQoS
	 */
	MqttQoS qos() default MqttQoS.QOS0;

	/**
	 * mqtt 消息反序列化
	 *
	 * @return 反序列化
	 */
	Class<? extends MqttDeserializer> deserialize() default MqttJsonDeserializer.class;

	/**
	 * 客户端 bean 名称
	 *
	 * @return bean name
	 */
	String clientTemplateBean() default MqttClientSubscribe.DEFAULT_CLIENT_TEMPLATE_BEAN;

}
