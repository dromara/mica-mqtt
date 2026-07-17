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

package org.dromara.mica.mqtt.broker.cluster;

import org.dromara.mica.mqtt.broker.cluster.config.MqttClusterConfig;
import org.dromara.mica.mqtt.broker.cluster.config.MqttStorageConfig;
import org.dromara.mica.mqtt.broker.cluster.core.ClusterStorage;
import org.dromara.mica.mqtt.broker.cluster.core.MqttClusterManager;
import org.dromara.mica.mqtt.broker.cluster.message.StateSyncRequestMessage;
import org.dromara.mica.mqtt.broker.cluster.store.SessionStore;
import org.dromara.mica.mqtt.broker.cluster.store.SharedSubStore;
import org.dromara.mica.mqtt.core.server.model.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in, independent-process membership and transport chaos acceptance test.
 * Run with {@code mvn -pl mica-mqtt-broker -Pcluster-chaos -DforkCount=0 test}.
 */
class ClusterProcessChaosIT {
	@TempDir
	Path tempDir;

	@Test
	void threeNodeKillAndRejoinConverges() throws Exception {
		runKillAndRejoinScenario(3);
	}

	@Test
	void fiveNodeKillAndRejoinConverges() throws Exception {
		runKillAndRejoinScenario(5);
	}

	private void runKillAndRejoinScenario(int nodeCount) throws Exception {
		List<Integer> ports = reservePorts(nodeCount);
		Path scenarioDir = Files.createDirectories(tempDir.resolve("nodes-" + nodeCount));
		List<NodeProcess> nodes = new ArrayList<>();
		try {
			for (int i = 0; i < ports.size(); i++) {
				int port = ports.get(i);
				nodes.add(NodeProcess.start(port, ports, scenarioDir.resolve("node-" + i)));
			}
			for (NodeProcess node : nodes) {
				node.awaitLine("READY", Duration.ofSeconds(15));
			}
			awaitMembership(nodes, nodeCount - 1, Duration.ofSeconds(20));

			nodes.get(0).markMessages();
			nodes.get(1).sendTo(nodes.get(0));
			nodes.get(0).awaitMessage(Duration.ofSeconds(10));

			NodeProcess killed = nodes.remove(nodes.size() - 1);
			int killedPort = killed.port;
			killed.writeState();
			killed.kill();
			awaitMembership(nodes, nodeCount - 2, Duration.ofSeconds(20));

			NodeProcess replacement = NodeProcess.start(killedPort, ports, killed.dataDir);
			nodes.add(replacement);
			replacement.awaitLine("READY", Duration.ofSeconds(15));
			replacement.verifyState();
			awaitMembership(nodes, nodeCount - 1, Duration.ofSeconds(20));

			nodes.get(0).markMessages();
			replacement.sendTo(nodes.get(0));
			nodes.get(0).awaitMessage(Duration.ofSeconds(10));
		} finally {
			for (NodeProcess node : nodes) {
				node.close();
			}
		}
	}

