/* Copyright (c) 2022 Peigen.info. All rights reserved. */

package org.dromara.mica.mqtt.client.solon.integration;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.mica.mqtt.client.solon.MqttClientSubscribe;
import org.dromara.mica.mqtt.client.solon.MqttClientSubscribeListener;
import org.dromara.mica.mqtt.client.solon.MqttClientTemplate;
import org.dromara.mica.mqtt.client.solon.config.MqttClientConfiguration;
import org.dromara.mica.mqtt.client.solon.config.MqttClientProperties;
import org.dromara.mica.mqtt.core.client.*;
import org.dromara.mica.mqtt.core.deserialize.MqttDeserializer;
import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.noear.solon.Solon;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.BeanWrap;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.util.ClassUtil;
import org.tio.core.ssl.SSLEngineCustomizer;
import org.tio.core.ssl.SslConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * <b>(MqttClientPluginImpl)</b>
 *
 * @author Lihai
 * @version 1.0.0
 * @since 2023/7/20
 */
@Slf4j
public class MqttClientPluginImpl implements Plugin {
	private final List<ExtractorClassTag<MqttClientSubscribe>> subscribeClassTags = new ArrayList<>();
	private final List<ExtractorMethodTag<MqttClientSubscribe>> subscribeMethodTags = new ArrayList<>();
	private AppContext context;

	@Override
	public void start(AppContext context) throws Throwable {
		this.context = context; //todo: 去掉 Solon.context() 写法，可同时兼容 2.5 之前与之后的版本 by noear,2023-09-15
		// 查找类上的 MqttClientSubscribe 注解
		context.beanBuilderAdd(MqttClientSubscribe.class, (clz, beanWrap, anno) -> {
			subscribeClassTags.add(new ExtractorClassTag<>(clz, beanWrap, anno));
		});
		// 查找方法上的 MqttClientSubscribe 注解
		context.beanExtractorAdd(MqttClientSubscribe.class, (bw, method, anno) -> {
			subscribeMethodTags.add(new ExtractorMethodTag<>(bw, method, anno));
		});
		context.lifecycle(-9, () -> {
			context.beanMake(MqttClientProperties.class);
			context.beanMake(MqttClientConfiguration.class);
			MqttClientProperties properties = context.getBean(MqttClientProperties.class);
			MqttClientCreator clientCreator = context.getBean(MqttClientCreator.class);

			// MqttClientTemplate init
			IMqttClientConnectListener clientConnectListener = context.getBean(IMqttClientConnectListener.class);
			MqttClientCustomizer customizers = context.getBean(MqttClientCustomizer.class);
			MqttClientTemplate clientTemplate = new MqttClientTemplate(clientCreator, clientConnectListener, customizers);
			BeanWrap mqttClientTemplateWrap = context.wrap(MqttClientTemplate.class, clientTemplate);
			context.putWrap(MqttClientTemplate.DEFAULT_CLIENT_TEMPLATE_BEAN, mqttClientTemplateWrap);
			context.putWrap(MqttClientTemplate.class, mqttClientTemplateWrap);

			// ssl 自定义配置
			SslConfig sslConfig = clientCreator.getSslConfig();
			if (sslConfig != null) {
				SSLEngineCustomizer sslCustomizer = context.getBean(SSLEngineCustomizer.class);
				if (sslCustomizer != null) {
					sslConfig.setSslEngineCustomizer(sslCustomizer);
				}
			}

			// 客户端 session
			IMqttClientSession clientSession = context.getBean(IMqttClientSession.class);
			clientCreator.clientSession(clientSession);

			// 添加启动时的临时订阅
			subscribeDetector();

			// connect
			if (properties.isEnabled()) {
				clientTemplate.connect();
			}
		});
	}

	private void subscribeDetector() {
		// 类级别的注解订阅
		subscribeClassTags.forEach(each -> {
			MqttClientSubscribe anno = each.getAnno();
			MqttClientTemplate clientTemplate = getMqttClientTemplate(anno);
			String[] topicFilters = getTopicFilters(anno);
			IMqttClientMessageListener clientMessageListener = each.getBeanWrap().get();
			clientTemplate.addSubscriptionList(topicFilters, anno.qos(), clientMessageListener);
		});
		// 方法级别的注解订阅
		subscribeMethodTags.forEach(each -> {
			MqttClientSubscribe anno = each.getAnno();
			MqttClientTemplate clientTemplate = getMqttClientTemplate(anno);
			String[] topicFilters = getTopicFilters(anno);
			// 自定义的反序列化，支持 solon bean 或者 无参构造器初始化
			Class<? extends MqttDeserializer> deserialized = anno.deserialize();
			MqttDeserializer deserializer = getMqttDeserializer(deserialized);
			// 构造监听器
			MqttClientSubscribeListener listener = new MqttClientSubscribeListener(each.getBw().get(), each.getMethod(), deserializer);
			clientTemplate.addSubscriptionList(topicFilters, anno.qos(), listener);
		});
	}

	@Override
	public void stop() throws Throwable {
		MqttClientTemplate clientTemplate = context.getBean(MqttClientTemplate.class);
		clientTemplate.destroy();
		log.info("mqtt client stop...");
	}

	private MqttClientTemplate getMqttClientTemplate(MqttClientSubscribe anno) {
		return context.getBean(anno.clientTemplateBean());
	}

	/**
	 * 获取解码器
	 *
	 * @param deserializerType deserializerType
	 * @return 解码器
	 */
	private MqttDeserializer getMqttDeserializer(Class<?> deserializerType) {
		BeanWrap beanWrap = context.getWrap(deserializerType);
		if (beanWrap == null) {
			return ClassUtil.newInstance(deserializerType);
		}
		return beanWrap.get();
	}

	private String[] getTopicFilters(MqttClientSubscribe anno) {
		// 1. 替换 solon cfg 变量
		// 2. 替换订阅中的其他变量
		return Arrays.stream(anno.value())
			.map((x) -> Optional.ofNullable(Solon.cfg().getByTmpl(x)).orElse(x))
			.map(TopicUtil::getTopicFilter)
			.toArray(String[]::new);
	}

	@Data
	@RequiredArgsConstructor
	private static class ExtractorClassTag<T> {
		private final Class<?> clz;
		private final BeanWrap beanWrap;
		private final T anno;
	}

	@Data
	@RequiredArgsConstructor
	private static class ExtractorMethodTag<T> {
		private final BeanWrap bw;
		private final Method method;
		private final T anno;
	}
}
