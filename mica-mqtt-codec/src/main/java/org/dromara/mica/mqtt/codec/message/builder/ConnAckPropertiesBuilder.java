package org.dromara.mica.mqtt.codec.message.builder;

import org.dromara.mica.mqtt.codec.properties.*;

public final class ConnAckPropertiesBuilder {
	private final UserProperties userProperties = new UserProperties();
	private String clientId;
	private Long sessionExpiryInterval;
	private int receiveMaximum;
	private Byte maximumQos;
	private boolean retain;
	private Long maximumPacketSize;
	private int topicAliasMaximum;
	private String reasonString;
	private Boolean wildcardSubscriptionAvailable;
	private Boolean subscriptionIdentifiersAvailable;
	private Boolean sharedSubscriptionAvailable;
	private Integer serverKeepAlive;
	private String responseInformation;
	private String serverReference;
	private String authenticationMethod;
	private byte[] authenticationData;

	public MqttProperties build() {
		final MqttProperties props = new MqttProperties();
		if (clientId != null) {
			props.add(new StringProperty(MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER.value(),
				clientId));
		}
		if (sessionExpiryInterval != null) {
			props.add(new IntegerProperty(
				MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), sessionExpiryInterval.intValue()));
		}
		if (receiveMaximum > 0) {
			props.add(new IntegerProperty(MqttPropertyType.RECEIVE_MAXIMUM.value(), receiveMaximum));
		}
		if (maximumQos != null) {
			props.add(new IntegerProperty(MqttPropertyType.MAXIMUM_QOS.value(), maximumQos.intValue()));
		}
		props.add(new BooleanProperty(MqttPropertyType.RETAIN_AVAILABLE.value(), retain));
		if (maximumPacketSize != null) {
			props.add(new IntegerProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE.value(),
				maximumPacketSize.intValue()));
		}
		props.add(new IntegerProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM.value(),
			topicAliasMaximum));
		if (reasonString != null) {
			props.add(new StringProperty(MqttPropertyType.REASON_STRING.value(), reasonString));
		}
		props.add(userProperties);
		if (wildcardSubscriptionAvailable != null) {
			props.add(new BooleanProperty(MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE.value(),
				wildcardSubscriptionAvailable));
		}
		if (subscriptionIdentifiersAvailable != null) {
			props.add(new BooleanProperty(MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE.value(),
				subscriptionIdentifiersAvailable));
		}
		if (sharedSubscriptionAvailable != null) {
			props.add(new BooleanProperty(MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE.value(),
				sharedSubscriptionAvailable));
		}
		if (serverKeepAlive != null) {
			props.add(new IntegerProperty(MqttPropertyType.SERVER_KEEP_ALIVE.value(),
				serverKeepAlive));
		}
		if (responseInformation != null) {
			props.add(new StringProperty(MqttPropertyType.RESPONSE_INFORMATION.value(),
				responseInformation));
		}
		if (serverReference != null) {
			props.add(new StringProperty(MqttPropertyType.SERVER_REFERENCE.value(),
				serverReference));
		}
		if (authenticationMethod != null) {
			props.add(new StringProperty(MqttPropertyType.AUTHENTICATION_METHOD.value(),
				authenticationMethod));
		}
		if (authenticationData != null) {
			props.add(new BinaryProperty(MqttPropertyType.AUTHENTICATION_DATA.value(),
				authenticationData));
		}
		return props;
	}

	public ConnAckPropertiesBuilder sessionExpiryInterval(long seconds) {
		this.sessionExpiryInterval = seconds;
		return this;
	}

	public ConnAckPropertiesBuilder receiveMaximum(int value) {
		if (value <= 0) {
			throw new IllegalArgumentException("receive maximum property must be > 0");
		}
		this.receiveMaximum = value;
		return this;
	}

	public ConnAckPropertiesBuilder maximumQos(byte value) {
		if (value != 0 && value != 1) {
			throw new IllegalArgumentException("maximum QoS property could be 0 or 1");
		}
		this.maximumQos = value;
		return this;
	}

	public ConnAckPropertiesBuilder retainAvailable(boolean retain) {
		this.retain = retain;
		return this;
	}

	public ConnAckPropertiesBuilder maximumPacketSize(long size) {
		if (size <= 0) {
			throw new IllegalArgumentException("maximum packet size property must be > 0");
		}
		this.maximumPacketSize = size;
		return this;
	}

	public ConnAckPropertiesBuilder assignedClientId(String clientId) {
		this.clientId = clientId;
		return this;
	}

	public ConnAckPropertiesBuilder topicAliasMaximum(int value) {
		this.topicAliasMaximum = value;
		return this;
	}

	public ConnAckPropertiesBuilder reasonString(String reason) {
		this.reasonString = reason;
		return this;
	}

	public ConnAckPropertiesBuilder userProperty(String name, String value) {
		userProperties.add(name, value);
		return this;
	}

	public ConnAckPropertiesBuilder wildcardSubscriptionAvailable(boolean value) {
		this.wildcardSubscriptionAvailable = value;
		return this;
	}

	public ConnAckPropertiesBuilder subscriptionIdentifiersAvailable(boolean value) {
		this.subscriptionIdentifiersAvailable = value;
		return this;
	}

	public ConnAckPropertiesBuilder sharedSubscriptionAvailable(boolean value) {
		this.sharedSubscriptionAvailable = value;
		return this;
	}

	public ConnAckPropertiesBuilder serverKeepAlive(int seconds) {
		this.serverKeepAlive = seconds;
		return this;
	}

	public ConnAckPropertiesBuilder responseInformation(String value) {
		this.responseInformation = value;
		return this;
	}

	public ConnAckPropertiesBuilder serverReference(String host) {
		this.serverReference = host;
		return this;
	}

	public ConnAckPropertiesBuilder authenticationMethod(String methodName) {
		this.authenticationMethod = methodName;
		return this;
	}

	public ConnAckPropertiesBuilder authenticationData(byte[] rawData) {
		this.authenticationData = rawData.clone();
		return this;
	}
}
