/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.dromara.mica.mqtt.codec;

import org.dromara.mica.mqtt.codec.properties.*;
import org.tio.core.ChannelContext;
import org.tio.utils.buffer.ByteBufferAllocator;
import org.tio.utils.buffer.ByteBufferUtil;
import org.tio.utils.hutool.FastByteBuffer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.dromara.mica.mqtt.codec.MqttConstant.DEFAULT_MAX_CLIENT_ID_LENGTH;

/**
 * Encodes Mqtt messages into bytes following the protocol specification v3.1
 * as described here <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html">MQTTV3.1</a>
 * or v5.0 as described here <a href="https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html">MQTTv5.0</a> -
 * depending on the version specified in the first CONNECT message that goes through the channel.
 *
 * @author netty
 * @author L.cm
 */
public final class MqttEncoder {
	public static final MqttEncoder INSTANCE = new MqttEncoder();

	private MqttEncoder() {
	}

	/**
	 * This is the main encoding method.
	 * It's only visible for testing.
	 *
	 * @param ctx       ChannelContext
	 * @param message   MQTT message to encode
	 * @param allocator ByteBuffer Allocator
	 * @return ByteBuf with encoded bytes
	 */
	public ByteBuffer doEncode(ChannelContext ctx, MqttMessage message, ByteBufferAllocator allocator) {
		switch (message.fixedHeader().messageType()) {
			case CONNECT:
				return encodeConnectMessage(ctx, (MqttConnectMessage) message, allocator);
			case CONNACK:
				return encodeConnAckMessage(ctx, (MqttConnAckMessage) message, allocator);
			case PUBLISH:
				return encodePublishMessage(ctx, (MqttPublishMessage) message, allocator);
			case SUBSCRIBE:
				return encodeSubscribeMessage(ctx, (MqttSubscribeMessage) message, allocator);
			case UNSUBSCRIBE:
				return encodeUnsubscribeMessage(ctx, (MqttUnsubscribeMessage) message, allocator);
			case SUBACK:
				return encodeSubAckMessage(ctx, (MqttSubAckMessage) message, allocator);
			case UNSUBACK:
				if (message instanceof MqttUnsubAckMessage) {
					return encodeUnsubAckMessage(ctx, (MqttUnsubAckMessage) message, allocator);
				}
				return encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(message, allocator);
			case PUBACK:
			case PUBREC:
			case PUBREL:
			case PUBCOMP:
				return encodePubReplyMessage(ctx, message, allocator);
			case DISCONNECT:
			case AUTH:
				return encodeReasonCodePlusPropertiesMessage(ctx, message, allocator);
			case PINGREQ:
			case PINGRESP:
				return encodeMessageWithOnlySingleByteFixedHeader(message, allocator);
			default:
				throw new IllegalArgumentException("Unknown message type: " + message.fixedHeader().messageType().value());
		}
	}

