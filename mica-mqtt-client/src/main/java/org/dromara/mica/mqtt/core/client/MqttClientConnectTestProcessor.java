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

package org.dromara.mica.mqtt.core.client;

import org.dromara.mica.mqtt.codec.codes.MqttConnectReasonCode;
import org.dromara.mica.mqtt.codec.message.*;
import org.dromara.mica.mqtt.codec.message.header.MqttConnAckVariableHeader;
import org.tio.core.ChannelContext;
import org.tio.core.Tio;

import java.util.concurrent.CompletableFuture;

/**
 * 默认的 mqtt 消息处理器
 *
 * @author L.cm
 */
public class MqttClientConnectTestProcessor implements IMqttClientProcessor {
	private final CompletableFuture<MqttConnectReasonCode> future;

	public MqttClientConnectTestProcessor(CompletableFuture<MqttConnectReasonCode> future) {
		this.future = future;
	}

	@Override
	public void processConAck(ChannelContext context, MqttConnAckMessage message) {
		MqttConnAckVariableHeader connAckVariableHeader = message.variableHeader();
		Tio.remove(context, "mqtt connect tested.");
		future.complete(connAckVariableHeader.connectReturnCode());
	}

	@Override
	public void processSubAck(ChannelContext context, MqttSubAckMessage message) {

	}

	@Override
	public void processPublish(ChannelContext context, MqttPublishMessage message) {

	}

	@Override
	public void processUnSubAck(MqttUnsubAckMessage message) {

	}

	@Override
	public void processPubAck(MqttPubAckMessage message) {

	}

	@Override
	public void processPubRec(ChannelContext context, MqttMessage message) {

	}

	@Override
	public void processPubRel(ChannelContext context, MqttMessage message) {

	}

	@Override
	public void processPubComp(MqttMessage message) {

	}

}
