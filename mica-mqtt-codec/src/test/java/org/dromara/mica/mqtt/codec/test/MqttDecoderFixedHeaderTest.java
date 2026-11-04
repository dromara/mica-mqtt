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

package org.dromara.mica.mqtt.codec.test;

import net.dreamlu.mica.net.client.ClientChannelContext;
import net.dreamlu.mica.net.client.TioClientConfig;
import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.codec.MqttDecoder;
import org.dromara.mica.mqtt.codec.exception.DecoderException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定头解码回归测试。
 *
 * <p>背景：明文 MQTT 端口收到非 MQTT 流量（如 TLS ClientHello 首字节 0x16、HTTP GET 首字节 'G'=0x47）时，
 * 旧实现把首字节的 bit1~bit2 当作 QoS 解析，抛出 {@code IllegalArgumentException: invalid QoS: 3}，
 * 既误导运维（客户端根本没发 QoS 3 消息），又绕过了 DecoderException 的错误处理路径。
 *
 * @author L.cm
 */
class MqttDecoderFixedHeaderTest {
	private static final TioClientConfig TIO_CONFIG = new TioClientConfig(null, null);

	@Test
	void tlsClientHelloFirstByteMustNotBeReportedAsInvalidQos() {
		// TLS ClientHello: 首字节 0x16 (handshake record)
		Throwable e = assertThrows(Throwable.class,
			() -> decode(new byte[]{0x16, 0x03, 0x01, 0x00, 0x2a}));
		assertTrue(e instanceof DecoderException, "期望 DecoderException，实际: " + e.getClass().getName());
		assertFalse(String.valueOf(e.getMessage()).contains("invalid QoS"),
			"非 MQTT 流量不应被误报为非法 QoS: " + e.getMessage());
		assertTrue(String.valueOf(e.getMessage()).contains("0x16"),
			"错误信息应包含首字节，便于识别非 MQTT 流量: " + e.getMessage());
	}

	@Test
	void httpGetFirstByteMustNotBeReportedAsInvalidQos() {
		// HTTP GET: 首字节 'G' = 0x47
		Throwable e = assertThrows(Throwable.class,
			() -> decode("GET /mqtt HTTP/1.1\r\nHost: x\r\n\r\n".getBytes()));
		assertTrue(e instanceof DecoderException, "期望 DecoderException，实际: " + e.getClass().getName());
		assertFalse(String.valueOf(e.getMessage()).contains("invalid QoS"),
			"非 MQTT 流量不应被误报为非法 QoS: " + e.getMessage());
		assertTrue(String.valueOf(e.getMessage()).contains("0x47"),
			"错误信息应包含首字节，便于识别非 MQTT 流量: " + e.getMessage());
	}

	@Test
	void publishWithQos3IsRejectedAsInvalidQos() {
		// 真正的 QoS 3 PUBLISH: type=3(0x30) | qos=3(0x06) = 0x36
		Throwable e = assertThrows(DecoderException.class,
			() -> decode(new byte[]{0x36, 0x08, 0x00, 0x02, '/', 't', 0x00, 0x01, 'h', 'i'}));
		assertTrue(String.valueOf(e.getMessage()).contains("invalid QoS"),
			"真实 QoS 3 应报非法 QoS: " + e.getMessage());
		assertTrue(String.valueOf(e.getMessage()).contains("0x36"),
			"错误信息应包含首字节: " + e.getMessage());
	}

	@Test
	void connectWithReservedFlagsIsRejected() {
		// CONNECT 的保留位（bit3~bit0）必须为 0，0x12 表示 bit2 = 1
		Throwable e = assertThrows(DecoderException.class,
			() -> decode(new byte[]{0x12, 0x0E, 0x00, 0x04, 'M', 'Q', 'T', 'T',
				0x04, 0x02, 0x00, 0x3c, 0x00, 0x02, 'a', 'b'}));
		assertTrue(String.valueOf(e.getMessage()).contains("reserved"),
			"CONNECT 保留位非法应报 reserved: " + e.getMessage());
	}

	@Test
	void reservedMessageTypeIsDecoderException() {
		// 首字节 0x00 -> 消息类型 0 为保留值
		Throwable e = assertThrows(DecoderException.class, () -> decode(new byte[]{0x00, 0x00}));
		assertTrue(String.valueOf(e.getMessage()).contains("message type"),
			"非法消息类型应报 message type: " + e.getMessage());
	}

