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

package org.dromara.mica.mqtt.spring.client;


import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * @author ChangJin Wei (魏昌进)
 */
public class MqttClientFactoryBean<T> implements FactoryBean<T>, ApplicationContextAware {

    private final Class<T> interfaceClass;

    private final String mqttClientTemplateBeanName;

    private ApplicationContext applicationContext;

    public MqttClientFactoryBean(Class<T> interfaceClass, String mqttClientTemplateBeanName) {
        this.interfaceClass = interfaceClass;
        this.mqttClientTemplateBeanName = mqttClientTemplateBeanName;
    }

    @Override
    public T getObject() {
        MqttClientTemplate mqttClientTemplate =
                applicationContext.getBean(mqttClientTemplateBeanName, MqttClientTemplate.class);
        return mqttClientTemplate.getInterface(interfaceClass);
    }

    @Override
    public Class<?> getObjectType() {
        return interfaceClass;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}