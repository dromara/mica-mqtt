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

package org.dromara.mica.mqtt.broker.config;

import net.dreamlu.mica.redis.cache.MicaRedisCache;
import net.dreamlu.mica.redis.stream.RStreamTemplate;
import org.dromara.mica.mqtt.broker.cluster.*;
import org.dromara.mica.mqtt.broker.enums.RedisKeys;
import org.dromara.mica.mqtt.broker.listener.RedisMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.MqttServer;
import org.dromara.mica.mqtt.core.server.dispatcher.IMqttMessageDispatcher;
import org.dromara.mica.mqtt.core.server.event.IMqttConnectStatusListener;
import org.dromara.mica.mqtt.core.server.serializer.IMessageSerializer;
import org.dromara.mica.mqtt.core.server.serializer.JsonMessageSerializer;
import org.dromara.mica.mqtt.core.server.store.IMqttMessageStore;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * mica mqtt broker 配置
 *
 * @author L.cm
 */
@Configuration(proxyBeanMethods = false)
public class MqttBrokerConfiguration {

	@Bean
	public IMessageSerializer messageSerializer() {
		return new JsonMessageSerializer();
	}

	@Bean
	public IMqttConnectStatusListener mqttBrokerConnectListener(ApplicationContext context,
																MicaRedisCache redisCache) {
		return new RedisMqttConnectStatusListener(context, redisCache);
	}

	@Bean
	public IMqttMessageStore mqttMessageStore(MicaRedisCache redisCache,
											  IMessageSerializer messageSerializer) {
		return new RedisMqttMessageStore(redisCache, messageSerializer);
	}

	@Bean
	public RedisMqttMessageExchangeReceiver mqttMessageUpReceiver(IMessageSerializer messageSerializer,
																  MqttServer mqttServer) {
		return new RedisMqttMessageExchangeReceiver(messageSerializer, mqttServer);
	}

	@Bean
	public RedisMqttMessageDownReceiver mqttMessageDownReceiver(IMessageSerializer messageSerializer,
																MqttServer mqttServer) {
		return new RedisMqttMessageDownReceiver(messageSerializer, mqttServer);
	}

	@Bean
	public IMqttMessageDispatcher mqttMessageDispatcher(ApplicationContext context,
														RStreamTemplate streamTemplate,
														IMessageSerializer messageSerializer) {
		return new RedisMqttMessageDispatcher(context, streamTemplate, messageSerializer, RedisKeys.REDIS_CHANNEL_EXCHANGE.getKey());
	}

	@Bean
	public RedisMqttServerManage mqttServerManage(MicaRedisCache redisCache,
												  MqttServer mqttServer) {
		return new RedisMqttServerManage(redisCache, mqttServer);
	}

}