	private static ByteBuffer encodeConnectMessage(ChannelContext ctx,
												   MqttConnectMessage message,
												   ByteBufferAllocator allocator) {
		int payloadBufferSize = 0;

		MqttFixedHeader mqttFixedHeader = message.fixedHeader();
		MqttConnectVariableHeader variableHeader = message.variableHeader();
		MqttConnectPayload payload = message.payload();
		MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(variableHeader.name(), (byte) variableHeader.version());
		MqttCodecUtil.setMqttVersion(ctx, mqttVersion);

		// as MQTT 3.1 & 3.1.1 spec, If the User Name Flag is set to 0, the Password Flag MUST be set to 0
		if (!variableHeader.hasUserName() && variableHeader.hasPassword()) {
			throw new EncoderException("Without a username, the password MUST be not set");
		}

		// Client id
		String clientIdentifier = payload.clientIdentifier();
		if (!MqttCodecUtil.isValidClientId(mqttVersion, DEFAULT_MAX_CLIENT_ID_LENGTH, clientIdentifier)) {
			throw new MqttIdentifierRejectedException("invalid clientIdentifier: " + clientIdentifier);
		}
		byte[] clientIdentifierBytes = encodeStringUtf8(clientIdentifier);
		payloadBufferSize += 2 + clientIdentifierBytes.length;

		// Will topic and message
		String willTopic = payload.willTopic();
		byte[] willTopicBytes = willTopic != null ? encodeStringUtf8(willTopic) : ByteBufferUtil.EMPTY_BYTES;
		byte[] willMessage = payload.willMessageInBytes();
		byte[] willMessageBytes = willMessage != null ? willMessage : ByteBufferUtil.EMPTY_BYTES;
		if (variableHeader.isWillFlag()) {
			payloadBufferSize += 2 + willTopicBytes.length;
			payloadBufferSize += 2 + willMessageBytes.length;
		}

		String userName = payload.userName();
		byte[] userNameBytes = userName != null ? encodeStringUtf8(userName) : ByteBufferUtil.EMPTY_BYTES;
		if (variableHeader.hasUserName()) {
			payloadBufferSize += 2 + userNameBytes.length;
		}

		byte[] password = payload.passwordInBytes();
		byte[] passwordBytes = password != null ? password : ByteBufferUtil.EMPTY_BYTES;
		if (variableHeader.hasPassword()) {
			payloadBufferSize += 2 + passwordBytes.length;
		}

		// Fixed and variable header
		byte[] protocolNameBytes = mqttVersion.protocolNameBytes();
		byte[] propertiesBytes = encodePropertiesIfNeeded(mqttVersion, message.variableHeader().properties());

		final byte[] willPropertiesBytes;
		if (variableHeader.isWillFlag()) {
			willPropertiesBytes = encodePropertiesIfNeeded(mqttVersion, payload.willProperties());
			payloadBufferSize += willPropertiesBytes.length;
		} else {
			willPropertiesBytes = ByteBufferUtil.EMPTY_BYTES;
		}
		int variableHeaderBufferSize = 2 + protocolNameBytes.length + 4 + propertiesBytes.length;

		int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
		int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
		// 申请 ByteBuffer
		ByteBuffer buf = allocator.allocate(fixedHeaderBufferSize + variablePartSize);
		buf.put((byte) getFixedHeaderByte1(mqttFixedHeader));
		writeVariableLengthInt(buf, variablePartSize);
		buf.putShort((short) protocolNameBytes.length);
		buf.put(protocolNameBytes);

		buf.put((byte) variableHeader.version());
		buf.put((byte) getConnVariableHeaderFlag(variableHeader));
		buf.putShort((short) variableHeader.keepAliveTimeSeconds());
		buf.put(propertiesBytes);

		// Payload
		buf.putShort((short) clientIdentifierBytes.length);
		buf.put(clientIdentifierBytes, 0, clientIdentifierBytes.length);
		if (variableHeader.isWillFlag()) {
			buf.put(willPropertiesBytes, 0, willPropertiesBytes.length);
			buf.putShort((short) willTopicBytes.length);
			buf.put(willTopicBytes, 0, willTopicBytes.length);
			buf.putShort((short) willMessageBytes.length);
			buf.put(willMessageBytes, 0, willMessageBytes.length);
		}
		if (variableHeader.hasUserName()) {
			buf.putShort((short) userNameBytes.length);
			buf.put(userNameBytes, 0, userNameBytes.length);
		}
		if (variableHeader.hasPassword()) {
			buf.putShort((short) passwordBytes.length);
			buf.put(passwordBytes, 0, passwordBytes.length);
		}
		return buf;
	}

	private static int getConnVariableHeaderFlag(MqttConnectVariableHeader variableHeader) {
		int flagByte = 0;
		if (variableHeader.hasUserName()) {
			flagByte |= 0x80;
		}
		if (variableHeader.hasPassword()) {
			flagByte |= 0x40;
		}
		if (variableHeader.isWillRetain()) {
			flagByte |= 0x20;
		}
		flagByte |= (variableHeader.willQos() & 0x03) << 3;
		if (variableHeader.isWillFlag()) {
			flagByte |= 0x04;
		}
		if (variableHeader.isCleanSession()) {
			flagByte |= 0x02;
		}
		return flagByte;
	}

	private static ByteBuffer encodeConnAckMessage(ChannelContext ctx,
												   MqttConnAckMessage message,
												   ByteBufferAllocator allocator) {
		final MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
		byte[] propertiesBytes = encodePropertiesIfNeeded(mqttVersion, message.variableHeader().properties());
		ByteBuffer buf = allocator.allocate(4 + propertiesBytes.length);
		buf.put((byte) getFixedHeaderByte1(message.fixedHeader()));
		writeVariableLengthInt(buf, 2 + propertiesBytes.length);
		buf.put((byte) (message.variableHeader().isSessionPresent() ? 0x01 : 0x00));
		buf.put(message.variableHeader().connectReturnCode().value());
		buf.put(propertiesBytes);
		return buf;
	}

