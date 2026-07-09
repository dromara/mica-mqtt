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

import org.dromara.mica.mqtt.codec.MqttDecoder;
import org.dromara.mica.mqtt.codec.exception.DecoderException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MqttDecoder validation regression tests.
 *
 * @author L.cm
 */
class MqttDecoderValidationTest {

	@Test
	void decodeStringSupportsDirectByteBuffer() throws Exception {
		byte[] value = "mqtt".getBytes(StandardCharsets.UTF_8);
		ByteBuffer buffer = ByteBuffer.allocateDirect(value.length + 2);
		buffer.putShort((short) value.length);
		buffer.put(value);
		buffer.flip();

		Object bytesConsumed = newIntValue();
		String decoded = (String) decodeStringMethod().invoke(null, buffer, bytesConsumed);

		assertEquals("mqtt", decoded);
	}

	@Test
	void subscribeOptionReservedBitsAreRejected() throws Exception {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putShort((short) 1);
		buffer.put((byte) 'a');
		buffer.put((byte) 0xC0);
		buffer.flip();

		InvocationTargetException exception = assertThrows(InvocationTargetException.class,
			() -> decodeSubscribePayloadMethod().invoke(null, buffer, buffer.remaining()));
		assertTrue(exception.getCause() instanceof DecoderException);
	}

	private static Object newIntValue() throws Exception {
		Class<?> intValueClass = Class.forName("org.dromara.mica.mqtt.codec.MqttDecoder$IntValue");
		Constructor<?> constructor = intValueClass.getDeclaredConstructor();
		constructor.setAccessible(true);
		return constructor.newInstance();
	}

	private static Method decodeStringMethod() throws Exception {
		Class<?> intValueClass = Class.forName("org.dromara.mica.mqtt.codec.MqttDecoder$IntValue");
		Method method = MqttDecoder.class.getDeclaredMethod("decodeString", ByteBuffer.class, intValueClass);
		method.setAccessible(true);
		return method;
	}

	private static Method decodeSubscribePayloadMethod() throws Exception {
		Method method = MqttDecoder.class.getDeclaredMethod("decodeSubscribePayload", ByteBuffer.class, int.class);
		method.setAccessible(true);
		return method;
	}
}
