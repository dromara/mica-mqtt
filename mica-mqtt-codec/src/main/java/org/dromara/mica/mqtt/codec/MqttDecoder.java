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

import org.dromara.mica.mqtt.codec.codes.MqttConnectReasonCode;
import org.dromara.mica.mqtt.codec.exception.DecoderException;
import org.dromara.mica.mqtt.codec.exception.MqttIdentifierRejectedException;
import org.dromara.mica.mqtt.codec.message.MqttMessage;
import org.dromara.mica.mqtt.codec.message.builder.MqttSubscriptionOption;
import org.dromara.mica.mqtt.codec.message.builder.MqttTopicSubscription;
import org.dromara.mica.mqtt.codec.message.header.*;
import org.dromara.mica.mqtt.codec.message.payload.*;
import org.dromara.mica.mqtt.codec.properties.*;
import org.tio.core.ChannelContext;
import org.tio.core.intf.Packet;
import org.tio.utils.buffer.ByteBufferUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes Mqtt messages from bytes, following
 * the MQTT protocol specification
 * <a href="https://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html">v3.1</a>
 * or
 * <a href="https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html">v5.0</a>, depending on the
 * version specified in the CONNECT message that first goes through the channel.
 *
 * @author netty
 * @author L.cm
 */
public final class MqttDecoder {
	private static final String MQTT_FIXED_HEADER_KEY = "MQTT_F_H_K";
	private final int maxBytesInMessage;
	private final int maxClientIdLength;

	public MqttDecoder() {
		this(MqttConstant.DEFAULT_MAX_BYTES_IN_MESSAGE);
	}

	public MqttDecoder(int maxBytesInMessage) {
		this(maxBytesInMessage, MqttConstant.DEFAULT_MAX_CLIENT_ID_LENGTH);
	}

	public MqttDecoder(int maxBytesInMessage, int maxClientIdLength) {
		this.maxBytesInMessage = maxBytesInMessage;
		this.maxClientIdLength = maxClientIdLength;
	}

	/**
	 * 解码 MQTT 消息
	 *
	 * @param ctx            ChannelContext
	 * @param buffer         ByteBuffer
	 * @param readableLength 可读长度
	 * @return MqttMessage
	 */
	public Packet doDecode(ChannelContext ctx, ByteBuffer buffer, int readableLength) {
		// 1. 解析消息头
		MqttFixedHeader mqttFixedHeader = getOrDecodeMqttFixedHeader(ctx, buffer, readableLength);
		if (mqttFixedHeader == null) {
			return null;
		}
		// 2. 判断消息长度
		int messageLength = mqttFixedHeader.getMessageLength();
		if (readableLength < messageLength) {
			return null;
		}
		// 清除缓存
		ctx.remove(MQTT_FIXED_HEADER_KEY);
		// 3. 判断是否 ping 消息，ping 只有消息类型
		MqttMessageType messageType = mqttFixedHeader.messageType();
		if (MqttMessageType.PINGREQ == messageType) {
			return MqttMessage.PINGREQ;
		} else if (MqttMessageType.PINGRESP == messageType) {
			return MqttMessage.PINGRESP;
		}
		return decodeMqttMessage(ctx, buffer, messageType, mqttFixedHeader);
	}

	private MqttMessage decodeMqttMessage(ChannelContext ctx, ByteBuffer buffer, MqttMessageType messageType,
										  MqttFixedHeader mqttFixedHeader) {
		// 1. 消息体长度
		int bytesRemainingInVariablePart = mqttFixedHeader.remainingLength();
		// 2. 解析头信息
		Object variableHeader;
		try {
			Result<?> decodedVariableHeader = decodeVariableHeader(ctx, buffer, messageType, mqttFixedHeader, bytesRemainingInVariablePart);
			variableHeader = decodedVariableHeader.value;
			bytesRemainingInVariablePart -= decodedVariableHeader.numberOfBytesConsumed;
		} catch (Exception cause) {
			throw new DecoderException(cause);
		}
		// 3. 解析消息体
		final Object payload = decodePayload(buffer, maxClientIdLength, messageType, bytesRemainingInVariablePart, variableHeader);
		return MqttMessageFactory.newMessage(mqttFixedHeader, variableHeader, payload);
	}

