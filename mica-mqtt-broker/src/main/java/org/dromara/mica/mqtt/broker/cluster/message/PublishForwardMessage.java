package org.dromara.mica.mqtt.broker.cluster.message;

public class PublishForwardMessage extends ClusterMessage {
    private String topic;           // 主题
    private byte[] payload;         // 消息体
    private int qos;                // QoS 级别
    private boolean retain;         // 是否保留

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public byte[] getPayload() {
        return payload;
    }

    public void setPayload(byte[] payload) {
        this.payload = payload;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public boolean isRetain() {
        return retain;
    }

    public void setRetain(boolean retain) {
        this.retain = retain;
    }
}
