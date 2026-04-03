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

package org.dromara.mica.mqtt.spring.client.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dreamlu.mica.net.core.ChannelContext;

import java.io.Serializable;

/**
 * mqtt 客户端断开连接事件
 *
 * @author L.cm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MqttDisconnectEvent implements Serializable {

	/**
	 * context
	 */
	private ChannelContext context;
	/**
	 * 断开原因
	 */
	private String reason;
	/**
	 * 是否删除连接
	 */
	private boolean isRemove;

}
