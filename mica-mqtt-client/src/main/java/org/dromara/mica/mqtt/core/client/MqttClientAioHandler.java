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

import org.dromara.mica.mqtt.codec.*;
import org.tio.client.intf.TioClientHandler;
import org.tio.core.ChannelContext;
import org.tio.core.TioConfig;
import org.tio.core.exception.TioDecodeException;
import org.tio.core.intf.Packet;
import org.tio.utils.buffer.ByteBufferAllocator;

import java.nio.ByteBuffer;

/**
 * mqtt 客户端处理
 *
 * @author L.cm
 */
public class MqttClientAioHandler implements TioClientHandler {
	private final MqttDecoder mqttDecoder;
	private final MqttEncoder mqttEncoder;
	private final ByteBufferAllocator allocator;
	private final IMqttClientProcessor processor;

	public MqttClientAioHandler(MqttClientCreator mqttClientCreator,
								IMqttClientProcessor processor) {
		this.mqttDecoder = new MqttDecoder(mqttClientCreator.getMaxBytesInMessage(), mqttClientCreator.getMaxClientIdLength());
		this.mqttEncoder = MqttEncoder.INSTANCE;
		this.allocator = mqttClientCreator.getBufferAllocator();
		this.processor = processor;
	}

	@Override
	public Packet heartbeatPacket(ChannelContext channelContext) {
		return MqttPacket.MQTT_PING_REQ;
	}

	@Override
	public Packet decode(ByteBuffer buffer, int limit, int position, int readableLength, ChannelContext context) throws TioDecodeException {
		return mqttDecoder.doDecode(context, buffer, readableLength);
	}

	@Override
	public ByteBuffer encode(Packet packet, TioConfig tioConfig, ChannelContext channelContext) {
		return mqttEncoder.doEncode(channelContext, ((MqttPacket) packet).getMqttMessage(), allocator);
	}

	@Override
	public void handler(Packet packet, ChannelContext context) {
		MqttMessage message = ((MqttPacket) packet).getMqttMessage();
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
				processor.processUnSubAck((MqttUnsubAckMessage) message);
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
