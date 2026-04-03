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

import net.dreamlu.mica.net.client.intf.TioClientHandler;
import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.TioConfig;
import net.dreamlu.mica.net.core.exception.TioDecodeException;
import net.dreamlu.mica.net.core.intf.Packet;
import org.dromara.mica.mqtt.codec.MqttDecoder;
import org.dromara.mica.mqtt.codec.MqttEncoder;
import org.dromara.mica.mqtt.codec.MqttMessageType;
import org.dromara.mica.mqtt.codec.message.*;
import org.dromara.mica.mqtt.codec.message.header.MqttFixedHeader;

import java.nio.ByteBuffer;

/**
 * mqtt 客户端处理
 *
 * @author L.cm
 */
public class MqttClientAioHandler implements TioClientHandler {
	private final MqttDecoder mqttDecoder;
	private final MqttEncoder mqttEncoder;
	private final IMqttClientProcessor processor;

	public MqttClientAioHandler(MqttClientCreator mqttClientCreator,
								IMqttClientProcessor processor) {
		this.mqttDecoder = new MqttDecoder(mqttClientCreator.getMaxBytesInMessage(), mqttClientCreator.getMaxClientIdLength());
		this.mqttEncoder = MqttEncoder.INSTANCE;
		this.processor = processor;
	}

	@Override
	public Packet heartbeatPacket(ChannelContext channelContext) {
		return MqttMessage.PINGREQ;
	}

	@Override
	public Packet decode(ByteBuffer buffer, int limit, int position, int readableLength, ChannelContext context) throws TioDecodeException {
		return mqttDecoder.doDecode(context, buffer, readableLength);
	}

	@Override
	public ByteBuffer encode(Packet packet, TioConfig tioConfig, ChannelContext channelContext) {
		return mqttEncoder.doEncode(channelContext, (MqttMessage) packet);
	}

	@Override
	public void handler(Packet packet, ChannelContext context) {
		MqttMessage message = (MqttMessage) packet;
		MqttFixedHeader fixedHeader = message.fixedHeader();
		// 根据消息类型处理消息
		MqttMessageType messageType = fixedHeader.messageType();
		switch (messageType) {
			case CONNACK:
				processor.processConAck(context, (MqttConnAckMessage) message);
				break;
			case SUBACK:
				processor.processSubAck(context, (MqttSubAckMessage) message);
				break;
			case PUBLISH:
				processor.processPublish(context, (MqttPublishMessage) message);
				break;
			case UNSUBACK:
				processor.processUnSubAck((MqttUnSubAckMessage) message);
				break;
			case PUBACK:
				processor.processPubAck((MqttPubAckMessage) message);
				break;
			case PUBREC:
				processor.processPubRec(context, message);
				break;
			case PUBREL:
				processor.processPubRel(context, message);
				break;
			case PUBCOMP:
				processor.processPubComp(message);
				break;
			default:
				break;
		}
	}

}
