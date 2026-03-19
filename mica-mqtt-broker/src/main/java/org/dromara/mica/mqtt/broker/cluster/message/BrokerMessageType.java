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

package org.dromara.mica.mqtt.broker.cluster.message;

public enum BrokerMessageType {
	CLIENT_CONNECT(1),
	CLIENT_DISCONNECT(2),
	SUBSCRIBE_NOTIFY(3),
	UNSUBSCRIBE_NOTIFY(4),
	PUBLISH_FORWARD(5),
	NODE_LEAVE(6),
	STATE_SYNC_REQUEST(7),
	STATE_SYNC_RESPONSE(8);

	private final int code;

	BrokerMessageType(int code) {
		this.code = code;
	}

	public int getCode() {
		return code;
	}

	public static BrokerMessageType fromCode(int code) {
		for (BrokerMessageType type : values()) {
			if (type.code == code) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown message type code: " + code);
	}
}
