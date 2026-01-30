package dev.hytaleone.query.config;

/**
 * Network participation mode.
 *
 * <ul>
 *   <li>{@link #PUBLISH} - Reports own state (heartbeats, player events) to the network</li>
 *   <li>{@link #SYNC} - Publishes + subscribes to events and maintains local cache</li>
 *   <li>{@link #AGGREGATE} - Syncs + serves aggregated network data in query responses</li>
 * </ul>
 */
public enum NetworkMode {

    /**
     * Publish only - reports own state to the network.
     * Use for game servers that don't need to know about other servers.
     */
    PUBLISH,

    /**
     * Publish + sync - subscribes to network events and maintains local cache.
     * Use for servers that need real-time network state (e.g., for routing).
     */
    SYNC,

    /**
     * Publish + sync + aggregate - serves aggregated data in query responses.
     * Use for hub/lobby servers that respond to external queries.
     */
    AGGREGATE
}