	@Test
	void legitConnectStillDecodes() {
		assertDoesNotThrow(() -> decode(new byte[]{0x10, 0x0E, 0x00, 0x04, 'M', 'Q', 'T', 'T',
			0x04, 0x02, 0x00, 0x3c, 0x00, 0x02, 'a', 'b'}));
	}

	/**
	 * 客户端实际会发出的各类固定头必须全部通过严格校验，
	 * 确认新增的标志位校验不会误伤正常客户端。
	 */
	@Test
	void allFixedHeadersSentByClientsAreAccepted() {
		// CONNECT (0x10)
		assertDoesNotThrow(() -> decode(new byte[]{0x10, 0x0E, 0x00, 0x04, 'M', 'Q', 'T', 'T',
			0x04, 0x02, 0x00, 0x3c, 0x00, 0x02, 'a', 'b'}));
		// PUBLISH QoS0 (0x30) / QoS1 (0x32) / QoS2 (0x34) / QoS1+dup+retain (0x3B)
		assertDoesNotThrow(() -> decode(new byte[]{0x30, 0x06, 0x00, 0x02, '/', 't', 'h', 'i'}));
		assertDoesNotThrow(() -> decode(new byte[]{0x32, 0x08, 0x00, 0x02, '/', 't', 'h', 'i'}));
		assertDoesNotThrow(() -> decode(new byte[]{0x34, 0x08, 0x00, 0x02, '/', 't', 'h', 'i'}));
		assertDoesNotThrow(() -> decode(new byte[]{0x3B, 0x08, 0x00, 0x02, '/', 't', 'h', 'i'}));
		// PUBACK (0x40) / PUBREC (0x50) / PUBCOMP (0x70)
		assertDoesNotThrow(() -> decode(new byte[]{0x40, 0x02, 0x00, 0x01}));
		assertDoesNotThrow(() -> decode(new byte[]{0x50, 0x02, 0x00, 0x01}));
		assertDoesNotThrow(() -> decode(new byte[]{0x70, 0x02, 0x00, 0x01}));
		// PUBREL (0x62)
		assertDoesNotThrow(() -> decode(new byte[]{0x62, 0x02, 0x00, 0x01}));
		// SUBSCRIBE (0x82) / UNSUBSCRIBE (0xA2)
		assertDoesNotThrow(() -> decode(new byte[]{(byte) 0x82, 0x08, 0x00, 0x01, 0x00, 0x02, '/', 't', 0x00}));
		assertDoesNotThrow(() -> decode(new byte[]{(byte) 0xA2, 0x06, 0x00, 0x01, 0x00, 0x02, '/', 't'}));
		// PINGREQ (0xC0)
		assertDoesNotThrow(() -> decode(new byte[]{(byte) 0xC0, 0x00}));
		// DISCONNECT (0xE0)
		assertDoesNotThrow(() -> decode(new byte[]{(byte) 0xE0, 0x00}));
	}

	/**
	 * PUBREL 重传时部分历史实现会置 DUP 位，broker 通常忽略该位，不能因此断开连接。
	 */
	@Test
	void pubrelWithDupFlagIsAccepted() {
		assertDoesNotThrow(() -> decode(new byte[]{0x6A, 0x02, 0x00, 0x01}));
	}

	/**
	 * SUBSCRIBE 订阅选项里的请求 QoS 为 3（保留值）时，应抛 DecoderException，
	 * 而不是绕过服务端错误处理路径的 IllegalArgumentException。
	 */
	@Test
	void subscribeOptionWithQos3IsRejectedAsDecoderException() {
		DecoderException e = assertThrows(DecoderException.class,
			() -> decodeAfterConnect(new byte[]{(byte) 0x82, 0x07, 0x00, 0x01, 0x00, 0x02, '/', 't', 0x03}));
		assertTrue(String.valueOf(e.getMessage()).contains("invalid QoS"),
			"订阅选项 QoS 非法应报 invalid QoS: " + e.getMessage());
	}