	/**
	 * Decodes the variable header (if any)
	 *
	 * @param buffer          the buffer to decode from
	 * @param mqttFixedHeader MqttFixedHeader of the same message
	 * @return the variable header
	 */
	private Result<?> decodeVariableHeader(ChannelContext ctx, ByteBuffer buffer,
										   MqttMessageType messageType,
										   MqttFixedHeader mqttFixedHeader,
										   int bytesRemainingInVariablePart) {
		switch (messageType) {
			case CONNECT:
				return decodeConnectionVariableHeader(ctx, buffer);
			case CONNACK:
				return decodeConnAckVariableHeader(ctx, buffer);
			case UNSUBSCRIBE:
			case SUBSCRIBE:
			case SUBACK:
			case UNSUBACK:
				return decodeMessageIdAndPropertiesVariableHeader(ctx, buffer, mqttFixedHeader);
			case PUBACK:
			case PUBREC:
			case PUBCOMP:
			case PUBREL:
				return decodePubReplyMessage(buffer, mqttFixedHeader, bytesRemainingInVariablePart);
			case PUBLISH:
				return decodePublishVariableHeader(ctx, buffer, mqttFixedHeader);
			case DISCONNECT:
			case AUTH:
				return decodeReasonCodeAndPropertiesVariableHeader(buffer, bytesRemainingInVariablePart);
			default:
				//shouldn't reach here
				throw new DecoderException("Unknown message type: " + messageType);
		}
	}

	/**
	 * Decodes the fixed header. It's one byte for the flags and then variable bytes for the remaining length.
	 *
	 * @param buffer the buffer to decode from
	 * @return the fixed header
	 */
	private static MqttFixedHeader decodeFixedHeader(ChannelContext ctx, ByteBuffer buffer) {
		short b1 = ByteBufferUtil.readUnsignedByte(buffer);
		MqttMessageType messageType = MqttMessageType.valueOf(b1 >> 4);
		boolean dup = (b1 & 0x08) == 0x08;
		MqttQoS qos = MqttQoS.valueOf((b1 & 0x06) >> 1);
		boolean retain = (b1 & 0x01) != 0;
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
		// MQTT protocol limits Remaining Length to 4 bytes
		if (loops == 4 && (digit & 128) != 0) {
			throw new DecoderException("remaining length exceeds 4 digits (" + messageType + ')');
		}
		int headLength = 1 + loops;
		return resetAndValidateFixedHeader(ctx, messageType, dup, qos, retain, headLength, remainingLength);
	}

	private static MqttFixedHeader resetAndValidateFixedHeader(ChannelContext ctx, MqttMessageType messageType,
															   boolean dup, MqttQoS qos, boolean retain,
															   int headLength, int remainingLength) {
		// === 内联 resetUnusedFields 逻辑 ===
		switch (messageType) {
			case CONNECT:
			case CONNACK:
			case PUBACK:
			case PUBREC:
			case PUBCOMP:
			case SUBACK:
			case UNSUBACK:
			case PINGREQ:
			case PINGRESP:
			case DISCONNECT:
				if (dup || qos != MqttQoS.QOS0 || retain) {
					dup = false;
					qos = MqttQoS.QOS0;
					retain = false;
				}
				break;
			case PUBREL:
			case SUBSCRIBE:
			case UNSUBSCRIBE:
				if (retain) {
					retain = false;
				}
				break;
			default:
				// 其他类型不做额外处理
				break;
		}
		// === 内联 validateFixedHeader 逻辑 ===
		switch (messageType) {
			case PUBREL:
			case SUBSCRIBE:
			case UNSUBSCRIBE:
				if (qos != MqttQoS.QOS1) {
					throw new DecoderException(messageType.name() + " message must have QoS 1");
				}
				break;
			case AUTH:
				if (MqttVersion.MQTT_5 != MqttCodecUtil.getMqttVersion(ctx)) {
					throw new DecoderException("AUTH message requires at least MQTT 5");
				}
				break;
			default:
				break;
		}
		// 消息头
		return new MqttFixedHeader(messageType, dup, qos, retain, headLength, remainingLength);
	}

