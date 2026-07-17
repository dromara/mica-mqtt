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

package org.dromara.mica.mqtt.broker.cluster.core;

import net.dreamlu.mica.net.client.TioClient;
import net.dreamlu.mica.net.client.TioClientConfig;
import net.dreamlu.mica.net.client.intf.TioClientHandler;
import net.dreamlu.mica.net.core.ChannelContext;
import net.dreamlu.mica.net.core.Node;
import net.dreamlu.mica.net.core.TioConfig;
import net.dreamlu.mica.net.core.exception.TioDecodeException;
import net.dreamlu.mica.net.core.intf.Packet;
import net.dreamlu.mica.net.server.cluster.core.ClusterApi;
import net.dreamlu.mica.net.server.cluster.core.ClusterImpl;
import net.dreamlu.mica.net.server.cluster.message.ClusterDataMessage;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Adds application-data handling to mica-net's cluster client connections.
 * <p>
 * mica-net 2.0.11 dispatches {@link ClusterDataMessage} only in its server-side
 * handler. A node that initiated a seed connection can therefore send data to
 * the seed, but cannot consume data returned on the same TCP connection. This
 * adapter keeps mica-net's codec, heartbeat and synchronous-ACK handling while
 * delivering application data received on client-side channels as well.
 * </p>
 */
final class BidirectionalClusterClientHandler implements TioClientHandler {
	private static final String CLIENT_FIELD = "tcpClusterClient";
	private static final String MEMBER_CHANNELS_FIELD = "memberChannels";

	private final TioClientHandler delegate;
	private final Consumer<ClusterDataMessage> messageConsumer;

	private BidirectionalClusterClientHandler(TioClientHandler delegate,
									  Consumer<ClusterDataMessage> messageConsumer) {
		this.delegate = delegate;
		this.messageConsumer = messageConsumer;
	}

	static void install(ClusterApi cluster, Consumer<ClusterDataMessage> messageConsumer) {
		if (!(cluster instanceof ClusterImpl)) {
			throw new IllegalStateException("Unsupported mica-net ClusterApi: " + cluster.getClass().getName());
		}
		try {
			Field clientField = ClusterImpl.class.getDeclaredField(CLIENT_FIELD);
			clientField.setAccessible(true);
			TioClient client = (TioClient) clientField.get(cluster);
			TioClientConfig clientConfig = client.getClientConfig();
			TioClientHandler current = clientConfig.getTioClientHandler();
			if (!(current instanceof BidirectionalClusterClientHandler)) {
				clientConfig.setTioClientHandler(
					new BidirectionalClusterClientHandler(current, messageConsumer));
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
				"mica-net cluster client internals changed; bidirectional transport cannot be installed", e);
		}
	}

	static Set<String> directMemberIds(ClusterApi cluster) {
		Set<String> memberIds = new HashSet<>();
		if (cluster == null) {
			return memberIds;
		}
		if (!(cluster instanceof ClusterImpl)) {
			throw new IllegalStateException("Unsupported mica-net ClusterApi: " + cluster.getClass().getName());
		}
		try {
			Field channelsField = ClusterImpl.class.getDeclaredField(MEMBER_CHANNELS_FIELD);
			channelsField.setAccessible(true);
			@SuppressWarnings("unchecked")
			Map<Node, ChannelContext> channels = (Map<Node, ChannelContext>) channelsField.get(cluster);
			for (Map.Entry<Node, ChannelContext> entry : channels.entrySet()) {
				ChannelContext context = entry.getValue();
				if (context != null && !context.isClosed()) {
					memberIds.add(entry.getKey().getPeerHost());
				}
			}
			return memberIds;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
				"mica-net cluster internals changed; direct topology cannot be inspected", e);
		}
	}

	@Override
	public Packet heartbeatPacket(ChannelContext context) {
		return delegate.heartbeatPacket(context);
	}

	@Override
	public Packet decode(ByteBuffer buffer, int limit, int position, int readableLength,
					 ChannelContext context) throws TioDecodeException {
		return delegate.decode(buffer, limit, position, readableLength, context);
	}

	@Override
	public ByteBuffer encode(Packet packet, TioConfig config, ChannelContext context) {
		return delegate.encode(packet, config, context);
	}

	@Override
	public void handler(Packet packet, ChannelContext context) throws Exception {
		if (packet instanceof ClusterDataMessage) {
			messageConsumer.accept((ClusterDataMessage) packet);
			return;
		}
		delegate.handler(packet, context);
	}
}