	/**
	 * SUBSCRIBE 订阅选项里的 Retain Handling 为 3（保留值）时，应抛 DecoderException。
	 */
	@Test
	void subscribeOptionWithInvalidRetainHandlingIsRejectedAsDecoderException() {
		DecoderException e = assertThrows(DecoderException.class,
			() -> decodeAfterConnect(new byte[]{(byte) 0x82, 0x07, 0x00, 0x01, 0x00, 0x02, '/', 't', 0x30}));
		assertTrue(String.valueOf(e.getMessage()).contains("RetainHandlingPolicy"),
			"订阅选项 RetainHandling 非法应报 RetainHandlingPolicy: " + e.getMessage());
	}

	/**
	 * 先完成 CONNECT 确定协议版本，再解码后续报文
	 */
	private static Object decodeAfterConnect(byte[] bytes) {
		ChannelContext ctx = new ClientChannelContext(TIO_CONFIG);
		MqttDecoder decoder = new MqttDecoder();
		byte[] connect = new byte[]{0x10, 0x0E, 0x00, 0x04, 'M', 'Q', 'T', 'T',
			0x04, 0x02, 0x00, 0x3c, 0x00, 0x02, 'a', 'b'};
		decoder.doDecode(ctx, ByteBuffer.wrap(connect), connect.length);
		return decoder.doDecode(ctx, ByteBuffer.wrap(bytes), bytes.length);
	}

	/**
	 * 合法 MQTT 流在任意 TCP 分片下都必须能正确解码，
	 * 确认固定头标志位校验不会误伤正常报文。
	 */
	@Test
	void legitMqttStreamSurvivesArbitraryTcpSegmentation() throws Exception {
		byte[] stream = legitStream();
		Random random = new Random(42);
		int packets = 0;
		for (int iter = 0; iter < 200; iter++) {
			ChannelContext ctx = new ClientChannelContext(TIO_CONFIG);
			MqttDecoder decoder = new MqttDecoder();
			// 模拟 TcpDecodeRunnable 的流式拼接：未消费的数据保留，等待下一帧
			ByteBuffer buf = ByteBuffer.allocate(stream.length + 64);
			int pos = 0;
			while (pos < stream.length) {
				int chunk = Math.min(1 + random.nextInt(7), stream.length - pos);
				buf.put(stream, pos, chunk);
				pos += chunk;
				buf.flip();
				while (true) {
					int initPosition = buf.position();
					int readableLength = buf.limit() - initPosition;
					Integer needed = ctx.packetNeededLength;
					if (needed != null && readableLength < needed) {
						break;
					}
					Object msg;
					try {
						msg = decoder.doDecode(ctx, buf, readableLength);
					} catch (Throwable e) {
						buf.position(initPosition);
						throw new AssertionError("合法 MQTT 流解码失败: " + e, e);
					}
					if (msg == null) {
						buf.position(initPosition);
						break;
					}
					packets++;
				}
				buf.compact();
			}
		}
		assertTrue(packets > 0, "合法 MQTT 流应能解码出报文");
	}

	/**
	 * 合法 MQTT 字节流：CONNECT + PUBLISH(QoS1) + PUBLISH(QoS2) + PINGREQ
	 */
	private static byte[] legitStream() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(new byte[]{0x10, (byte) 0x0E});
		out.write(new byte[]{0x00, 0x04, 'M', 'Q', 'T', 'T', 0x04, 0x02, 0x00, 0x3c});
		out.write(new byte[]{0x00, 0x02, 'a', 'b'});
		// PUBLISH QoS1 topic=/t payload=hi
		byte[] p1 = new byte[]{0x32, 0x08, 0x00, 0x02, '/', 't', 0x00, 0x01, 'h', 'i'};
		out.write(p1, 0, p1.length);
		// PUBLISH QoS2 topic=/t payload=hi
		byte[] p2 = new byte[]{0x34, 0x08, 0x00, 0x02, '/', 't', 0x00, 0x01, 'h', 'i'};
		out.write(p2, 0, p2.length);
		// PINGREQ
		out.write(new byte[]{(byte) 0xC0, 0x00});
		return out.toByteArray();
	}

	private static Object decode(byte[] bytes) {
		ChannelContext ctx = new ClientChannelContext(TIO_CONFIG);
		return new MqttDecoder().doDecode(ctx, ByteBuffer.wrap(bytes), bytes.length);
	}
}