	private static ByteBuffer encodeSubscribeMessage(ChannelContext ctx,
													 MqttSubscribeMessage message,
													 ByteBufferAllocator allocator) {
		MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
		byte[] propertiesBytes = encodePropertiesIfNeeded(mqttVersion,
			message.idAndPropertiesVariableHeader().properties());

		final int variableHeaderBufferSize = 2 + propertiesBytes.length;
		int payloadBufferSize = 0;

		MqttFixedHeader mqttFixedHeader = message.fixedHeader();
		MqttMessageIdVariableHeader variableHeader = message.variableHeader();
		MqttSubscribePayload payload = message.payload();

		for (MqttTopicSubscription topic : payload.topicSubscriptions()) {
			String topicFilter = topic.topicFilter();
			byte[] topicFilterBytes = encodeStringUtf8(topicFilter);
			payloadBufferSize += 2 + topicFilterBytes.length;
			payloadBufferSize += 1;
		}

		int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
		int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

		ByteBuffer buf = allocator.allocate(fixedHeaderBufferSize + variablePartSize);
		buf.put((byte) getFixedHeaderByte1(mqttFixedHeader));
		writeVariableLengthInt(buf, variablePartSize);

		// Variable Header
		int messageId = variableHeader.messageId();
		buf.putShort((short) messageId);
		buf.put(propertiesBytes);

		// Payload
		for (MqttTopicSubscription topic : payload.topicSubscriptions()) {
			// topicFilter
			String topicFilter = topic.topicFilter();
			byte[] topicFilterBytes = encodeStringUtf8(topicFilter);
			buf.putShort((short) topicFilterBytes.length);
			buf.put(topicFilterBytes, 0, topicFilterBytes.length);
			if (mqttVersion == MqttVersion.MQTT_3_1_1 || mqttVersion == MqttVersion.MQTT_3_1) {
				buf.put((byte) topic.qualityOfService().value());
			} else {
				// option
				final MqttSubscriptionOption option = topic.option();
				int optionEncoded = option.retainHandling().value() << 4;
				if (option.isRetainAsPublished()) {
					optionEncoded |= 0x08;
				}
				if (option.isNoLocal()) {
					optionEncoded |= 0x04;
				}
				optionEncoded |= option.qos().value();
				buf.put((byte) optionEncoded);
			}
		}
		return buf;
	}

	private static ByteBuffer encodeUnsubscribeMessage(ChannelContext ctx,
													   MqttUnsubscribeMessage message,
													   ByteBufferAllocator allocator) {
		MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
		byte[] propertiesBytes = encodePropertiesIfNeeded(mqttVersion,
			message.idAndPropertiesVariableHeader().properties());

		final int variableHeaderBufferSize = 2 + propertiesBytes.length;
		int payloadBufferSize = 0;

		MqttFixedHeader mqttFixedHeader = message.fixedHeader();
		MqttMessageIdVariableHeader variableHeader = message.variableHeader();
		MqttUnsubscribePayload payload = message.payload();

		for (String topicName : payload.topics()) {
			byte[] topicNameBytes = encodeStringUtf8(topicName);
			payloadBufferSize += 2 + topicNameBytes.length;
		}

		int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
		int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

		ByteBuffer buf = allocator.allocate(fixedHeaderBufferSize + variablePartSize);
		buf.put((byte) getFixedHeaderByte1(mqttFixedHeader));
		writeVariableLengthInt(buf, variablePartSize);

		// Variable Header
		int messageId = variableHeader.messageId();
		buf.putShort((short) messageId);
		buf.put(propertiesBytes);

		// Payload
		for (String topicName : payload.topics()) {
			// topicName
			byte[] topicNameBytes = encodeStringUtf8(topicName);
			buf.putShort((short) topicNameBytes.length);
			buf.put(topicNameBytes, 0, topicNameBytes.length);
		}
		return buf;
	}

	private static ByteBuffer encodeSubAckMessage(ChannelContext ctx,
												  MqttSubAckMessage message,
												  ByteBufferAllocator allocator) {
		MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
		byte[] propertiesBytes = encodePropertiesIfNeeded(mqttVersion,
			message.idAndPropertiesVariableHeader().properties());
		int variableHeaderBufferSize = 2 + propertiesBytes.length;
		int payloadBufferSize = message.payload().grantedQoSLevels().size();
		int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
		int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
		ByteBuffer buf = allocator.allocate(fixedHeaderBufferSize + variablePartSize);
		buf.put((byte) getFixedHeaderByte1(message.fixedHeader()));
		writeVariableLengthInt(buf, variablePartSize);
		buf.putShort((short) message.variableHeader().messageId());
		buf.put(propertiesBytes);
		for (int code : message.payload().reasonCodes()) {
			buf.put((byte) code);
		}
		return buf;
	}