	private static Result<MqttMessageIdAndPropertiesVariableHeader> decodeMessageIdAndPropertiesVariableHeader(
		ChannelContext ctx, ByteBuffer buffer, MqttFixedHeader mqttFixedHeader) {
		final MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
		final int packetId = decodeMessageId(buffer, mqttFixedHeader);

		final MqttMessageIdAndPropertiesVariableHeader mqttVariableHeader;
		final int mqtt5Consumed;

		if (mqttVersion == MqttVersion.MQTT_5) {
			final Result<MqttProperties> properties = decodeProperties(buffer);
			mqttVariableHeader = new MqttMessageIdAndPropertiesVariableHeader(packetId, properties.value);
			mqtt5Consumed = properties.numberOfBytesConsumed;
		} else {
			mqttVariableHeader = new MqttMessageIdAndPropertiesVariableHeader(packetId, MqttProperties.NO_PROPERTIES);
			mqtt5Consumed = 0;
		}
		return new Result<>(mqttVariableHeader, 2 + mqtt5Consumed);
	}

	/**
	 * decodeMessageId
	 *
	 * @param buffer          ByteBuffer
	 * @param mqttFixedHeader MqttFixedHeader
	 * @return messageId with numberOfBytesConsumed is 2
	 */
	private static int decodeMessageId(ByteBuffer buffer, MqttFixedHeader mqttFixedHeader) {
		final int messageId = decodeMsbLsb(buffer);
		// 注意：此处做 qos 降级处理，mqtt 规定 qos > 0，messageId 必须大于 0，固做降级处理
		if (messageId == 0) {
			mqttFixedHeader.downgradeQos();
		}
		return messageId;
	}

	private static MqttSubAckPayload decodeSubAckPayload(ByteBuffer buffer, int bytesRemainingInVariablePart) {
		final short[] grantedQos = new short[bytesRemainingInVariablePart];
		for (int i = 0; i < bytesRemainingInVariablePart; i++) {
			grantedQos[i] = ByteBufferUtil.readUnsignedByte(buffer);
		}
		return new MqttSubAckPayload(grantedQos);
	}

	private static MqttUnsubAckPayload decodeUnSubAckPayload(ByteBuffer buffer, int bytesRemainingInVariablePart) {
		final short[] reasonCodes = new short[bytesRemainingInVariablePart];
		for (int i = 0; i < bytesRemainingInVariablePart; i++) {
			reasonCodes[i] = ByteBufferUtil.readUnsignedByte(buffer);
		}
		return new MqttUnsubAckPayload(reasonCodes);
	}

	private static Result<MqttConnectVariableHeader> decodeConnectionVariableHeader(
		ChannelContext ctx, ByteBuffer buffer) {
		final Result<String> protoString = decodeString(buffer);
		int numberOfBytesConsumed = protoString.numberOfBytesConsumed;

		final byte protocolLevel = buffer.get();
		numberOfBytesConsumed += 1;

		MqttVersion version = MqttVersion.fromProtocolNameAndLevel(protoString.value, protocolLevel);
		MqttCodecUtil.setMqttVersion(ctx, version);

		final int b1 = ByteBufferUtil.readUnsignedByte(buffer);
		numberOfBytesConsumed += 1;

		final int keepAlive = decodeMsbLsb(buffer);
		numberOfBytesConsumed += 2;

		final boolean hasUserName = (b1 & 0x80) == 0x80;
		final boolean hasPassword = (b1 & 0x40) == 0x40;
		final boolean willRetain = (b1 & 0x20) == 0x20;
		final int willQos = (b1 & 0x18) >> 3;
		final boolean willFlag = (b1 & 0x04) == 0x04;
		final boolean cleanStart = (b1 & 0x02) == 0x02;
		if (version == MqttVersion.MQTT_3_1_1 || version == MqttVersion.MQTT_5) {
			final boolean zeroReservedFlag = (b1 & 0x01) == 0x0;
			if (!zeroReservedFlag) {
				// MQTT v3.1.1: The Server MUST validate that the reserved flag in the CONNECT Control Packet is
				// set to zero and disconnect the Client if it is not zero.
				// See https://docs.oasis-open.org/mqtt/mqtt/v3.1.1/os/mqtt-v3.1.1-os.html#_Toc385349230
				throw new DecoderException("non-zero reserved flag");
			}
		}

		final MqttProperties properties;
		if (version == MqttVersion.MQTT_5) {
			final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
			properties = propertiesResult.value;
			numberOfBytesConsumed += propertiesResult.numberOfBytesConsumed;
		} else {
			properties = MqttProperties.NO_PROPERTIES;
		}

		final MqttConnectVariableHeader mqttConnectVariableHeader = new MqttConnectVariableHeader(
			version.protocolName(),
			version.protocolLevel(),
			hasUserName,
			hasPassword,
			willRetain,
			willQos,
			willFlag,
			cleanStart,
			keepAlive,
			properties);
		return new Result<>(mqttConnectVariableHeader, numberOfBytesConsumed);
	}

