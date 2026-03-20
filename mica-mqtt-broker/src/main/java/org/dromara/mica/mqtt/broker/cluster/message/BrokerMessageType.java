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

/**
 * 集群消息类型枚举
 *
 * @author L.cm
 */
public enum BrokerMessageType {
	/**
	 * 客户端连接通知 - 客户端连接成功时广播，用于更新全局客户端位置映射
	 */
	CLIENT_CONNECT(1),

	/**
	 * 客户端断开通知 - 客户端断开连接时广播，用于清理远程客户端信息
	 */
	CLIENT_DISCONNECT(2),

	/**
	 * 订阅通知 - 客户端订阅时广播，用于同步订阅信息到其他节点
	 */
	SUBSCRIBE_NOTIFY(3),

	/**
	 * 取消订阅通知 - 客户端取消订阅时广播，用于同步取消订阅信息
	 */
	UNSUBSCRIBE_NOTIFY(4),

	/**
	 * 消息转发 - 跨节点消息转发，发布者所在节点向订阅者所在节点转发消息
	 */
	PUBLISH_FORWARD(5),

	/**
	 * 节点离开通知 - 节点关闭或失联时广播，通知其他节点清理该节点的客户端和订阅信息
	 */
	NODE_LEAVE(6),

	/**
	 * 状态同步请求 - 新节点加入时，向其他节点请求全量状态（客户端映射+订阅信息）
	 */
	STATE_SYNC_REQUEST(7),

	/**
	 * 状态同步响应 - 响应状态同步请求，发送全量状态数据
	 */
	STATE_SYNC_RESPONSE(8),

	/**
	 * 遗嘱消息通知 - 客户端设置遗嘱消息时广播，用于在其他节点备份遗嘱消息
	 */
	WILL_MESSAGE(9),

	/**
	 * 保留消息通知 - 保留消息发布或清除时广播，用于同步保留消息到所有节点
	 */
	RETAIN_MESSAGE(10);

	private final int code;

	BrokerMessageType(int code) {
		this.code = code;
	}

	public static BrokerMessageType fromCode(int code) {
		for (BrokerMessageType type : values()) {
			if (type.code == code) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown message type code: " + code);
	}

	public int getCode() {
		return code;
	}
}