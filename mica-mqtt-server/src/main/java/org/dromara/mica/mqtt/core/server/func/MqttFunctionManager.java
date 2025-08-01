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

package org.dromara.mica.mqtt.core.server.func;

import org.dromara.mica.mqtt.core.util.TopicUtil;
import org.tio.utils.hutool.CollUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * mqtt 服务端的函数管理
 *
 * @author L.cm
 */
public class MqttFunctionManager {
	/**
	 * root 节点
	 */
	private final Node root = new Node("root", null);

	private static class Node {
		/**
		 * topic 片段
		 */
		private final String part;
		/**
		 * 监听器集合
		 */
		private final List<IMqttFunctionMessageListener> listeners;
		/**
		 * 子节点
		 */
		private final Map<String, Node> children;

		public Node(String part) {
			this(part, new CopyOnWriteArrayList<>());
		}

		public Node(String part, List<IMqttFunctionMessageListener> listeners) {
			this(part, listeners, new ConcurrentHashMap<>(16));
		}

		public Node(String part, List<IMqttFunctionMessageListener> listeners, Map<String, Node> children) {
			this.part = part;
			this.listeners = listeners;
			this.children = children;
		}

		/**
		 * 获取或者添加节点
		 *
		 * @param nodePart nodePart
		 * @return Node
		 */
		protected Node addChildIfAbsent(String nodePart) {
			assert children != null;
			return CollUtil.computeIfAbsent(this.children, nodePart, Node::new);
		}

		protected Node findNodeByPart(String nodePart) {
			assert children != null;
			return children.get(nodePart);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Node node = (Node) o;
			return Objects.equals(part, node.part);
		}

		@Override
		public int hashCode() {
			return part != null ? part.hashCode() : 0;
		}

		@Override
		public String toString() {
			return "Node{" +
				"part='" + part + '\'' +
				'}';
		}
	}

	/**
	 * 注册监听
	 *
	 * @param topicFilters topicFilter array
	 * @param listener     listener
	 */
	public void register(String[] topicFilters, IMqttFunctionMessageListener listener) {
		for (String topicFilter : topicFilters) {
			this.register(topicFilter, listener);
		}
	}

	/**
	 * 注册监听
	 *
	 * @param topicFilter topicFilter
	 * @param listener    listener
	 */
	public void register(String topicFilter, IMqttFunctionMessageListener listener) {
		Node prev = root;
		String[] topicParts = TopicUtil.getTopicParts(topicFilter);
		int partLength = topicParts.length - 1;
		for (int i = 0; i < topicParts.length; i++) {
			prev = prev.addChildIfAbsent(topicParts[i]);
			// 判断是否结尾，添加订阅数据
			boolean isEnd = i == partLength;
			if (isEnd) {
				// 添加监听器
				prev.listeners.add(listener);
			}
		}
	}

	/**
	 * 获取监听器
	 *
	 * @param topic topic
	 * @return 监听器集合
	 */
	public List<IMqttFunctionMessageListener> get(String topic) {
		List<IMqttFunctionMessageListener> listenerList = new ArrayList<>();
		// 这里都是完整的 topic，利用完整的 topic 获取到匹配的监听器
		String[] topicParts = TopicUtil.getTopicParts(topic);
		searchListenerRecursively(root, listenerList, topicParts, 0);
		return listenerList;
	}

	/**
	 * 递归查找监听器
	 *
	 * @param node         node
	 * @param listenerList listener list
	 * @param topicParts   topic parts
	 * @param index        index
	 */
	private static void searchListenerRecursively(Node node, List<IMqttFunctionMessageListener> listenerList, String[] topicParts, int index) {
		// 层级已经超过，跳出
		if (index >= topicParts.length) {
			return;
		}
		// # 单独处理
		Node nodeMore = node.findNodeByPart(TopicUtil.TOPIC_WILDCARDS_MORE);
		if (nodeMore != null) {
			listenerList.addAll(nodeMore.listeners);
		}
		int topicPartLen = topicParts.length - 1;
		// + 处理
		Node nodeOne = node.findNodeByPart(TopicUtil.TOPIC_WILDCARDS_ONE);
		if (nodeOne != null) {
			// 最后一位为 +
			if (index == topicPartLen) {
				listenerList.addAll(nodeOne.listeners);
			} else {
				searchListenerRecursively(nodeOne, listenerList, topicParts, index + 1);
			}
		}
		String topicPart = topicParts[index];
		Node nodePart = node.findNodeByPart(topicPart);
		if (nodePart != null) {
			// 跳出循环
			if (index == topicPartLen) {
				listenerList.addAll(nodePart.listeners);
				// 判断是否还有 #
				Node nodePartMore = nodePart.findNodeByPart(TopicUtil.TOPIC_WILDCARDS_MORE);
				if (nodePartMore != null) {
					listenerList.addAll(nodePartMore.listeners);
				}
			} else {
				searchListenerRecursively(nodePart, listenerList, topicParts, index + 1);
			}
		}
	}

}
