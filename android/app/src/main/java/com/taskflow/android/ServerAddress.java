package com.taskflow.android;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class ServerAddress {
    private final URI origin;

    private ServerAddress(URI origin) {
        this.origin = origin;
    }

    static ServerAddress parse(String text) {
        URI address = URI.create(text.trim());
        String scheme = address.getScheme();
        String host = address.getHost();
        String path = address.getRawPath();
        int port = address.getPort();

        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Expected HTTP or HTTPS");
        }
        if (host == null || address.getRawUserInfo() != null
                || address.getRawQuery() != null || address.getRawFragment() != null
                || port == 0 || port > 65535
                || (path != null && !path.isEmpty() && !path.equals("/"))) {
            throw new IllegalArgumentException("Expected a server origin");
        }
        if ("http".equalsIgnoreCase(scheme) && !isLocalHost(host)) {
            throw new IllegalArgumentException("HTTP is available on the local network");
        }

        try {
            return new ServerAddress(new URI(scheme.toLowerCase(Locale.ROOT), null,
                    host.toLowerCase(Locale.ROOT), port, null, null, null));
        } catch (URISyntaxException invalidAddress) {
            throw new IllegalArgumentException(invalidAddress);
        }
    }

    boolean contains(String url) {
        try {
            URI candidate = URI.create(url);
            return origin.getScheme().equalsIgnoreCase(candidate.getScheme())
                    && origin.getHost().equalsIgnoreCase(candidate.getHost())
                    && effectivePort(origin) == effectivePort(candidate)
                    && candidate.getRawUserInfo() == null;
        } catch (IllegalArgumentException invalidAddress) {
            return false;
        }
    }

    private static int effectivePort(URI address) {
        if (address.getPort() != -1) {
            return address.getPort();
        }
        return "https".equalsIgnoreCase(address.getScheme()) ? 443 : 80;
    }

    private static boolean isLocalHost(String host) {
        if ("localhost".equalsIgnoreCase(host) || "[::1]".equals(host)) {
            return true;
        }

        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("0|[1-9][0-9]{0,2}")) {
                return false;
            }
            octets[i] = Integer.parseInt(parts[i]);
            if (octets[i] > 255) {
                return false;
            }
        }
        return octets[0] == 127
                || octets[0] == 10
                || (octets[0] == 192 && octets[1] == 168)
                || (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31);
    }

    @Override
    public String toString() {
        return origin.toASCIIString();
    }
}
