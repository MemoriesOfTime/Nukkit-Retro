package cn.nukkit.network;

import cn.nukkit.Server;

import java.util.Locale;

public final class NetworkInterfaceFactory {

    private static final String LEGACY = "legacy";
    private static final String TRANSPORT_RAKNET = "transport-raknet";

    private NetworkInterfaceFactory() {
    }

    public static SourceInterface create(Server server) {
        String transport = normalizeTransportName(String.valueOf(server.getConfig("network.transport", LEGACY)));
        if (TRANSPORT_RAKNET.equals(transport)) {
            try {
                server.getLogger().notice("Using transport-raknet network interface");
                return new TransportRakNetInterface(server);
            } catch (Throwable t) {
                server.getLogger().warning("Failed to initialize transport-raknet network interface, falling back to legacy RakNet", t);
            }
        }

        return new RakNetInterface(server);
    }

    static String normalizeTransportName(String transport) {
        if (transport == null) {
            return LEGACY;
        }

        String normalized = transport.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "transport-raknet":
            case "transport_raknet":
            case "transport":
            case "cloudburst":
            case "modern":
                return TRANSPORT_RAKNET;
            case "legacy":
            case "raknet":
            default:
                return LEGACY;
        }
    }
}
