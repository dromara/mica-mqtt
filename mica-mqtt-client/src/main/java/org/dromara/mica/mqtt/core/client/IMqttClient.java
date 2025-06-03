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

import java.lang.reflect.Proxy;

/**
 * @author ChangJin Wei (魏昌进)
 */
public interface IMqttClient {

    MqttClient getMqttClient();

    /**
     * 增加一个代理接口方法
     *
     * @param clientClass 被代理接口
     * @param <T> 代理接口的类型
     * @return 代理对象
     */
    default <T> T getInterface(Class<T> clientClass) {
        return (T) Proxy.newProxyInstance(
                clientClass.getClassLoader(),
                new Class<?>[]{clientClass},
                new MqttInvocationHandler(this, clientClass)
        );
    }
}
