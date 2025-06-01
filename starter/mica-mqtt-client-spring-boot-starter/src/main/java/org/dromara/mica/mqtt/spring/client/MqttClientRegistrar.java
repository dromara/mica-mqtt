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

import org.dromara.mica.mqtt.spring.client.annotation.EnableMqttClients;
import org.dromara.mica.mqtt.spring.client.annotation.MqttClient;
import org.dromara.mica.mqtt.spring.client.config.MqttClientConfiguration;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.Map;
import java.util.Set;

/**
 * @author ChangJin Wei (魏昌进)
 */
@AutoConfigureAfter(MqttClientConfiguration.class)
public class MqttClientRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attrs = importingClassMetadata.getAnnotationAttributes(EnableMqttClients.class.getName());
        String[] basePackages = (String[]) attrs.get("basePackages");

        if (basePackages == null || basePackages.length == 0) {
            basePackages = new String[]{ClassUtils.getPackageName(importingClassMetadata.getClassName())};
        }

        for (String basePackage : basePackages) {
            scanAndRegisterClients(basePackage, registry);
        }
    }

    private void scanAndRegisterClients(String basePackage, BeanDefinitionRegistry registry) {
        ClassPathScanningCandidateComponentProvider scanner = getScanner();
        scanner.addIncludeFilter(new AnnotationTypeFilter(MqttClient.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
        for (BeanDefinition candidate : candidates) {
            try {
                String className = candidate.getBeanClassName();
                Class<?> interfaceClass = Class.forName(className);

                MqttClient mqttClientAnnotation = AnnotationUtils.findAnnotation(interfaceClass, MqttClient.class);
                if (mqttClientAnnotation == null) {
                    continue;
                }

                String mqttClientTemplateBeanName = mqttClientAnnotation.clientBean();

                // 构造 FactoryBean，注入接口和客户端 Bean 名称
                BeanDefinitionBuilder builder = BeanDefinitionBuilder
                        .genericBeanDefinition(MqttClientFactoryBean.class);
                builder.addConstructorArgValue(interfaceClass);
                builder.addConstructorArgValue(mqttClientTemplateBeanName);

                // 注册为 Spring Bean，使用接口类名作为 beanName
                String beanName = ClassUtils.getShortNameAsProperty(interfaceClass);
                registry.registerBeanDefinition(beanName, builder.getBeanDefinition());

            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Failed to load MQTT client interface", e);
            }
        }
    }

    private ClassPathScanningCandidateComponentProvider getScanner() {
        return new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true; // 允许接口作为候选组件
            }
        };
    }
}
