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

package org.dromara.mica.mqtt.broker.cluster.codec;

import org.dromara.mica.mqtt.broker.cluster.message.*;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.dromara.mica.mqtt.core.server.model.Subscribe;
import org.dromara.mica.mqtt.core.server.serializer.DefaultMessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 二进制集群消息编解码器（完全手写，无第三方依赖）
 */
public class BinaryClusterMessageCodec implements ClusterMessageCodec {
    private static final Logger logger = LoggerFactory.getLogger(BinaryClusterMessageCodec.class);

    private static final byte MAGIC = 0x0C;
    private static final DefaultMessageSerializer SERIALIZER = DefaultMessageSerializer.INSTANCE;

    @Override
    public byte[] encode(ClusterMessage msg) {
        int bodyLength = calculateBodyLength(msg);
        int varintLengthSize = varintSize(bodyLength);

        ByteBuffer buf = ByteBuffer.allocate(1 + 1 + varintLengthSize + bodyLength);
        buf.put(MAGIC);
        buf.put((byte) msg.getType().ordinal());
        writeVarint(buf, bodyLength);
        encodeBodyToBuffer(msg, buf);

        byte[] result = new byte[buf.position()];
        buf.flip();
        buf.get(result);
        return result;
    }

    private int calculateBodyLength(ClusterMessage msg) {
        ClusterMessageType type = msg.getType();
        switch (type) {
            case CLIENT_CONNECT:
                return calculateStringLength(((ClientConnectMessage) msg).getClientId());
            case CLIENT_DISCONNECT:
                return calculateStringLength(((ClientDisconnectMessage) msg).getClientId());
            case SUBSCRIBE_NOTIFY:
                return calculateSubscribeNotifyLength((SubscribeNotifyMessage) msg);
            case UNSUBSCRIBE_NOTIFY:
                return calculateUnsubscribeNotifyLength((UnsubscribeNotifyMessage) msg);
            case PUBLISH_FORWARD:
                Message m = ((PublishForwardMessage) msg).getMessage();
                return SERIALIZER.serialize(m).length;
            case NODE_LEAVE:
            case STATE_SYNC_REQUEST:
                return 0;
            case STATE_SYNC_RESPONSE:
                return calculateStateSyncResponseLength((StateSyncResponseMessage) msg);
            default:
                throw new IllegalArgumentException("Unknown message type: " + type);
        }
    }

    private void encodeBodyToBuffer(ClusterMessage msg, ByteBuffer buf) {
        ClusterMessageType type = msg.getType();
        switch (type) {
            case CLIENT_CONNECT:
                writeString(buf, ((ClientConnectMessage) msg).getClientId());
                break;
            case CLIENT_DISCONNECT:
                writeString(buf, ((ClientDisconnectMessage) msg).getClientId());
                break;
            case SUBSCRIBE_NOTIFY:
                encodeSubscribeNotify(buf, (SubscribeNotifyMessage) msg);
                break;
            case UNSUBSCRIBE_NOTIFY:
                encodeUnsubscribeNotify(buf, (UnsubscribeNotifyMessage) msg);
                break;
            case PUBLISH_FORWARD:
                Message m = ((PublishForwardMessage) msg).getMessage();
                buf.put(SERIALIZER.serialize(m));
                break;
            case STATE_SYNC_RESPONSE:
                encodeStateSyncResponse(buf, (StateSyncResponseMessage) msg);
                break;
            default:
                break;
        }
    }

    @Override
    public ClusterMessage decode(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);

        byte magic = buf.get();
        if (magic != MAGIC) {
            throw new IllegalArgumentException("Invalid magic: " + magic);
        }

        byte typeOrdinal = buf.get();
        ClusterMessageType type = ClusterMessageType.values()[typeOrdinal];

        int length = readVarint(buf);

        byte[] body = new byte[length];
        buf.get(body);

