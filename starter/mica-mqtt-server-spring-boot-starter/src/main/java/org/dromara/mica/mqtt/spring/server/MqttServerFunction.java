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

package org.dromara.mica.mqtt.spring.server;

import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.deserialize.MqttJsonDeserializer;

import java.lang.annotation.*;

/**
 * 服务端函数
 *
 * @author L.cm
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MqttServerFunction {

	/**
	 * 订阅的 topic filter
	 *
	 * @return topic filter
	 */
	String[] value();

	/**
	 * mqtt 消息反序列化
	 *
	 * @return 反序列化
	 */
	Class<? extends MqttDeserializer> deserialize() default MqttJsonDeserializer.class;

}
