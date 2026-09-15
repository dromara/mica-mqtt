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

package org.dromara.mica.mqtt.broker.rule;

/**
 * ChannelContext 的薄封装，避免用户 Action 直接依赖 t-io。
 *
 * @author L.cm
 */
public final class RuleChannelInfo {

	private final String remoteIp;
	private final int remotePort;
	private final String nodeId;

	public RuleChannelInfo(String remoteIp, int remotePort, String nodeId) {
		this.remoteIp = remoteIp;
		this.remotePort = remotePort;
		this.nodeId = nodeId;
	}

	public String getRemoteIp() {
		return remoteIp;
	}

	public int getRemotePort() {
		return remotePort;
	}

	public String getNodeId() {
		return nodeId;
	}

	@Override
	public String toString() {
		return "RuleChannelInfo{" +
			"remoteIp='" + remoteIp + '\'' +
			", remotePort=" + remotePort +
			", nodeId='" + nodeId + '\'' +
			'}';
	}
}
