package io.github.rroyoo.mockjdbc.users.testsupport.tcp;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

final class TcpServiceProbe {

    private TcpServiceProbe() {
    }

    static boolean isReachable(String host, int port, int timeoutMs) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
