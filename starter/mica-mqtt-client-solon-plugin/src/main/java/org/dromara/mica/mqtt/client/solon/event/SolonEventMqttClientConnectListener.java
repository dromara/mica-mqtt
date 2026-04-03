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

package org.dromara.mica.mqtt.client.solon.event;

import lombok.extern.slf4j.Slf4j;
import net.dreamlu.mica.net.core.ChannelContext;
import org.dromara.mica.mqtt.core.client.IMqttClientConnectListener;
import org.noear.solon.core.event.EventBus;

/**
 * spring event mqtt client 连接监听
 *
 * @author L.cm
 */
@Slf4j
public class SolonEventMqttClientConnectListener implements IMqttClientConnectListener {

	@Override
	public void onConnected(ChannelContext context, boolean isReconnect) {
		if (isReconnect) {
			log.info("重连 mqtt 服务器重连成功...");
		} else {
			log.info("连接 mqtt 服务器成功...");
		}
		EventBus.publish(new MqttConnectedEvent(isReconnect));
	}

	@Override
	public void onDisconnect(ChannelContext channelContext, Throwable throwable, String remark, boolean isRemove) {
		String reason;
		if (throwable == null) {
			reason = remark;
			log.info("mqtt 链接断开 remark:{} isRemove:{}", remark, isRemove);
		} else {
			reason = remark + " Exception:" + throwable.getMessage();
			log.error("mqtt 链接断开 remark:{} isRemove:{}", remark, isRemove, throwable);
		}
		EventBus.publish(new MqttDisconnectEvent(reason, isRemove));
	}

}
