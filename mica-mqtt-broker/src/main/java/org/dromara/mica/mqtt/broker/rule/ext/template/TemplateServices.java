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

package org.dromara.mica.mqtt.broker.rule.ext.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocketFactory;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模板 action（{@code publish} / {@code store} / {@code alert} / {@code webhook}）运行期依赖。
 * <p>
 * 由 broker 装配流程在 {@code MqttServer} 构建完成后创建，注入到各个
 * {@link TemplateActionFactory}；broker 关闭时调用 {@link #close()} 释放告警中心线程池。
 * </p>
 *
 * @author L.cm
 */
public final class TemplateServices implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(TemplateServices.class);

	/**
	 * 内置内存 store 的注册名。
	 */
	public static final String MEMORY_STORE = "memory";

	private final PublishTemplateAction.PublishFunction publisher;
	private final Map<String, StoreTemplateAction.StoreFunction> stores;
	private final AlertCenter alertCenter;
	private final SSLSocketFactory sslSocketFactory;

	/**
	 * 构造。
	 *
	 * @param publisher        发布函数，用于 {@code publish} 模板
	 * @param stores           store 函数注册表（按 {@code storage} 属性名索引）；为空时自动注册内存实现
	 * @param alertCenter      告警中心，用于 {@code alert} 模板；为 {@code null} 时禁用 alert
	 */
	public TemplateServices(PublishTemplateAction.PublishFunction publisher,
							Map<String, StoreTemplateAction.StoreFunction> stores,
							AlertCenter alertCenter) {
		this(publisher, stores, alertCenter, null);
	}

	/**
	 * 构造。
	 *
	 * @param publisher        发布函数，用于 {@code publish} 模板
	 * @param stores           store 函数注册表（按 {@code storage} 属性名索引）；为空时自动注册内存实现
	 * @param alertCenter      告警中心，用于 {@code alert} 模板；为 {@code null} 时禁用 alert
	 * @param sslSocketFactory 自定义 TLS 工厂（如自签名证书场景）；为 {@code null} 时使用 JDK 默认信任链
	 */
	public TemplateServices(PublishTemplateAction.PublishFunction publisher,
							Map<String, StoreTemplateAction.StoreFunction> stores,
							AlertCenter alertCenter,
							SSLSocketFactory sslSocketFactory) {
		this.publisher = publisher;
		Map<String, StoreTemplateAction.StoreFunction> copy = new LinkedHashMap<>();
		if (stores != null) {
			copy.putAll(stores);
		}
		copy.putIfAbsent(MEMORY_STORE, new StoreTemplateAction.MemoryStoreFunction());
		this.stores = Collections.unmodifiableMap(copy);
		this.alertCenter = alertCenter;
		this.sslSocketFactory = sslSocketFactory;
	}

	/**
	 * 创建默认依赖：发布走 broker 广播、store 使用内存实现、启用告警中心。
	 *
	 * @param publisher 发布函数
	 * @return 默认依赖集合
	 */
	public static TemplateServices defaults(PublishTemplateAction.PublishFunction publisher) {
		return new TemplateServices(publisher, null, new AlertCenter());
	}

	public PublishTemplateAction.PublishFunction getPublisher() {
		return publisher;
	}

	/**
	 * 按 {@code storage} 属性取 store 函数，未注册时回退到内存实现。
	 *
	 * @param type store 类型名；{@code null} 等价于 {@link #MEMORY_STORE}
	 * @return store 函数，永不为 {@code null}
	 */
	public StoreTemplateAction.StoreFunction getStore(String type) {
		StoreTemplateAction.StoreFunction fn = type == null ? null : stores.get(type);
		return fn != null ? fn : stores.get(MEMORY_STORE);
	}

	public AlertCenter getAlertCenter() {
		return alertCenter;
	}

	public SSLSocketFactory getSslSocketFactory() {
		return sslSocketFactory;
	}

	@Override
	public void close() {
		if (alertCenter == null) {
			return;
		}
		try {
			alertCenter.shutdown();
		} catch (Exception e) {
			logger.warn("Failed to shutdown alert center", e);
		}
	}
}
