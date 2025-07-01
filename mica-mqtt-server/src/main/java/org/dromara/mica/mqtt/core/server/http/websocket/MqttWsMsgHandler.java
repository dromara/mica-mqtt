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

package org.dromara.mica.mqtt.core.server.http.websocket;

import org.dromara.mica.mqtt.codec.MqttMessage;
import org.dromara.mica.mqtt.core.server.MqttMessageInterceptors;
import org.dromara.mica.mqtt.core.server.MqttServerCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.ChannelContext;
import org.tio.core.TioConfig;
import org.tio.core.intf.Packet;
import org.tio.core.intf.TioHandler;
import org.tio.http.common.HttpRequest;
import org.tio.http.common.HttpResponse;
import org.tio.utils.buffer.ByteBufferUtil;
import org.tio.utils.hutool.FastByteBuffer;
import org.tio.websocket.common.WsRequest;
import org.tio.websocket.common.WsResponse;
import org.tio.websocket.server.handler.IWsMsgHandler;

import java.nio.ByteBuffer;

/**
 * mqtt websocket 消息处理
 *
 * @author L.cm
 */
public class MqttWsMsgHandler implements IWsMsgHandler {
	private static final Logger logger = LoggerFactory.getLogger(MqttWsMsgHandler.class);

	/**
	 * mqtt websocket message body key
	 */
	private static final String MQTT_WS_MSG_BODY_KEY = "MQTT_WS_MSG_BODY_KEY";
	/**
	 * websocket 握手端点
	 */
	private final String[] supportedSubProtocols;
	private final TioHandler mqttServerAioHandler;
	private final MqttMessageInterceptors messageInterceptors;

	public MqttWsMsgHandler(MqttServerCreator serverCreator, TioHandler handler) {
		this(serverCreator, new String[]{"mqtt", "mqttv3.1", "mqttv3.1.1"}, handler);
	}

	public MqttWsMsgHandler(MqttServerCreator serverCreator,
							String[] supportedSubProtocols,
							TioHandler handler) {
		this.supportedSubProtocols = supportedSubProtocols;
		this.mqttServerAioHandler = handler;
		this.messageInterceptors = serverCreator.getMessageInterceptors();
	}

	@Override
	public String[] getSupportedSubProtocols() {
		return this.supportedSubProtocols;
	}

	/**
	 * 握手后处理
	 *
	 * @param request  HttpRequest
	 * @param response HttpResponse
	 * @param context  ChannelContext
	 */
	@Override
	public void onAfterHandshaked(HttpRequest request, HttpResponse response, ChannelContext context) {
		// 在连接中添加 WriteBuffer 用来处理半包消息
		context.computeIfAbsent(MQTT_WS_MSG_BODY_KEY, key -> new FastByteBuffer());
	}

	/**
	 * 字节消息（binaryType = arraybuffer）过来后会走这个方法
	 */
	@Override
	public Object onBytes(WsRequest wsRequest, byte[] bytes, ChannelContext context) throws Exception {
		FastByteBuffer wsBody = context.get(MQTT_WS_MSG_BODY_KEY);
		ByteBuffer buffer = getMqttBody(wsBody, bytes);
		if (buffer == null) {
			return null;
		}
		// 可能会一次有多个包，所以需要进行拆包
		while (buffer.hasRemaining()) {
			// 解析 mqtt 消息
			int readableLength = buffer.remaining();
			Packet packet = mqttServerAioHandler.decode(buffer, 0, 0, readableLength, context);
			if (packet == null) {
				// 如果拆包之后还有剩余，写回到 WriteBuffer
				int remaining = buffer.remaining();
				if (remaining > 0) {
					byte[] data = new byte[remaining];
					buffer.get(data);
					wsBody.writeBytes(data);
				}
				return null;
			}
			MqttMessage mqttMessage = (MqttMessage) packet;
			// 消息解析后
			try {
				messageInterceptors.onAfterDecoded(context, mqttMessage, readableLength);
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
			// 消息处理
			mqttServerAioHandler.handler(packet, context);
			// 消息处理后
			try {
				messageInterceptors.onAfterHandled(context, mqttMessage, readableLength);
			} catch (Throwable e) {
				logger.error(e.getMessage(), e);
			}
		}
		return null;
	}

	@Override
	public WsResponse encodeSubProtocol(Packet packet, TioConfig tioConfig, ChannelContext context) {
		if (packet instanceof MqttMessage) {
			ByteBuffer buffer = mqttServerAioHandler.encode(packet, null, context);
			return WsResponse.fromBytes(buffer.array());
		}
		return null;
	}

	/**
	 * 当客户端发 close flag 时，会走这个方法
	 */
	@Override
	public Object onClose(WsRequest wsRequest, byte[] bytes, ChannelContext context) {
		context.remove(MQTT_WS_MSG_BODY_KEY);
		return null;
	}

	/**
	 * 字符消息（binaryType = blob）过来后会走这个方法
	 */
	@Override
	public Object onText(WsRequest wsRequest, String text, ChannelContext context) {
		return null;
	}

	/**
	 * 读取 mqtt 消息体处理半包的情况
	 *
	 * @param bytes 消息类容
	 * @return ByteBuffer
	 */
	private static synchronized ByteBuffer getMqttBody(FastByteBuffer wsBody, byte[] bytes) {
		wsBody.writeBytes(bytes);
		int length = wsBody.size();
		if (length < 2) {
			return null;
		}
		ByteBuffer buffer = wsBody.toBuffer();
		Integer mqttLength = getMqttLength(buffer);
		if (mqttLength == null || length < (mqttLength + 2)) {
			return null;
		}
		// 数据已经读取完毕，此处需要重构
		wsBody.reset();
		// 重置 buffer
		buffer.rewind();
		return buffer;
	}

	/**
	 * 解析 mqtt 消息长度
	 *
	 * @param buffer the buffer to decode from
	 * @return mqtt 消息长度
	 */
	private static Integer getMqttLength(ByteBuffer buffer) {
		ByteBufferUtil.skipBytes(buffer, 1);
		int remainingLength = 0;
		int multiplier = 1;
		short digit;
		int loops = 0;
		do {
			if (!buffer.hasRemaining()) {
				return null;
			}
			digit = ByteBufferUtil.readUnsignedByte(buffer);
			remainingLength += (digit & 127) * multiplier;
			multiplier *= 128;
			loops++;
		} while ((digit & 128) != 0 && loops < 4);
		return remainingLength;
	}

}
