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

import org.dromara.mica.mqtt.core.annotation.MqttServerFunction;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.LazyInitializationExcludeFilter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * mqtt 服务端函数延迟加载排除
 *
 * @author L.cm
 */
public class MqttServerFunctionLazyFilter implements LazyInitializationExcludeFilter {

	@Override
	public boolean isExcluded(String beanName, BeanDefinition beanDefinition, Class<?> beanType) {
		// 类上有注解的情况
		MqttServerFunction subscribe = AnnotationUtils.findAnnotation(beanType, MqttServerFunction.class);
		if (subscribe != null) {
			return true;
		}
		// 方法上的注解
		List<Method> methodList = new ArrayList<>();
		ReflectionUtils.doWithMethods(beanType, method -> {
			MqttServerFunction clientSubscribe = AnnotationUtils.findAnnotation(method, MqttServerFunction.class);
			if (clientSubscribe != null) {
				methodList.add(method);
			}
		}, ReflectionUtils.USER_DECLARED_METHODS);
		return !methodList.isEmpty();
	}

}
