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

package org.dromara.mica.mqtt.core.function;

import org.dromara.mica.mqtt.codec.message.MqttPublishMessage;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.tio.core.ChannelContext;

import java.lang.reflect.Type;

/**
 * 需要序列化的对象函数
 *
 * @author L.cm
 */
public class ObjectParamValueFunction implements ParamValueFunction {
	private final MqttDeserializer deserializer;
	private final Type parameterType;

	public ObjectParamValueFunction(MqttDeserializer deserializer, Type parameterType) {
		this.deserializer = deserializer;
		this.parameterType = parameterType;
	}

	@Override
	public Object getValue(ChannelContext context, String topic, MqttPublishMessage message, byte[] payload) {
		return deserializer.deserialize(payload, parameterType);
	}
}
