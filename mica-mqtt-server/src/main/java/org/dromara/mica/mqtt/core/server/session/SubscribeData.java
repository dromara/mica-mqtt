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

package org.dromara.mica.mqtt.core.server.session;

/**
 * 订阅数据内部类 - 使用享元模式复用实例。
 *
 * @author L.cm
 */
final class SubscribeData {
	private static final SubscribeData[][][][] POOL = new SubscribeData[3][2][2][3];
	private static final SubscribeData[] DECODE_CACHE = new SubscribeData[64];

	static {
		for (int qos = 0; qos <= 2; qos++) {
			for (int noLocal = 0; noLocal <= 1; noLocal++) {
				for (int retainAsPublished = 0; retainAsPublished <= 1; retainAsPublished++) {
					for (int retainHandling = 0; retainHandling <= 2; retainHandling++) {
						SubscribeData data = new SubscribeData((byte) qos, noLocal == 1,
							retainAsPublished == 1, (byte) retainHandling);
						POOL[qos][noLocal][retainAsPublished][retainHandling] = data;
						DECODE_CACHE[data.encoded] = data;
					}
				}
			}
		}
	}

	final byte qos;
	final boolean noLocal;
	final boolean retainAsPublished;
	final byte retainHandling;
	final byte encoded;

	private SubscribeData(byte qos, boolean noLocal, boolean retainAsPublished, byte retainHandling) {
		this.qos = qos;
		this.noLocal = noLocal;
		this.retainAsPublished = retainAsPublished;
		this.retainHandling = retainHandling;
		this.encoded = (byte) ((qos & 0x03)
			| ((noLocal ? 1 : 0) << 2)
			| ((retainAsPublished ? 1 : 0) << 3)
			| ((retainHandling & 0x03) << 4));
	}

	static SubscribeData of(byte qos, boolean noLocal) {
		return of(qos, noLocal, false, (byte) 0);
	}

	static SubscribeData of(byte qos, boolean noLocal, boolean retainAsPublished, byte retainHandling) {
		if (qos >= 0 && qos <= 2 && retainHandling >= 0 && retainHandling <= 2) {
			return POOL[qos][noLocal ? 1 : 0][retainAsPublished ? 1 : 0][retainHandling];
		}
		return new SubscribeData(qos, noLocal, retainAsPublished, retainHandling);
	}

	static SubscribeData decode(byte encoded) {
		SubscribeData data = DECODE_CACHE[encoded & 0x3F];
		if (data != null) {
			return data;
		}
		byte qos = (byte) (encoded & 0x03);
		boolean noLocal = (encoded & 0x04) != 0;
		boolean retainAsPublished = (encoded & 0x08) != 0;
		byte retainHandling = (byte) ((encoded >>> 4) & 0x03);
		return of(qos, noLocal, retainAsPublished, retainHandling);
	}
}