	private static Result<MqttConnAckVariableHeader> decodeConnAckVariableHeader(
		ChannelContext ctx, ByteBuffer buffer) {
		final MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
		final boolean sessionPresent = (ByteBufferUtil.readUnsignedByte(buffer) & 0x01) == 0x01;
		byte returnCode = buffer.get();
		int numberOfBytesConsumed = 2;

		final MqttProperties properties;
		if (mqttVersion == MqttVersion.MQTT_5) {
			final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
			properties = propertiesResult.value;
			numberOfBytesConsumed += propertiesResult.numberOfBytesConsumed;
		} else {
			properties = MqttProperties.NO_PROPERTIES;
		}

		final MqttConnAckVariableHeader mqttConnAckVariableHeader =
			new MqttConnAckVariableHeader(MqttConnectReasonCode.valueOf(returnCode), sessionPresent, properties);
		return new Result<>(mqttConnAckVariableHeader, numberOfBytesConsumed);
	}

	/**
	 * Decodes the payload.
	 *
	 * @param buffer                       the buffer to decode from
	 * @param messageType                  type of the message being decoded
	 * @param bytesRemainingInVariablePart bytes remaining
	 * @param variableHeader               variable header of the same message
	 * @return the payload
	 */
	private static Object decodePayload(ByteBuffer buffer, int maxClientIdLength,
										MqttMessageType messageType, int bytesRemainingInVariablePart,
										Object variableHeader) {
		switch (messageType) {
			case CONNECT:
				return decodeConnectionPayload(buffer, maxClientIdLength,
					(MqttConnectVariableHeader) variableHeader, bytesRemainingInVariablePart);
			case SUBSCRIBE:
				return decodeSubscribePayload(buffer, bytesRemainingInVariablePart);
			case SUBACK:
				return decodeSubAckPayload(buffer, bytesRemainingInVariablePart);
			case UNSUBSCRIBE:
				return decodeUnSubscribePayload(buffer, bytesRemainingInVariablePart);
			case UNSUBACK:
				return decodeUnSubAckPayload(buffer, bytesRemainingInVariablePart);
			case PUBLISH:
				return decodePublishPayload(buffer, bytesRemainingInVariablePart);
			default:
				// unknown payload , no byte consumed
				return null;
		}
	}

	private static MqttConnectPayload decodeConnectionPayload(ByteBuffer buffer, int maxClientIdLength,
															  MqttConnectVariableHeader mqttConnectVariableHeader,
															  int bytesRemainingInVariablePart) {
		final Result<String> decodedClientId = decodeString(buffer);
		final String decodedClientIdValue = decodedClientId.value;
		final MqttVersion mqttVersion = MqttVersion.fromProtocolNameAndLevel(mqttConnectVariableHeader.name(),
			(byte) mqttConnectVariableHeader.version());
		if (MqttCodecUtil.isInvalidClientId(mqttVersion, maxClientIdLength, decodedClientIdValue)) {
			throw new MqttIdentifierRejectedException("invalid clientIdentifier: " + decodedClientIdValue);
		}
		int numberOfBytesConsumed = decodedClientId.numberOfBytesConsumed;

		Result<String> decodedWillTopic = null;
		byte[] decodedWillMessage = null;
		final MqttProperties willProperties;
		if (mqttConnectVariableHeader.isWillFlag()) {
			if (mqttVersion == MqttVersion.MQTT_5) {
				final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
				willProperties = propertiesResult.value;
				numberOfBytesConsumed += propertiesResult.numberOfBytesConsumed;
			} else {
				willProperties = MqttProperties.NO_PROPERTIES;
			}
			decodedWillTopic = decodeString(buffer, 0, 32767);
			numberOfBytesConsumed += decodedWillTopic.numberOfBytesConsumed;
			decodedWillMessage = decodeByteArray(buffer);
			numberOfBytesConsumed += decodedWillMessage.length + 2;
		} else {
			willProperties = MqttProperties.NO_PROPERTIES;
		}
		Result<String> decodedUserName = null;
		byte[] decodedPassword = null;
		if (mqttConnectVariableHeader.hasUsername()) {
			decodedUserName = decodeString(buffer);
			numberOfBytesConsumed += decodedUserName.numberOfBytesConsumed;
		}
		if (mqttConnectVariableHeader.hasPassword()) {
			decodedPassword = decodeByteArray(buffer);
			numberOfBytesConsumed += decodedPassword.length + 2;
		}

		// 校验消息中剩余的字节数是否为0
		validateNoBytesRemain(bytesRemainingInVariablePart, numberOfBytesConsumed, MqttMessageType.CONNECT);
		return new MqttConnectPayload(
			decodedClientId.value,
			willProperties,
			decodedWillTopic != null ? decodedWillTopic.value : null,
			decodedWillMessage,
			decodedUserName != null ? decodedUserName.value : null,
			decodedPassword);
	}

