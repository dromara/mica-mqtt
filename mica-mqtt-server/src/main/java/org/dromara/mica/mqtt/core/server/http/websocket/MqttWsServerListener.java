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

package org.dromara.mica.mqtt.core.server.http.websocket;

import org.tio.core.ChannelContext;
import org.tio.core.intf.Packet;
import org.tio.server.intf.TioServerListener;
import org.tio.websocket.server.WsTioServerListener;

/**
 * mqtt websocket 监听器
 *
 * @author L.cm
 */
public class MqttWsServerListener extends WsTioServerListener {
	private final TioServerListener serverListener;

	public MqttWsServerListener(TioServerListener serverListener) {
		this.serverListener = serverListener;
	}

	@Override
	public void onAfterConnected(ChannelContext context, boolean isConnected, boolean isReconnect) throws Exception {
		super.onAfterConnected(context, isConnected, isReconnect);
		serverListener.onAfterConnected(context, isConnected, isReconnect);
	}

	@Override
	public boolean onHeartbeatTimeout(ChannelContext context, long interval, int heartbeatTimeoutCount) {
		return serverListener.onHeartbeatTimeout(context, interval, heartbeatTimeoutCount);
	}

	@Override
	public void onAfterDecoded(ChannelContext context, Packet packet, int packetSize) throws Exception {
		serverListener.onAfterDecoded(context, packet, packetSize);
	}

	@Override
	public void onAfterReceivedBytes(ChannelContext context, int receivedBytes) throws Exception {
		serverListener.onAfterReceivedBytes(context, receivedBytes);
	}

	@Override
	public void onAfterSent(ChannelContext context, Packet packet, boolean isSentSuccess) throws Exception {
		serverListener.onAfterSent(context, packet, isSentSuccess);
	}

	@Override
	public void onAfterHandled(ChannelContext context, Packet packet, long cost) throws Exception {
		serverListener.onAfterHandled(context, packet, cost);
	}

	@Override
	public void onBeforeClose(ChannelContext context, Throwable throwable, String remark, boolean isRemove) throws Exception {
		serverListener.onBeforeClose(context, throwable, remark, isRemove);
	}
}