        return decodeBody(type, ByteBuffer.wrap(body));
    }

    private ClusterMessage decodeBody(ClusterMessageType type, ByteBuffer buf) {
        switch (type) {
            case CLIENT_CONNECT:
                return decodeClientConnectMessage(buf);
            case CLIENT_DISCONNECT:
                return decodeClientDisconnectMessage(buf);
            case SUBSCRIBE_NOTIFY:
                return decodeSubscribeNotifyMessage(buf);
            case UNSUBSCRIBE_NOTIFY:
                return decodeUnsubscribeNotifyMessage(buf);
            case PUBLISH_FORWARD:
                return decodePublishForwardMessage(buf);
            case NODE_LEAVE:
            case STATE_SYNC_REQUEST:
                return decodeGenericMessage(type);
            case STATE_SYNC_RESPONSE:
                return decodeStateSyncResponseMessage(buf);
            default:
                throw new IllegalArgumentException("Unknown message type: " + type);
        }
    }

    // ==================== 编码长度计算 ====================

    private int calculateStringLength(String s) {
        if (s == null) return 2;
        return 2 + s.getBytes(StandardCharsets.UTF_8).length;
    }

    private int calculateSubscribeNotifyLength(SubscribeNotifyMessage msg) {
        int len = 0;
        len += calculateStringLength(msg.getClientId());
        len += calculateStringLength(msg.getNodeId());
        List<Subscribe> subs = msg.getSubscriptions();
        if (subs == null) {
            len += 1;
        } else {
            len += varintSize(subs.size());
            for (Subscribe sub : subs) {
                len += calculateStringLength(sub.getTopicFilter());
                len += calculateStringLength(sub.getClientId());
                len += 2;
            }
        }
        return len;
    }

    private int calculateUnsubscribeNotifyLength(UnsubscribeNotifyMessage msg) {
        int len = 0;
        len += calculateStringLength(msg.getClientId());
        len += calculateStringLength(msg.getNodeId());
        List<String> topics = msg.getTopics();
        if (topics == null) {
            len += 1;
        } else {
            len += varintSize(topics.size());
            for (String topic : topics) {
                len += calculateStringLength(topic);
            }
        }
        return len;
    }

    private int calculateStateSyncResponseLength(StateSyncResponseMessage msg) {
        int len = 0;
        Map<String, String> clientNodeMap = msg.getClientNodeMap();
        if (clientNodeMap == null) {
            len += 1;
        } else {
            len += varintSize(clientNodeMap.size());
            for (Map.Entry<String, String> entry : clientNodeMap.entrySet()) {
                len += calculateStringLength(entry.getKey());
                len += calculateStringLength(entry.getValue());
            }
        }

        Map<String, List<Subscribe>> subMap = msg.getSubscriptionMap();
        if (subMap == null) {
            len += 1;
        } else {
            len += varintSize(subMap.size());
            for (Map.Entry<String, List<Subscribe>> entry : subMap.entrySet()) {
                len += calculateStringLength(entry.getKey());
                List<Subscribe> subs = entry.getValue();
                if (subs == null) {
                    len += 1;
                } else {
                    len += varintSize(subs.size());
                    for (Subscribe sub : subs) {
                        len += calculateStringLength(sub.getTopicFilter());
                        len += calculateStringLength(sub.getClientId());
                        len += 2;
                    }
                }
            }
        }
        return len;
    }

    // ==================== 编码方法 ====================

    private void encodeSubscribeNotify(ByteBuffer buf, SubscribeNotifyMessage msg) {
        writeString(buf, msg.getClientId());
        writeString(buf, msg.getNodeId());
        List<Subscribe> subs = msg.getSubscriptions();
        if (subs == null) {
            writeVarint(buf, 0);
        } else {
            writeVarint(buf, subs.size());
            for (Subscribe sub : subs) {
                writeString(buf, sub.getTopicFilter());
                writeString(buf, sub.getClientId());
                buf.put((byte) sub.getMqttQoS());
                buf.put(sub.isNoLocal() ? (byte) 1 : (byte) 0);
            }
        }
    }

    private void encodeUnsubscribeNotify(ByteBuffer buf, UnsubscribeNotifyMessage msg) {
        writeString(buf, msg.getClientId());
        writeString(buf, msg.getNodeId());
        List<String> topics = msg.getTopics();
        if (topics == null) {
            writeVarint(buf, 0);
        } else {
            writeVarint(buf, topics.size());
            for (String topic : topics) {
                writeString(buf, topic);
            }
        }
    }

    private void encodeStateSyncResponse(ByteBuffer buf, StateSyncResponseMessage msg) {
        Map<String, String> clientNodeMap = msg.getClientNodeMap();
        if (clientNodeMap == null) {
            writeVarint(buf, 0);
        } else {
            writeVarint(buf, clientNodeMap.size());
            for (Map.Entry<String, String> entry : clientNodeMap.entrySet()) {
                writeString(buf, entry.getKey());
                writeString(buf, entry.getValue());
            }
        }

        Map<String, List<Subscribe>> subMap = msg.getSubscriptionMap();
        if (subMap == null) {
            writeVarint(buf, 0);
        } else {
            writeVarint(buf, subMap.size());
            for (Map.Entry<String, List<Subscribe>> entry : subMap.entrySet()) {
                writeString(buf, entry.getKey());
                List<Subscribe> subs = entry.getValue();
                if (subs == null) {
                    writeVarint(buf, 0);
                } else {
                    writeVarint(buf, subs.size());
                    for (Subscribe sub : subs) {
                        writeString(buf, sub.getTopicFilter());
                        writeString(buf, sub.getClientId());
                        buf.put((byte) sub.getMqttQoS());
                        buf.put(sub.isNoLocal() ? (byte) 1 : (byte) 0);
                    }
                }
            }
        }
    }

    // ==================== 解码方法 ====================

    private ClientConnectMessage decodeClientConnectMessage(ByteBuffer buf) {
        ClientConnectMessage msg = new ClientConnectMessage();
        msg.setClientId(readString(buf));
        return msg;
    }

    private ClientDisconnectMessage decodeClientDisconnectMessage(ByteBuffer buf) {
        ClientDisconnectMessage msg = new ClientDisconnectMessage();
        msg.setClientId(readString(buf));
        return msg;
    }

    private SubscribeNotifyMessage decodeSubscribeNotifyMessage(ByteBuffer buf) {
        SubscribeNotifyMessage msg = new SubscribeNotifyMessage();
        msg.setClientId(readString(buf));
        msg.setNodeId(readString(buf));
        msg.setSubscriptions(readSubscribeList(buf));
        return msg;
    }

    private UnsubscribeNotifyMessage decodeUnsubscribeNotifyMessage(ByteBuffer buf) {
        UnsubscribeNotifyMessage msg = new UnsubscribeNotifyMessage();
        msg.setClientId(readString(buf));
        msg.setNodeId(readString(buf));
        msg.setTopics(readStringList(buf));
        return msg;
    }

    private PublishForwardMessage decodePublishForwardMessage(ByteBuffer buf) {
        byte[] body = new byte[buf.remaining()];
        buf.get(body);
        PublishForwardMessage msg = new PublishForwardMessage();
        msg.setMessage(SERIALIZER.deserialize(body));
        return msg;
    }

    private StateSyncResponseMessage decodeStateSyncResponseMessage(ByteBuffer buf) {
        StateSyncResponseMessage msg = new StateSyncResponseMessage();
        msg.setClientNodeMap(readStringStringMap(buf));
        msg.setSubscriptionMap(readSubscriptionMap(buf));
        return msg;
    }

    private GenericClusterMessage decodeGenericMessage(ClusterMessageType type) {
        return new GenericClusterMessage(type);
    }

    // ==================== 基础方法 ====================

    private void writeVarint(ByteBuffer buf, int value) {
        while (true) {
            int b = value & 0x7F;
            value >>>= 7;
            if (value == 0) {
                buf.put((byte) b);
                break;
            }
            buf.put((byte) (b | 0x80));
        }
    }

    private void writeString(ByteBuffer buf, String s) {
        if (s == null) {
            buf.putShort((short) -1);
            return;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }

    private int readVarint(ByteBuffer buf) {
        int result = 0;
        int shift = 0;
        while (true) {
            byte b = buf.get();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        }
        return result;
    }

    private String readString(ByteBuffer buf) {
        short len = buf.getShort();
        if (len == -1) {
            return null;
        }
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private List<String> readStringList(ByteBuffer buf) {
        int size = readVarint(buf);
        if (size == 0) {
            return null;
        }
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(readString(buf));
        }
        return list;
    }

    private List<Subscribe> readSubscribeList(ByteBuffer buf) {
        int size = readVarint(buf);
        if (size == 0) {
            return null;
        }
        List<Subscribe> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(readSubscribe(buf));
        }
        return list;
    }

    private Subscribe readSubscribe(ByteBuffer buf) {
        Subscribe sub = new Subscribe();
        sub.setTopicFilter(readString(buf));
        sub.setClientId(readString(buf));
        sub.setMqttQoS(buf.get() & 0xFF);
        sub.setNoLocal(buf.get() == 1);
        return sub;
    }

    private Map<String, String> readStringStringMap(ByteBuffer buf) {
        int size = readVarint(buf);
        if (size == 0) {
            return null;
        }
        Map<String, String> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = readString(buf);
            String value = readString(buf);
            map.put(key, value);
        }
        return map;
    }

    private Map<String, List<Subscribe>> readSubscriptionMap(ByteBuffer buf) {
        int size = readVarint(buf);
        if (size == 0) {
            return null;
        }
        Map<String, List<Subscribe>> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            String key = readString(buf);
            List<Subscribe> value = readSubscribeList(buf);
            map.put(key, value);
        }
        return map;
    }

    private int varintSize(int value) {
        if (value < 0) {
            return 5;
        }
        if (value < 128) {
            return 1;
        }
        if (value < 16384) {
            return 2;
        }
        if (value < 2097152) {
            return 3;
        }
        return 4;
    }
}
