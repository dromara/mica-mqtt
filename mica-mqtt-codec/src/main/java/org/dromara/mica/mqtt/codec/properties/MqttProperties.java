/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.dromara.mica.mqtt.codec.properties;

import java.util.*;

/**
 * MQTT Properties container
 *
 * @author netty
 */
public final class MqttProperties {
	public static final MqttProperties NO_PROPERTIES = new MqttProperties(false);
	private final boolean canModify;
	private Map<Integer, MqttProperty> props;
	private List<UserProperty> userProperties;
	private List<IntegerProperty> subscriptionIds;
	public MqttProperties() {
		this(true);
	}
	private MqttProperties(boolean canModify) {
		this.canModify = canModify;
	}

	public static MqttProperties withEmptyDefaults(MqttProperties properties) {
		if (properties == null) {
			return MqttProperties.NO_PROPERTIES;
		}
		return properties;
	}

	public void add(MqttProperty property) {
		if (!canModify) {
			throw new UnsupportedOperationException("adding property isn't allowed");
		}
		Map<Integer, MqttProperty> props = this.props;
		int propertyId = property.propertyId();
		if (propertyId == MqttPropertyType.USER_PROPERTY.value()) {
			List<UserProperty> userProperties = this.userProperties;
			if (userProperties == null) {
				userProperties = new ArrayList<>(1);
				this.userProperties = userProperties;
			}
			if (property instanceof UserProperty) {
				userProperties.add((UserProperty) property);
			} else if (property instanceof UserProperties) {
				for (StringPair pair : ((UserProperties) property).value()) {
					userProperties.add(new UserProperty(pair.key, pair.value));
				}
			} else {
				throw new IllegalArgumentException("User property must be of UserProperty or UserProperties type");
			}
		} else if (propertyId == MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value()) {
			List<IntegerProperty> subscriptionIds = this.subscriptionIds;
			if (subscriptionIds == null) {
				subscriptionIds = new ArrayList<>(1);
				this.subscriptionIds = subscriptionIds;
			}
			if (property instanceof IntegerProperty) {
				subscriptionIds.add((IntegerProperty) property);
			} else {
				throw new IllegalArgumentException("Subscription ID must be an integer property");
			}
		} else {
			if (props == null) {
				props = new HashMap<>();
				this.props = props;
			}
			props.put(propertyId, property);
		}
	}

	public Collection<? extends MqttProperty> listAll() {
		Map<Integer, MqttProperty> props = this.props;
		if (props == null && subscriptionIds == null && userProperties == null) {
			return Collections.emptyList();
		}
		if (props == null && userProperties == null) {
			return subscriptionIds;
		}
		if (props == null && subscriptionIds == null) {
			return userProperties;
		}
		if (subscriptionIds == null && userProperties == null) {
			return props.values();
		}
		List<MqttProperty> propValues = new ArrayList<>(props != null ? props.size() : 1);
		if (props != null) {
			propValues.addAll(props.values());
		}
		if (subscriptionIds != null) {
			propValues.addAll(subscriptionIds);
		}
		if (userProperties != null) {
			propValues.add(UserProperties.fromUserPropertyCollection(userProperties));
		}
		return propValues;
	}

	public boolean isEmpty() {
		Map<Integer, MqttProperty> props = this.props;
		return props == null || props.isEmpty();
	}

	/**
	 * Get property by ID. If there are multiple properties of this type (can be with Subscription ID)
	 * then return the first one.
	 *
	 * @param propertyId ID of the property
	 * @return a property if it is set, null otherwise
	 */
	public MqttProperty getProperty(int propertyId) {
		if (MqttPropertyType.USER_PROPERTY.value() == propertyId) {
			//special handling to keep compatibility with earlier versions
			List<UserProperty> userProperties = this.userProperties;
			if (userProperties == null) {
				return null;
			}
			return UserProperties.fromUserPropertyCollection(userProperties);
		}
		if (MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value() == propertyId) {
			List<IntegerProperty> subscriptionIds = this.subscriptionIds;
			if (subscriptionIds == null || subscriptionIds.isEmpty()) {
				return null;
			}
			return subscriptionIds.get(0);
		}
		Map<Integer, MqttProperty> props = this.props;
		return props == null ? null : props.get(propertyId);
	}

	/**
	 * Get property by ID. If there are multiple properties of this type (can be with Subscription ID)
	 * then return the first one.
	 *
	 * @param mqttPropertyType Type of the property
	 * @return a property if it is set, null otherwise
	 */
	public MqttProperty getProperty(MqttPropertyType mqttPropertyType) {
		return getProperty(mqttPropertyType.value());
	}

	/**
	 * Get property by ID. If there are multiple properties of this type (can be with Subscription ID)
	 * then return the first one.
	 *
	 * @param mqttPropertyType Type of the property
	 * @param <T>              泛型标记
	 * @return a property value if it is set, null otherwise
	 */
	public <T> T getPropertyValue(MqttPropertyType mqttPropertyType) {
		MqttProperty property = getProperty(mqttPropertyType.value());
		if (property == null) {
			return null;
		}
		return (T) property.value();
	}

	/**
	 * Get properties by ID.
	 * Some properties (Subscription ID and User Properties) may occur multiple times,
	 * this method returns all their values in order.
	 *
	 * @param propertyId ID of the property
	 * @return all properties having specified ID
	 */
	public List<? extends MqttProperty> getProperties(int propertyId) {
		if (propertyId == MqttPropertyType.USER_PROPERTY.value()) {
			return userProperties == null ? Collections.emptyList() : userProperties;
		}
		if (propertyId == MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value()) {
			return subscriptionIds == null ? Collections.emptyList() : subscriptionIds;
		}
		Map<Integer, MqttProperty> props = this.props;
		return (props == null || !props.containsKey(propertyId)) ?
			Collections.emptyList() :
			Collections.singletonList(props.get(propertyId));
	}
}
