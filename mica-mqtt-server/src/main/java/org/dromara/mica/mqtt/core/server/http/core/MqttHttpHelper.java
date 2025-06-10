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

package org.dromara.mica.mqtt.core.server.http.core;

import org.tio.core.ChannelContext;
import org.tio.core.Tio;
import org.tio.core.TioConfig;
import org.tio.core.intf.Packet;
import org.tio.http.common.HttpConst;
import org.tio.http.common.HttpRequest;
import org.tio.http.common.HttpResponse;

/**
 * http 工具
 *
 * @author L.cm
 */
public class MqttHttpHelper {

	/**
	 * 数据清理
	 *
	 * @param context ChannelContext
	 * @param packet  Packet
	 */
	public static void close(ChannelContext context, Packet packet) {
		// 1. 短链接数据解绑
		TioConfig tioConfig = context.getTioConfig();
		tioConfig.groups.unbind(context);
		tioConfig.bsIds.unbind(context);
		tioConfig.ids.unbind(context);
		tioConfig.clientNodes.remove(context);
		tioConfig.tokens.unbind(context);
		// 2. 关闭 http，sse 的包有可能会到这
		if (packet instanceof HttpResponse) {
			HttpRequest request = ((HttpResponse) packet).getHttpRequest();
			if (request != null) {
				if (request.httpConfig.compatible1_0) {
					if (HttpConst.HttpVersion.V1_0.equals(request.requestLine.version)) {
						if (!HttpConst.RequestHeaderValue.Connection.keep_alive.equals(request.getConnection())) {
							Tio.remove(context, "http 请求头Connection!=keep-alive：" + request.getRequestLine());
						}
					} else {
						if (HttpConst.RequestHeaderValue.Connection.close.equals(request.getConnection())) {
							Tio.remove(context, "http 请求头Connection=close：" + request.getRequestLine());
						}
					}
				} else {
					if (HttpConst.RequestHeaderValue.Connection.close.equals(request.getConnection())) {
						Tio.remove(context, "http 请求头Connection=close：" + request.getRequestLine());
					}
				}
			}
		}
	}

}