	private static void awaitMembership(List<NodeProcess> nodes, int expected, Duration timeout)
		throws Exception {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			boolean converged = true;
			for (NodeProcess node : nodes) {
				node.status();
				if (!node.awaitLine("MEMBERS " + expected, Duration.ofMillis(800), false)) {
					converged = false;
				}
			}
			if (converged) {
				return;
			}
			Thread.sleep(200L);
		}
		throw new AssertionError("Cluster membership did not converge to " + expected
			+ ", output=" + nodes);
	}

	private static List<Integer> reservePorts(int count) throws IOException {
		List<Integer> ports = new ArrayList<>(count);
		List<ServerSocket> sockets = new ArrayList<>(count);
		try {
			for (int i = 0; i < count; i++) {
				ServerSocket socket = new ServerSocket(0);
				sockets.add(socket);
				ports.add(socket.getLocalPort());
			}
		} finally {
			for (ServerSocket socket : sockets) {
				socket.close();
			}
		}
		return ports;
	}

	public static class ClusterWorker {
		public static void main(String[] args) throws Exception {
			int port = Integer.parseInt(args[0]);
			String[] seedPorts = args[1].split(",");
			Path dataDir = Paths.get(args[2]);
			List<String> seeds = new ArrayList<>();
			for (String seedPort : seedPorts) {
				seeds.add("127.0.0.1:" + Integer.parseInt(seedPort));
			}
			MqttClusterConfig config = new MqttClusterConfig()
				.enabled(true)
				.clusterHost("127.0.0.1")
				.clusterPort(port)
				.seedMembers(seeds)
				.heartbeatInterval(1_000L)
				.nodeTimeout(3_000L);
			MqttClusterManager manager = new MqttClusterManager(config, "127.0.0.1:" + port);
			ClusterStorage storage = new ClusterStorage(new MqttStorageConfig()
				.enabled(true).dataDir(dataDir.toString()));
			if (!storage.start()) {
				throw new IllegalStateException("Failed to start storage at " + dataDir);
			}
			manager.setClusterStorage(storage);
			manager.start();
			System.out.println("READY");
			long messageMark = manager.getMetrics().getClusterMessagesReceived();
			try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
				String command;
				while ((command = input.readLine()) != null) {
					if (command.equals("STATUS")) {
						System.out.println("MEMBERS " + (manager.getClusterNodeIds().size() - 1));
					} else if (command.equals("MARK")) {
						messageMark = manager.getMetrics().getClusterMessagesReceived();
						System.out.println("MARKED");
					} else if (command.equals("CHECK")) {
						if (manager.getMetrics().getClusterMessagesReceived() > messageMark) {
							System.out.println("MESSAGE_RECEIVED");
						}
					} else if (command.startsWith("SEND ")) {
						manager.sendToNode("127.0.0.1:" + command.substring("SEND ".length()),
							new StateSyncRequestMessage());
					} else if (command.equals("WRITE_STATE")) {
						writeState(storage, port);
						System.out.println("STATE_WRITTEN");
					} else if (command.equals("VERIFY_STATE")) {
						System.out.println(verifyState(storage) ? "STATE_OK" : "STATE_MISSING");
					} else if (command.equals("STOP")) {
						break;
					}
				}
			} finally {
				manager.stop();
				storage.stop();
			}
		}

		private static void writeState(ClusterStorage storage, int port) throws Exception {
			storage.getSessionStore().save("chaos-client", new SessionStore.Session(
				"chaos-client", Collections.emptyList(), false, 3_600L, "127.0.0.1:" + port));
			storage.getSharedSubStore().save(new SharedSubStore.SharedSubGroup(
				"chaos-group", "chaos/topic", Collections.singletonList("chaos-client"),
				"127.0.0.1:" + port, null, 1L, System.currentTimeMillis()));
			Message retained = new Message();
			retained.setTopic("chaos/retain");
			retained.setPayload(new byte[]{7});
			retained.setQos(1);
			retained.setRetain(true);
			storage.getRetainIndex().put("chaos/retain", retained);
			storage.getInflightStore().put("chaos-client", 7, System.currentTimeMillis() + 60_000L,
				"chaos/inflight", new byte[]{9}, 1);
			long deadline = System.currentTimeMillis() + 5_000L;
			while (storage.getInflightStore().count() != 1L && System.currentTimeMillis() < deadline) {
				Thread.yield();
			}
			if (storage.getInflightStore().count() != 1L) {
				throw new IllegalStateException("Inflight state was not persisted");
			}
		}

		private static boolean verifyState(ClusterStorage storage) {
			return storage.getSessionStore().load("chaos-client") != null
				&& storage.getSharedSubStore().get("chaos-group", "chaos/topic") != null
				&& storage.getRetainIndex().match("chaos/retain").size() == 1
				&& storage.getInflightStore().listByClient("chaos-client").size() == 1;
		}
	}

	private static class NodeProcess implements AutoCloseable {
		private final int port;
		private final Path dataDir;
		private final Process process;
		private final BufferedWriter input;
		private final List<String> output = new CopyOnWriteArrayList<>();

		private NodeProcess(int port, Path dataDir, Process process) {
			this.port = port;
			this.dataDir = dataDir;
			this.process = process;
			this.input = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
			Thread reader = new Thread(() -> readOutput(process), "cluster-worker-output-" + port);
			reader.setDaemon(true);
			reader.start();
		}

		private static NodeProcess start(int port, List<Integer> seedPorts, Path dataDir) throws IOException {
			Path java = Paths.get(System.getProperty("java.home"), "bin",
				isWindows() ? "java.exe" : "java");
			StringBuilder seeds = new StringBuilder();
			for (int seedPort : seedPorts) {
				if (seeds.length() > 0) {
					seeds.append(',');
				}
				seeds.append(seedPort);
			}
			Process process = new ProcessBuilder(
				java.toString(), "-cp", buildClasspath(),
				ClusterWorker.class.getName(), String.valueOf(port), seeds.toString(), dataDir.toString())
				.redirectErrorStream(true)
				.start();
			return new NodeProcess(port, dataDir, process);
		}

		private static String buildClasspath() {
			LinkedHashSet<String> entries = new LinkedHashSet<>();
			String configured = System.getProperty("java.class.path", "");
			for (String entry : configured.split(java.io.File.pathSeparator)) {
				if (!entry.isEmpty()) {
					entries.add(entry);
				}
			}
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			while (loader != null) {
				if (loader instanceof URLClassLoader) {
					for (URL url : ((URLClassLoader) loader).getURLs()) {
						if ("file".equals(url.getProtocol())) {
							try {
								entries.add(Paths.get(url.toURI()).toString());
							} catch (Exception ignored) {
								entries.add(url.getPath());
							}
						}
					}
				}
				loader = loader.getParent();
			}
			return String.join(java.io.File.pathSeparator, entries);
		}

		private void readOutput(Process process) {
			try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					output.add(line);
				}
			} catch (IOException e) {
				output.add("READER_ERROR " + e.getMessage());
			}
		}

		private void sendTo(NodeProcess target) throws IOException {
			send("SEND " + target.port);
		}

		private void markMessages() throws Exception {
			send("MARK");
			awaitLine("MARKED", Duration.ofSeconds(2));
		}

		private void writeState() throws Exception {
			send("WRITE_STATE");
			awaitLine("STATE_WRITTEN", Duration.ofSeconds(10));
		}

		private void verifyState() throws Exception {
			send("VERIFY_STATE");
			awaitLine("STATE_OK", Duration.ofSeconds(10));
		}

		private void awaitMessage(Duration timeout) throws Exception {
			long deadline = System.nanoTime() + timeout.toNanos();
			while (System.nanoTime() < deadline) {
				send("CHECK");
				if (awaitLine("MESSAGE_RECEIVED", Duration.ofMillis(500), false)) {
					return;
				}
			}
			throw new AssertionError("No cluster message received by node " + port + ": " + output);
		}

		private void status() throws IOException {
			send("STATUS");
		}

		private synchronized void send(String command) throws IOException {
			input.write(command);
			input.newLine();
			input.flush();
		}

		private void awaitLine(String expected, Duration timeout) throws InterruptedException {
			assertTrue(awaitLine(expected, timeout, true),
				() -> "Missing '" + expected + "' from node " + port + ": " + output);
		}

		private boolean awaitLine(String expected, Duration timeout, boolean consume) throws InterruptedException {
			long deadline = System.nanoTime() + timeout.toNanos();
			while (System.nanoTime() < deadline) {
				int index = output.indexOf(expected);
				if (index >= 0) {
					if (consume) {
						output.remove(index);
					} else {
						output.remove(index);
					}
					return true;
				}
				if (!process.isAlive()) {
					return false;
				}
				Thread.sleep(25L);
			}
			return false;
		}

		private void kill() throws InterruptedException {
			process.destroyForcibly();
			process.waitFor();
		}

		@Override
		public void close() {
			if (!process.isAlive()) {
				return;
			}
			try {
				send("STOP");
				if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
					process.destroyForcibly();
				}
			} catch (Exception e) {
				process.destroyForcibly();
			}
		}

		@Override
		public String toString() {
			return "NodeProcess{port=" + port + ", alive=" + process.isAlive()
				+ ", output=" + output + '}';
		}

		private static boolean isWindows() {
			return System.getProperty("os.name", "").toLowerCase().contains("win");
		}
	}
}
