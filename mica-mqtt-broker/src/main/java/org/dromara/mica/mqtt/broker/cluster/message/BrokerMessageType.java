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
	CLIENT_CONNECT,        // 客户端连接通知
	CLIENT_DISCONNECT,    // 客户端断开通知
	SUBSCRIBE_NOTIFY,     // 订阅通知
	UNSUBSCRIBE_NOTIFY,   // 取消订阅通知
	PUBLISH_FORWARD,     // 消息转发
	NODE_LEAVE,          // 节点离开
	STATE_SYNC_REQUEST,  // 状态同步请求
	STATE_SYNC_RESPONSE, // 状态同步响应
}