	private static MqttSubscribePayload decodeSubscribePayload(ByteBuffer buffer, int bytesRemainingInVariablePart) {
		final List<MqttTopicSubscription> subscribeTopics = new ArrayList<>();
		int numberOfBytesConsumed = 0;
		while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
			final Result<String> decodedTopicName = decodeString(buffer);
			numberOfBytesConsumed += decodedTopicName.numberOfBytesConsumed;
			//See 3.8.3.1 Subscription Options of MQTT 5.0 specification for optionByte details
			final short optionByte = ByteBufferUtil.readUnsignedByte(buffer);

			MqttQoS qos = MqttQoS.valueOf(optionByte & 0x03);
			boolean noLocal = ((optionByte & 0x04) >> 2) == 1;
			boolean retainAsPublished = ((optionByte & 0x08) >> 3) == 1;
			MqttSubscriptionOption.RetainedHandlingPolicy retainHandling =
				MqttSubscriptionOption.RetainedHandlingPolicy.valueOf((optionByte & 0x30) >> 4);

			final MqttSubscriptionOption subscriptionOption = new MqttSubscriptionOption(qos,
				noLocal, retainAsPublished, retainHandling);

			numberOfBytesConsumed++;
			subscribeTopics.add(new MqttTopicSubscription(decodedTopicName.value, subscriptionOption));
		}
		// 校验消息中剩余的字节数是否为0
		validateNoBytesRemain(bytesRemainingInVariablePart, numberOfBytesConsumed, MqttMessageType.SUBSCRIBE);
		return new MqttSubscribePayload(subscribeTopics);
	}

	private static MqttUnsubscribePayload decodeUnSubscribePayload(ByteBuffer buffer, int bytesRemainingInVariablePart) {
		final List<String> unsubscribeTopics = new ArrayList<>();
		int numberOfBytesConsumed = 0;
		while (numberOfBytesConsumed < bytesRemainingInVariablePart) {
			final Result<String> decodedTopicName = decodeString(buffer);
			numberOfBytesConsumed += decodedTopicName.numberOfBytesConsumed;
			unsubscribeTopics.add(decodedTopicName.value);
		}
		// 校验消息中剩余的字节数是否为0
		validateNoBytesRemain(bytesRemainingInVariablePart, numberOfBytesConsumed, MqttMessageType.UNSUBSCRIBE);
		return new MqttUnsubscribePayload(unsubscribeTopics);
	}

	private static byte[] decodePublishPayload(ByteBuffer buffer, int bytesRemainingInVariablePart) {
		if (bytesRemainingInVariablePart == 0) {
			return ByteBufferUtil.EMPTY_BYTES;
		} else {
			return ByteBufferUtil.readBytes(buffer, bytesRemainingInVariablePart);
		}
	}

	private static void validateNoBytesRemain(int bytesRemainingInVariablePart,
											  int numberOfBytesConsumed,
											  MqttMessageType mqttMessageType) {
		bytesRemainingInVariablePart -= numberOfBytesConsumed;
		if (bytesRemainingInVariablePart != 0) {
			throw new DecoderException(
				"non-zero remaining payload bytes: " + bytesRemainingInVariablePart + " (" + mqttMessageType + ')');
		}
	}

	private static Result<String> decodeString(ByteBuffer buffer) {
		return decodeString(buffer, 0, Integer.MAX_VALUE);
	}

	private static Result<String> decodeString(ByteBuffer buffer, int minBytes, int maxBytes) {
		int size = decodeMsbLsb(buffer);
		int numberOfBytesConsumed = 2;
		if (size < minBytes || size > maxBytes) {
			ByteBufferUtil.skipBytes(buffer, size);
			numberOfBytesConsumed += size;
			return new Result<>(null, numberOfBytesConsumed);
		}
		String s = new String(buffer.array(), buffer.position(), size, StandardCharsets.UTF_8);
		ByteBufferUtil.skipBytes(buffer, size);
		numberOfBytesConsumed += size;
		return new Result<>(s, numberOfBytesConsumed);
	}

	/**
	 * @return the decoded byte[], numberOfBytesConsumed = byte[].length + 2
	 */
	private static byte[] decodeByteArray(ByteBuffer buffer) {
		int size = decodeMsbLsb(buffer);
		byte[] bytes = new byte[size];
		buffer.get(bytes);
		return bytes;
	}

	// packing utils to reduce the amount of garbage while decoding ints
	private static long packInts(int a, int b) {
		return (((long) a) << 32) | (b & 0xFFFFFFFFL);
	}

	private static int unpackA(long ints) {
		return (int) (ints >> 32);
	}

	private static int unpackB(long ints) {
		return (int) ints;
	}

	/**
	 * numberOfBytesConsumed = 2. return decoded result.
	 */
	private static int decodeMsbLsb(ByteBuffer buffer) {
		int min = 0;
		int max = 65535;
		short msbSize = ByteBufferUtil.readUnsignedByte(buffer);
		short lsbSize = ByteBufferUtil.readUnsignedByte(buffer);
		int result = msbSize << 8 | lsbSize;
		if (result < min || result > max) {
			result = -1;
		}
		return result;
	}

	/**
	 * See 1.5.5 Variable Byte Integer section of MQTT 5.0 specification for encoding/decoding rules
	 *
	 * @param buffer the buffer to decode from
	 * @return result pack with a = decoded integer, b = numberOfBytesConsumed. Need to unpack to read them.
	 * @throws DecoderException if bad MQTT protocol limits Remaining Length
	 */
	private static long decodeVariableByteInteger(ByteBuffer buffer) {
		int remainingLength = 0;
		int multiplier = 1;
		short digit;
		int loops = 0;
		do {
			digit = ByteBufferUtil.readUnsignedByte(buffer);
			remainingLength += (digit & 127) * multiplier;
			multiplier *= 128;
			loops++;
		} while ((digit & 128) != 0 && loops < 4);

		if (loops == 4 && (digit & 128) != 0) {
			throw new DecoderException("MQTT protocol limits Remaining Length to 4 bytes");
		}
		return packInts(remainingLength, loops);
	}

	private static Result<MqttProperties> decodeProperties(ByteBuffer buffer) {
		final long propertiesLengthVBI = decodeVariableByteInteger(buffer);
		int totalPropertiesLength = unpackA(propertiesLengthVBI);
		int lengthFieldBytes = unpackB(propertiesLengthVBI);

		// 没有属性时，直接返回空属性
		if (totalPropertiesLength == 0) {
			return new Result<>(MqttProperties.NO_PROPERTIES, lengthFieldBytes);
		}

		MqttProperties decodedProperties = new MqttProperties();
		int consumedWithinProperties = 0;

		while (consumedWithinProperties < totalPropertiesLength) {
			long propertyIdVBI = decodeVariableByteInteger(buffer);
			final int propertyIdValue = unpackA(propertyIdVBI);
			consumedWithinProperties += unpackB(propertyIdVBI);
			MqttPropertyType propertyType = MqttPropertyType.valueOf(propertyIdValue);
			switch (propertyType) {
				case PAYLOAD_FORMAT_INDICATOR:
				case REQUEST_PROBLEM_INFORMATION:
				case REQUEST_RESPONSE_INFORMATION:
				case MAXIMUM_QOS:
				case RETAIN_AVAILABLE:
				case WILDCARD_SUBSCRIPTION_AVAILABLE:
				case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
				case SHARED_SUBSCRIPTION_AVAILABLE: {
					final int b1 = ByteBufferUtil.readUnsignedByte(buffer);
					consumedWithinProperties += 1;
					decodedProperties.add(new IntegerProperty(propertyIdValue, b1));
					break;
				}
				case SERVER_KEEP_ALIVE:
				case RECEIVE_MAXIMUM:
				case TOPIC_ALIAS_MAXIMUM:
				case TOPIC_ALIAS: {
					final int int2BytesResult = decodeMsbLsb(buffer);
					consumedWithinProperties += 2;
					decodedProperties.add(new IntegerProperty(propertyIdValue, int2BytesResult));
					break;
				}
				case MESSAGE_EXPIRY_INTERVAL:
				case SESSION_EXPIRY_INTERVAL:
				case WILL_DELAY_INTERVAL:
				case MAXIMUM_PACKET_SIZE: {
					final int maxPacketSize = buffer.getInt();
					consumedWithinProperties += 4;
					decodedProperties.add(new IntegerProperty(propertyIdValue, maxPacketSize));
					break;
				}
				case SUBSCRIPTION_IDENTIFIER: {
					long vbIntegerResult = decodeVariableByteInteger(buffer);
					consumedWithinProperties += unpackB(vbIntegerResult);
					decodedProperties.add(new IntegerProperty(propertyIdValue, unpackA(vbIntegerResult)));
					break;
				}
				case CONTENT_TYPE:
				case RESPONSE_TOPIC:
				case ASSIGNED_CLIENT_IDENTIFIER:
				case AUTHENTICATION_METHOD:
				case RESPONSE_INFORMATION:
				case SERVER_REFERENCE:
				case REASON_STRING: {
					final Result<String> stringResult = decodeString(buffer);
					consumedWithinProperties += stringResult.numberOfBytesConsumed;
					decodedProperties.add(new StringProperty(propertyIdValue, stringResult.value));
					break;
				}
				case USER_PROPERTY: {
					final Result<String> keyResult = decodeString(buffer);
					final Result<String> valueResult = decodeString(buffer);
					consumedWithinProperties += keyResult.numberOfBytesConsumed;
					consumedWithinProperties += valueResult.numberOfBytesConsumed;
					decodedProperties.add(new UserProperty(keyResult.value, valueResult.value));
					break;
				}
				case CORRELATION_DATA:
				case AUTHENTICATION_DATA: {
					final byte[] binaryDataResult = decodeByteArray(buffer);
					consumedWithinProperties += binaryDataResult.length + 2;
					decodedProperties.add(new BinaryProperty(propertyIdValue, binaryDataResult));
					break;
				}
				default:
					//shouldn't reach here
					throw new DecoderException("Unknown property type: " + propertyType);
			}
		}
		return new Result<>(decodedProperties, lengthFieldBytes + consumedWithinProperties);
	}


	private Result<MqttPubReplyMessageVariableHeader> decodePubReplyMessage(
		ByteBuffer buffer, MqttFixedHeader mqttFixedHeader, int bytesRemainingInVariablePart) {
		final int packetId = decodeMessageId(buffer, mqttFixedHeader);
		final MqttPubReplyMessageVariableHeader mqttPubAckVariableHeader;
		final int consumed;
		final int packetIdNumberOfBytesConsumed = 2;
		if (bytesRemainingInVariablePart > 3) {
			final byte reasonCode = buffer.get();
			final Result<MqttProperties> properties = decodeProperties(buffer);
			mqttPubAckVariableHeader = new MqttPubReplyMessageVariableHeader(packetId,
				reasonCode,
				properties.value);
			consumed = packetIdNumberOfBytesConsumed + 1 + properties.numberOfBytesConsumed;
		} else if (bytesRemainingInVariablePart > 2) {
			final byte reasonCode = buffer.get();
			mqttPubAckVariableHeader = new MqttPubReplyMessageVariableHeader(packetId,
				reasonCode,
				MqttProperties.NO_PROPERTIES);
			consumed = packetIdNumberOfBytesConsumed + 1;
		} else {
			mqttPubAckVariableHeader = new MqttPubReplyMessageVariableHeader(packetId,
				(byte) 0,
				MqttProperties.NO_PROPERTIES);
			consumed = packetIdNumberOfBytesConsumed;
		}

		return new Result<>(mqttPubAckVariableHeader, consumed);
	}

	private Result<MqttReasonCodeAndPropertiesVariableHeader> decodeReasonCodeAndPropertiesVariableHeader(
		ByteBuffer buffer, int bytesRemainingInVariablePart) {
		final byte reasonCode;
		final MqttProperties properties;
		final int consumed;
		if (bytesRemainingInVariablePart > 1) {
			reasonCode = buffer.get();
			final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
			properties = propertiesResult.value;
			consumed = 1 + propertiesResult.numberOfBytesConsumed;
		} else if (bytesRemainingInVariablePart > 0) {
			reasonCode = buffer.get();
			properties = MqttProperties.NO_PROPERTIES;
			consumed = 1;
		} else {
			reasonCode = 0;
			properties = MqttProperties.NO_PROPERTIES;
			consumed = 0;
		}
		final MqttReasonCodeAndPropertiesVariableHeader mqttReasonAndPropsVariableHeader =
			new MqttReasonCodeAndPropertiesVariableHeader(reasonCode, properties);
		return new Result<>(mqttReasonAndPropsVariableHeader, consumed);
	}

	private MqttFixedHeader getOrDecodeMqttFixedHeader(ChannelContext ctx, ByteBuffer buffer, int readableLength) {
		// 1. 缓存避免重复解析
		MqttFixedHeader mqttFixedHeader = ctx.get(MQTT_FIXED_HEADER_KEY);
		if (mqttFixedHeader != null) {
			ByteBufferUtil.skipBytes(buffer, mqttFixedHeader.headLength());
			return mqttFixedHeader;
		}
		// 2. 判断缓存中协议头是否读完（MQTT协议头为2字节）
		if (readableLength < MqttConstant.MQTT_PROTOCOL_LENGTH) {
			return null;
		}
		// 3. 解析 FixedHeader 2~5 个字节
		buffer.mark();
		mqttFixedHeader = decodeFixedHeader(ctx, buffer);
		// 不够读
		if (mqttFixedHeader == null) {
			buffer.reset();
			return null;
		}
		int messageLength = mqttFixedHeader.getMessageLength();
		// 超过最大包
		if (messageLength > maxBytesInMessage) {
			throw new DecoderException("too large message: " + messageLength + " bytes but maxBytesInMessage is " + maxBytesInMessage);
		}
		// 存储固定头，避免重复解析
		ctx.set(MQTT_FIXED_HEADER_KEY, mqttFixedHeader);
		// 3. 长度不够，直接返回 null
		if (readableLength < messageLength) {
			ctx.setPacketNeededLength(messageLength);
			return null;
		}
		return mqttFixedHeader;
	}

	private Result<MqttPublishVariableHeader> decodePublishVariableHeader(
		ChannelContext ctx, ByteBuffer buffer,
		MqttFixedHeader mqttFixedHeader) {
		final MqttVersion mqttVersion = MqttCodecUtil.getMqttVersion(ctx);
		final Result<String> decodedTopic = decodeString(buffer);
		// 校验发布的 topic name，不能包含通配符
		if (MqttCodecUtil.isTopicFilter(decodedTopic.value)) {
			throw new DecoderException("invalid publish topic name: " + decodedTopic.value + " (contains wildcards)");
		}
		int numberOfBytesConsumed = decodedTopic.numberOfBytesConsumed;

		int messageId = -1;
		if (mqttFixedHeader.qosLevel().value() > 0) {
			messageId = decodeMessageId(buffer, mqttFixedHeader);
			numberOfBytesConsumed += 2;
		}

		final MqttProperties properties;
		if (mqttVersion == MqttVersion.MQTT_5) {
			final Result<MqttProperties> propertiesResult = decodeProperties(buffer);
			properties = propertiesResult.value;
			numberOfBytesConsumed += propertiesResult.numberOfBytesConsumed;
		} else {
			properties = MqttProperties.NO_PROPERTIES;
		}

		final MqttPublishVariableHeader mqttPublishVariableHeader =
			new MqttPublishVariableHeader(decodedTopic.value, messageId, properties);
		return new Result<>(mqttPublishVariableHeader, numberOfBytesConsumed);
	}

	private static final class Result<T> {

		private final T value;
		private final int numberOfBytesConsumed;

		Result(T value, int numberOfBytesConsumed) {
			this.value = value;
			this.numberOfBytesConsumed = numberOfBytesConsumed;
		}
	}
}