	private static ByteBuffer encodeUnsubAckMessage(ChannelContext ctx,
													MqttUnsubAckMessage message,
													ByteBufferAllocator allocator) {
		if (message.variableHeader() instanceof MqttMessageIdAndPropertiesVariableHeader) {
			MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
			byte[] propertiesBytes = encodePropertiesIfNeeded(mqttVersion, message.idAndPropertiesVariableHeader().properties());

			int variableHeaderBufferSize = 2 + propertiesBytes.length;
			MqttUnsubAckPayload payload = message.payload();
			int payloadBufferSize = payload == null ? 0 : payload.unsubscribeReasonCodes().size();
			int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
			int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);
			ByteBuffer buf = allocator.allocate(fixedHeaderBufferSize + variablePartSize);
			buf.put((byte) getFixedHeaderByte1(message.fixedHeader()));
			writeVariableLengthInt(buf, variablePartSize);
			buf.putShort((short) message.variableHeader().messageId());
			buf.put(propertiesBytes);

			if (payload != null) {
				for (Short reasonCode : payload.unsubscribeReasonCodes()) {
					buf.putShort(reasonCode);
				}
			}
			return buf;
		} else {
			return encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(message, allocator);
		}
	}

	private static ByteBuffer encodePublishMessage(ChannelContext ctx,
												   MqttPublishMessage message,
												   ByteBufferAllocator allocator) {
		MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
		MqttFixedHeader mqttFixedHeader = message.fixedHeader();
		MqttPublishVariableHeader variableHeader = message.variableHeader();
		// 处理 payload 为 null 的情况
		byte[] payload = message.payload() == null ? ByteBufferUtil.EMPTY_BYTES : message.payload();

		String topicName = variableHeader.topicName();
		byte[] topicNameBytes = encodeStringUtf8(topicName);

		byte[] propertiesBytes = encodePropertiesIfNeeded(mqttVersion,
			message.variableHeader().properties());

		int variableHeaderBufferSize = 2 + topicNameBytes.length +
			(mqttFixedHeader.qosLevel().value() > 0 ? 2 : 0) + propertiesBytes.length;
		int payloadBufferSize = payload.length;
		int variablePartSize = variableHeaderBufferSize + payloadBufferSize;
		int fixedHeaderBufferSize = 1 + getVariableLengthInt(variablePartSize);

		ByteBuffer buf = allocator.allocate(fixedHeaderBufferSize + variablePartSize);
		buf.put((byte) getFixedHeaderByte1(mqttFixedHeader));
		writeVariableLengthInt(buf, variablePartSize);
		buf.putShort((short) topicNameBytes.length);
		buf.put(topicNameBytes);
		if (mqttFixedHeader.qosLevel().value() > 0) {
			buf.putShort((short) variableHeader.packetId());
		}
		buf.put(propertiesBytes);
		buf.put(payload);
		return buf;
	}

	private static ByteBuffer encodePubReplyMessage(ChannelContext ctx,
													MqttMessage message,
													ByteBufferAllocator allocator) {
		if (message.variableHeader() instanceof MqttPubReplyMessageVariableHeader) {
			MqttFixedHeader mqttFixedHeader = message.fixedHeader();
			MqttPubReplyMessageVariableHeader variableHeader =
				(MqttPubReplyMessageVariableHeader) message.variableHeader();
			final byte[] propertiesBytes;
			final boolean includeReasonCode;
			final int variableHeaderBufferSize;
			final MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
			if (mqttVersion == MqttVersion.MQTT_5 &&
				(variableHeader.reasonCode() != MqttPubReplyMessageVariableHeader.REASON_CODE_OK ||
					!variableHeader.properties().isEmpty())) {
				propertiesBytes = encodeProperties(variableHeader.properties());
				includeReasonCode = true;
				variableHeaderBufferSize = 3 + propertiesBytes.length;
			} else {
				propertiesBytes = ByteBufferUtil.EMPTY_BYTES;
				includeReasonCode = false;
				variableHeaderBufferSize = 2;
			}

			final int fixedHeaderBufferSize = 1 + getVariableLengthInt(variableHeaderBufferSize);
			ByteBuffer buf = allocator.allocate(fixedHeaderBufferSize + variableHeaderBufferSize);
			buf.put((byte) getFixedHeaderByte1(mqttFixedHeader));
			writeVariableLengthInt(buf, variableHeaderBufferSize);
			buf.putShort((short) variableHeader.messageId());
			if (includeReasonCode) {
				buf.put(variableHeader.reasonCode());
			}
			buf.put(propertiesBytes);
			return buf;
		} else {
			return encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(message, allocator);
		}
	}

	private static ByteBuffer encodeMessageWithOnlySingleByteFixedHeaderAndMessageId(MqttMessage message,
																					 ByteBufferAllocator allocator) {
		MqttFixedHeader mqttFixedHeader = message.fixedHeader();
		MqttMessageIdVariableHeader variableHeader = (MqttMessageIdVariableHeader) message.variableHeader();
		// variable part only has a message id
		int variableHeaderBufferSize = 2;
		int fixedHeaderBufferSize = 1 + getVariableLengthInt(variableHeaderBufferSize);
		ByteBuffer buf = allocator.allocate(fixedHeaderBufferSize + variableHeaderBufferSize);
		buf.put((byte) getFixedHeaderByte1(mqttFixedHeader));
		writeVariableLengthInt(buf, variableHeaderBufferSize);
		buf.putShort((short) variableHeader.messageId());
		return buf;
	}

	private static ByteBuffer encodeReasonCodePlusPropertiesMessage(ChannelContext ctx,
																	MqttMessage message,
																	ByteBufferAllocator allocator) {
		if (message.variableHeader() instanceof MqttReasonCodeAndPropertiesVariableHeader) {
			MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
			MqttFixedHeader mqttFixedHeader = message.fixedHeader();
			MqttReasonCodeAndPropertiesVariableHeader variableHeader =
				(MqttReasonCodeAndPropertiesVariableHeader) message.variableHeader();

			final byte[] propertiesBytes;
			final boolean includeReasonCode;
			final int variableHeaderBufferSize;
			if (mqttVersion == MqttVersion.MQTT_5 &&
				(variableHeader.reasonCode() != MqttReasonCodeAndPropertiesVariableHeader.REASON_CODE_OK ||
					!variableHeader.properties().isEmpty())) {
				propertiesBytes = encodeProperties(variableHeader.properties());
				includeReasonCode = true;
				variableHeaderBufferSize = 1 + propertiesBytes.length;
			} else {
				propertiesBytes = ByteBufferUtil.EMPTY_BYTES;
				includeReasonCode = false;
				variableHeaderBufferSize = 0;
			}
			final int fixedHeaderBufferSize = 1 + getVariableLengthInt(variableHeaderBufferSize);
			ByteBuffer buf = allocator.allocate(fixedHeaderBufferSize + variableHeaderBufferSize);
			buf.put((byte) getFixedHeaderByte1(mqttFixedHeader));
			writeVariableLengthInt(buf, variableHeaderBufferSize);
			if (includeReasonCode) {
				buf.put(variableHeader.reasonCode());
			}
			buf.put(propertiesBytes);
			return buf;
		} else {
			return encodeMessageWithOnlySingleByteFixedHeader(message, allocator);
		}
	}

	private static ByteBuffer encodeMessageWithOnlySingleByteFixedHeader(MqttMessage message,
																		 ByteBufferAllocator allocator) {
		MqttFixedHeader mqttFixedHeader = message.fixedHeader();
		ByteBuffer buf = allocator.allocate(2);
		buf.put((byte) getFixedHeaderByte1(mqttFixedHeader));
		buf.put((byte) 0);
		return buf;
	}

	private static byte[] encodePropertiesIfNeeded(MqttVersion mqttVersion,
												   MqttProperties mqttProperties) {
		if (mqttVersion == MqttVersion.MQTT_5) {
			return encodeProperties(mqttProperties);
		}
		return ByteBufferUtil.EMPTY_BYTES;
	}

	private static byte[] encodeProperties(MqttProperties mqttProperties) {
		FastByteBuffer writeBuffer = new FastByteBuffer(128);
		for (MqttProperty property : mqttProperties.listAll()) {
			int propertyId = property.propertyId();
			MqttPropertyType propertyType = MqttPropertyType.valueOf(propertyId);
			switch (propertyType) {
				case PAYLOAD_FORMAT_INDICATOR:
				case REQUEST_PROBLEM_INFORMATION:
				case REQUEST_RESPONSE_INFORMATION:
				case MAXIMUM_QOS:
				case RETAIN_AVAILABLE:
				case WILDCARD_SUBSCRIPTION_AVAILABLE:
				case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
				case SHARED_SUBSCRIPTION_AVAILABLE:
					writeBuffer.writeVarLengthInt(propertyId);
					final byte bytePropValue = ((IntegerProperty) property).value().byteValue();
					writeBuffer.writeByte(bytePropValue);
					break;
				case SERVER_KEEP_ALIVE:
				case RECEIVE_MAXIMUM:
				case TOPIC_ALIAS_MAXIMUM:
				case TOPIC_ALIAS:
					writeBuffer.writeVarLengthInt(propertyId);
					final short twoBytesInPropValue =
						((IntegerProperty) property).value().shortValue();
					writeBuffer.writeShortBE(twoBytesInPropValue);
					break;
				case PUBLICATION_EXPIRY_INTERVAL:
				case SESSION_EXPIRY_INTERVAL:
				case WILL_DELAY_INTERVAL:
				case MAXIMUM_PACKET_SIZE:
					writeBuffer.writeVarLengthInt(propertyId);
					final int fourBytesIntPropValue = ((IntegerProperty) property).value();
					writeBuffer.writeIntBE(fourBytesIntPropValue);
					break;
				case SUBSCRIPTION_IDENTIFIER:
					writeBuffer.writeVarLengthInt(propertyId);
					final int vbi = ((IntegerProperty) property).value();
					writeBuffer.writeVarLengthInt(vbi);
					break;
				case CONTENT_TYPE:
				case RESPONSE_TOPIC:
				case ASSIGNED_CLIENT_IDENTIFIER:
				case AUTHENTICATION_METHOD:
				case RESPONSE_INFORMATION:
				case SERVER_REFERENCE:
				case REASON_STRING:
					writeBuffer.writeVarLengthInt(propertyId);
					writeEagerUTF8String(writeBuffer, ((StringProperty) property).value());
					break;
				case USER_PROPERTY:
					final List<StringPair> pairs =
						((UserProperties) property).value();
					for (StringPair pair : pairs) {
						writeBuffer.writeVarLengthInt(propertyId);
						writeEagerUTF8String(writeBuffer, pair.key);
						writeEagerUTF8String(writeBuffer, pair.value);
					}
					break;
				case CORRELATION_DATA:
				case AUTHENTICATION_DATA:
					writeBuffer.writeVarLengthInt(propertyId);
					final byte[] binaryPropValue = ((BinaryProperty) property).value();
					writeBuffer.writeShortBE((short) binaryPropValue.length);
					writeBuffer.writeBytes(binaryPropValue, 0, binaryPropValue.length);
					break;
				default:
					//shouldn't reach here
					throw new EncoderException("Unknown property type: " + propertyType);
			}
		}
		byte[] propertiesBytes = writeBuffer.toArray();
		writeBuffer.reset();
		writeBuffer.writeVarLengthInt(propertiesBytes.length);
		writeBuffer.writeBytes(propertiesBytes);
		return writeBuffer.toArray();
	}

	private static int getFixedHeaderByte1(MqttFixedHeader header) {
		int ret = 0;
		ret |= header.messageType().value() << 4;
		if (header.isDup()) {
			ret |= 0x08;
		}
		ret |= header.qosLevel().value() << 1;
		if (header.isRetain()) {
			ret |= 0x01;
		}
		return ret;
	}

	private static void writeVariableLengthInt(ByteBuffer buf, int num) {
		do {
			int digit = num & 0x7F;
			num >>>= 7;
			if (num > 0) {
				digit |= 0x80;
			}
			buf.put((byte) digit);
		} while (num > 0);
	}

	private static int getVariableLengthInt(int num) {
		int count = 0;
		do {
			num >>>= 7;
			count++;
		} while (num > 0);
		return count;
	}

	private static void writeEagerUTF8String(FastByteBuffer buf, String s) {
		if (s == null) {
			buf.writeShortBE((short) 0);
		} else {
			byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
			buf.writeShortBE((short) bytes.length);
			buf.writeBytes(bytes);
		}
	}

	private static byte[] encodeStringUtf8(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}
}
