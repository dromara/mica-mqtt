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

package org.dromara.mica.mqtt.core.server.pipeline;

/**
 * 标记接口：真正执行消息投递的处理器（DOWN_STREAM / UP_STREAM / HTTP_API）。
 * <p>
 * 消息管线的返回值需要区分「投递是否成功」和「其他后置处理器是否报错」：
 * 遗嘱投递会依据返回值决定是否重试和清除遗嘱，若把后置 handler 的异常也算作
 * 投递失败，会造成遗嘱重复发布；反之，前置 handler 主动中断导致并未投递时，
 * 又会被误判为成功而清除遗嘱。
 * </p>
 *
 * @author L.cm
 */
public interface MqttMessageDeliveryHandler extends MqttMessageHandler {
}
