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

package org.dromara.mica.mqtt.core.deserialize;


import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * mqtt 消息反序列化
 *
 * @author L.cm
 * @author ChangJin Wei(魏昌进)
 */
public class MqttJsonDeserializer implements MqttDeserializer {

	private final ObjectMapper objectMapper;

    public MqttJsonDeserializer() {
		this(new ObjectMapper());
    }

	public MqttJsonDeserializer(ObjectMapper objectMapper) {
		this.objectMapper = new ObjectMapper();
	}

    @Override
	public <T> T deserialize(byte[] bytes, Type type) {
		JavaType javaType = objectMapper.getTypeFactory().constructType(type);
        try {
            return objectMapper.readValue(bytes, javaType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
