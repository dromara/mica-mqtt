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

package org.dromara.mica.mqtt.broker.rule.sink;

/**
 * Sink 工厂：通过 type 字符串 + 配置 Map 物化 Sink 实例。
 * <p>
 * 注册方式：在 {@code META-INF/services/org.dromara.mica.mqtt.broker.rule.sink.SinkFactory}
 * 中列出实现类全名，broker 启动时由 JDK {@link java.util.ServiceLoader} 加载。
 * </p>
 *
 * @author L.cm
 */
public interface SinkFactory {

	/**
	 * @return sink 类型标识
	 */
	String getType();

	/**
	 * 由 SinkRegistry 调用，物化 Sink 实例。
	 *
	 * @param ref sink 引用
	 * @return sink 实例
	 * @throws Exception 物化失败
	 */
	Sink create(SinkRef ref) throws Exception;
}
