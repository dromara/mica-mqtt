package org.dromara.mica.mqtt.broker.cluster;

import java.util.ArrayList;
import java.util.List;

/**
 * Mqtt Cluster Configuration
 */
public class MqttClusterConfig {
    // 是否启用集群
    private boolean enabled = false;

    // 集群监听地址和端口（用于集群节点间通信）
    private String clusterHost = "127.0.0.1";
    private int clusterPort = 9000;

    // 种子节点列表（格式：host:port）
    private List<String> seedMembers = new ArrayList<>();

    // 集群名称（相同集群名称的节点才能互联）
    private String clusterName = "mica-mqtt-cluster";

    // 集群心跳间隔（毫秒）
    private long heartbeatInterval = 5000;

    // 节点失联超时（毫秒）
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
