package org.dromara.mica.mqtt.core.util;

public class TestBean {
	private String name;
	private String node;
	private String clientId;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getNode() {
		return node;
	}

	public void setNode(String node) {
		this.node = node;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	@Override
	public String toString() {
		return "TestBean{" +
			"name='" + name + '\'' +
			", node='" + node + '\'' +
			", clientId='" + clientId + '\'' +
			'}';
	}
}
