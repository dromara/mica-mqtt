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

package org.dromara.mica.mqtt.broker.cluster.store;

import java.util.List;

/**
 * Persistent store for shared-subscription group membership (P2.2).
 * <p>
 * The shared-subscription dispatcher (P1.1) keeps the full candidate list
 * for each {@code $share/<group>/<topic>} group in memory on every node
 * (V1 full-replica strategy).  When the owner node of a group leaves the
 * cluster, the backup node needs to promote itself without losing track of
 * which clients belong to the group.
 * </p>
 * <p>
 * This store provides the durable source of truth: every membership change
 * is written here <em>before</em> being broadcast, so a backup node can
 * recover the latest snapshot after restart or failover.  The in-memory
 * replica on each node is rebuilt from this store on startup.
 * </p>
 * <p>
 * <strong>Versioning</strong>: each {@link SharedSubGroup} carries a
 * monotonically increasing {@code version} field that is incremented on
 * every update.  Cluster updates carry the pre-update version so that
 * out-of-order writes can be detected and dropped (optimistic locking,
 * see {@link #updateIfVersion(SharedSubGroup, long)}).
 * </p>
 *
 * @author L.cm
 * @since 2.6.0
 */
public interface SharedSubStore {

	/**
	 * Persists a new shared-subscription group or replaces an existing one.
	 *
	 * @param group the group state to write; never {@code null}
	 */
	void save(SharedSubGroup group);

	/**
	 * Persists a group only when its in-memory version matches {@code expectedVersion}.
	 * <p>
	 * Returns {@code true} if the write succeeded (no concurrent writer beat us to
	 * it) and {@code false} if the persisted version was different.  Callers
	 * should re-read and retry on {@code false}.
	 * </p>
	 *
	 * @param group           the new state
	 * @param expectedVersion the version we observed before computing {@code group}
	 * @return {@code true} if the update was applied; {@code false} on conflict
	 */
	boolean updateIfVersion(SharedSubGroup group, long expectedVersion);

	/**
	 * Removes every topic filter under a logical shared-subscription group.
	 *
	 * @param groupName the name extracted from {@code $share/<group>/<topic>}
	 */
	void delete(String groupName);

	/**
	 * Removes one topic filter from a logical shared-subscription group.
	 */
	void delete(String groupName, String topicFilter);

	/**
	 * Removes one topic filter only when its version still matches the caller's
	 * snapshot. Implementations should make the compare-and-delete atomic.
	 */
	default boolean deleteIfVersion(String groupName, String topicFilter, long expectedVersion) {
		SharedSubGroup current = get(groupName, topicFilter);
		if (current == null || current.getVersion() != expectedVersion) {
			return false;
		}
		delete(groupName, topicFilter);
		return true;
	}

	/**
	 * Returns an arbitrary topic filter under the given logical group name, or
	 * {@code null} if absent. New routing code should use
	 * {@link #get(String, String)} because a logical group can contain multiple
	 * independent topic filters.
	 *
	 * @param groupName the group name
	 * @return the group, or {@code null}
	 */
	SharedSubGroup get(String groupName);

	/**
	 * Returns one shared-subscription group identified by logical group and
	 * underlying topic filter.
	 */
	SharedSubGroup get(String groupName, String topicFilter);

	/**
	 * Returns all groups persisted in the store.  Used at startup to rebuild
	 * the in-memory replica.
	 *
	 * @return all groups; never {@code null}, may be empty
	 */
	List<SharedSubGroup> listAll();

	// ---- Nested value type -------------------------------------------------------

	/**
	 * Snapshot of a single shared-subscription group.
	 * <p>
	 * Members are referenced by {@code clientId} only; the broker resolves the
	 * {@code clientId} → owning node mapping at lookup time via
	 * {@link org.dromara.mica.mqtt.broker.cluster.core.ClusterMqttSessionManager}.
	 * </p>
	 */
	final class SharedSubGroup {
		/** Logical group name (the {@code <group>} in {@code $share/<group>/<topic>}). */
		private String groupName;
		/** Underlying topic filter (the {@code <topic>} in {@code $share/<group>/<topic>}). */
		private String topicFilter;
		/** {@code clientId}s that have subscribed to the group. */
		private List<String> members;
		/** Owner node id (the node that owns the group's state and routes messages first). */
		private String ownerNodeId;
		/** Backup node id (takes over if owner leaves the cluster). */
		private String backupNodeId;
		/** Monotonically increasing version for optimistic locking. */
		private long version;
		/** Last update time, milliseconds since epoch. */
		private long updatedAt;

		public SharedSubGroup() {
		}

		public SharedSubGroup(String groupName, String topicFilter,
							  List<String> members, String ownerNodeId,
							  String backupNodeId, long version, long updatedAt) {
			this.groupName = groupName;
			this.topicFilter = topicFilter;
			this.members = members;
			this.ownerNodeId = ownerNodeId;
			this.backupNodeId = backupNodeId;
			this.version = version;
			this.updatedAt = updatedAt;
		}

		public String getGroupName() {
			return groupName;
		}

		public void setGroupName(String groupName) {
			this.groupName = groupName;
		}

		public String getTopicFilter() {
			return topicFilter;
		}

		public void setTopicFilter(String topicFilter) {
			this.topicFilter = topicFilter;
		}

		public List<String> getMembers() {
			return members;
		}

		public void setMembers(List<String> members) {
			this.members = members;
		}

		public String getOwnerNodeId() {
			return ownerNodeId;
		}

		public void setOwnerNodeId(String ownerNodeId) {
			this.ownerNodeId = ownerNodeId;
		}

		public String getBackupNodeId() {
			return backupNodeId;
		}

		public void setBackupNodeId(String backupNodeId) {
			this.backupNodeId = backupNodeId;
		}

		public long getVersion() {
			return version;
		}

		public void setVersion(long version) {
			this.version = version;
		}

		public long getUpdatedAt() {
			return updatedAt;
		}

		public void setUpdatedAt(long updatedAt) {
			this.updatedAt = updatedAt;
		}
	}
}
