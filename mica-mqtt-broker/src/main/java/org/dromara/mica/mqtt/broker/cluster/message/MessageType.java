package org.dromara.mica.mqtt.broker.cluster.message;

public enum MessageType {
    CLIENT_CONNECT,        // 客户端连接通知
    CLIENT_DISCONNECT,     // 客户端断开通知
    SUBSCRIBE_NOTIFY,      // 订阅通知
    UNSUBSCRIBE_NOTIFY,    // 取消订阅通知
    PUBLISH_FORWARD,       // 消息转发
    HEARTBEAT,             // 心跳
    NODE_JOIN,             // 节点加入
    NODE_LEAVE,            // 节点离开
    STATE_SYNC_REQUEST,    // 状态同步请求（新节点加入时请求全局路由表）
    STATE_SYNC_RESPONSE    // 状态同步响应
}
