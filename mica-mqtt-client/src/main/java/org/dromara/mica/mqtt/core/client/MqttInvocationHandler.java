/*
 * Copyright 2025-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.MqttMessageBuilders;
import org.dromara.mica.mqtt.codec.MqttProperties;
import org.dromara.mica.mqtt.codec.MqttQoS;
import org.dromara.mica.mqtt.core.annotation.MqttClientPublish;
import org.dromara.mica.mqtt.core.annotation.MqttPayload;
import org.dromara.mica.mqtt.core.annotation.MqttRetain;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * @author ChangJin Wei (魏昌进)
 */
public class MqttInvocationHandler<T extends IMqttClient> implements InvocationHandler {

    private final T mqttClient;

    private final Class<?> targetInterface;

    public MqttInvocationHandler(T mqttClient, Class<?> targetInterface) {
        this.mqttClient = mqttClient;
        this.targetInterface = targetInterface;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MqttClientPublish mqttPublish = method.getAnnotation(MqttClientPublish.class);
        if (mqttPublish == null) {
            throw new UnsupportedOperationException("Method not annotated with @MqttClientPublish");
        }

        Object payload = null;
        MqttProperties properties = null;
        boolean retain = false;
        Consumer<MqttMessageBuilders.PublishBuilder> builder = null;

        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Annotation[] annotations = paramAnnotations[i];

            for (Annotation annotation : annotations) {
                if (annotation instanceof MqttPayload) {
                    payload = arg;
                } else if (annotation instanceof MqttRetain) {
                    retain = Boolean.TRUE.equals(arg);
                }
            }

            if (payload == null) {
                if (arg instanceof MqttProperties) {
                    properties = (MqttProperties) arg;
                } else if (arg instanceof Consumer) {
                    builder = (Consumer<MqttMessageBuilders.PublishBuilder>) arg;
                }
            }
        }

        String topic = resolveTopic(mqttPublish.value(), payload);
        MqttQoS qos = mqttPublish.qos();

        if (builder == null) {
            return mqttClient.getMqttClient().publish(topic, payload, qos, retain, properties);
        } else {
            return mqttClient.getMqttClient().publish(topic, payload, qos, builder);
        }
    }

    private String resolveTopic(String topicTemplate, Object payload) {
        if (payload == null || !topicTemplate.contains("${")) {
            return topicTemplate;
        }

        String resolved = topicTemplate;
        while (resolved.contains("${")) {
            int start = resolved.indexOf("${");
            int end = resolved.indexOf("}", start);
            if (end == -1) {
                break;
            }

            String fieldName = resolved.substring(start + 2, end);
            Object value = getFieldValue(payload, fieldName);

            resolved = resolved.substring(0, start) + (value != null ? value.toString() : "") + resolved.substring(end + 1);
        }
        return resolved;
    }

    private Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to resolve field: " + fieldName + " from payload object", e);
        }
    }
}
