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

package org.dromara.mica.mqtt.broker.cluster.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for MQTT broker cluster mode.
 * <p>
 * This class encapsulates all cluster-related settings including network configuration,
 * node discovery via seed members, heartbeat intervals for failure detection, and
 * cluster identity for node verification.
 * </p>
 *
 * @author L.cm
 * @since 1.0.0
 */
public class MqttClusterConfig {
	/**
	 * Whether cluster mode is enabled.
	 * When {@code false}, the broker operates in standalone mode without cluster communication.
	 */
	private boolean enabled = false;

	/**
	 * Network address on which this node listens for cluster inter-node communication.
	 */
	private String clusterHost = "127.0.0.1";

	/**
	 * Port number on which this node listens for cluster inter-node communication.
	 */
	private int clusterPort = 9000;

	/**
	 * List of seed members used for initial cluster discovery.
	 * <p>
	 * Format: {@code host:port}. New nodes use this list to discover existing cluster members
	 * and obtain the full cluster state during bootstrap.
	 * </p>
	 */
	private List<String> seedMembers = new ArrayList<>();

	/**
	 * Logical name identifying this cluster.
	 * <p>
	 * Only nodes with the same cluster name can communicate with each other.
	 * This provides isolation between different broker clusters.
	 * </p>
	 */
	private String clusterName = "mica-mqtt-cluster";

	/**
	 * Heartbeat interval in milliseconds between cluster nodes.
	 * <p>
	 * Nodes send periodic heartbeats to indicate liveness. If a node fails to receive
	 * heartbeats from a peer within the {@link #nodeTimeout} window, the peer is
	 * considered unreachable.
	 * </p>
	 *
	 * @see #nodeTimeout
	 */
	private long heartbeatInterval = 5000;

	/**
	 * Timeout in milliseconds after which an unresponsive node is considered unreachable.
	 * <p>
	 * This value should be significantly larger than {@link #heartbeatInterval} to
	 * account for network latency and transient connectivity issues.
	 * </p>
	 *
	 * @see #heartbeatInterval
	 */
	private long nodeTimeout = 15000;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getClusterHost() {
		return clusterHost;
	}

	public void setClusterHost(String clusterHost) {
		this.clusterHost = clusterHost;
	}

	public int getClusterPort() {
		return clusterPort;
	}

	public void setClusterPort(int clusterPort) {
		this.clusterPort = clusterPort;
	}

	public List<String> getSeedMembers() {
		return seedMembers;
	}

	public void setSeedMembers(List<String> seedMembers) {
		this.seedMembers = seedMembers;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public long getHeartbeatInterval() {
		return heartbeatInterval;
	}

	public void setHeartbeatInterval(long heartbeatInterval) {
		this.heartbeatInterval = heartbeatInterval;
	}

	public long getNodeTimeout() {
		return nodeTimeout;
	}

	public void setNodeTimeout(long nodeTimeout) {
		this.nodeTimeout = nodeTimeout;
	}

	public MqttClusterConfig enabled(boolean enabled) {
		this.enabled = enabled;
		return this;
	}

	public MqttClusterConfig clusterHost(String clusterHost) {
		this.clusterHost = clusterHost;
		return this;
	}

	public MqttClusterConfig clusterPort(int clusterPort) {
		this.clusterPort = clusterPort;
		return this;
	}

	public MqttClusterConfig seedMembers(List<String> seedMembers) {
		this.seedMembers = seedMembers;
		return this;
	}

	public MqttClusterConfig clusterName(String clusterName) {
		this.clusterName = clusterName;
		return this;
	}

	public MqttClusterConfig heartbeatInterval(long heartbeatInterval) {
		this.heartbeatInterval = heartbeatInterval;
		return this;
	}

	public MqttClusterConfig nodeTimeout(long nodeTimeout) {
		this.nodeTimeout = nodeTimeout;
		return this;
	}
}