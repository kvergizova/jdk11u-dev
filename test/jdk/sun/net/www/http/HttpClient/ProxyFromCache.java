/*
 * Copyright (c) 2006, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6498566
 * @summary URL.openConnection(Proxy.NO_PROXY) may connect through a proxy.
 * @library /test/lib
 * @run main/othervm ProxyFromCache
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

import jdk.test.lib.net.HttpHeaderParser;
import jdk.test.lib.net.URIBuilder;

/* Creates a simple proxy and http server that just return 200 OK.
 * Open a URL pointing to the http server and specify that the
 * connection should use the proxy. Now make a second connection
 * to the same URL, specifying that no proxy is to be used.
 * We count the amount of requests being sent to each server. There
 * should be only one request sent to each.
 */

public class ProxyFromCache
{
    public static void main(String[] args) {
        ServerSocket proxySSocket, httpSSocket;
        int proxyPort, httpPort;

        try {
            proxySSocket = new ServerSocket(0);
            proxyPort = proxySSocket.getLocalPort();
            httpSSocket = new ServerSocket(0);
            httpPort = httpSSocket.getLocalPort();
        } catch (Exception e) {
            System.out.println ("Exception: " + e);
            return;
        }

        SimpleServer proxyServer = new SimpleServer(proxySSocket);
        proxyServer.start();
        SimpleServer httpServer = new SimpleServer(httpSSocket);
        httpServer.start();

        InetSocketAddress addr = new InetSocketAddress("localhost", proxyPort);
        Proxy proxy = new Proxy(Proxy.Type.HTTP, addr);

        try {
            String urlStr = "http://localhost:" + httpPort + "/";
            URL url = new URL(urlStr);

            // 1st connection.
            HttpURLConnection uc = (HttpURLConnection) url.openConnection(proxy);
            InputStream is = uc.getInputStream();

            byte[] ba = new byte[1024];
            while(is.read(ba) != -1);
            is.close();

            // 2nd connection.
            uc = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
            is = uc.getInputStream();

            while(is.read(ba) != -1);
            is.close();

            try {
                proxySSocket.close();
                httpSSocket.close();
            } catch (IOException e) {}

            proxyServer.terminate();
            httpServer.terminate();

            int httpCount = httpServer.getConnectionCount();
            int proxyCount = proxyServer.getConnectionCount();

            if (proxyCount != 1 && httpCount != 1) {
                System.out.println("Proxy = " + proxyCount + ", http = " + httpCount);
                throw new RuntimeException("Failed: Proxy being sent " + proxyCount  + " requests");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

class SimpleServer extends Thread
{
    private ServerSocket ss;
    private Socket sock;
    private int connectionCount;

    String replyOK =  "HTTP/1.1 200 OK\r\n" +
                      "Content-Length: 0\r\n\r\n";

    public SimpleServer(ServerSocket ss) {
        this.ss = ss;
    }

    public void run() {
        try {
            sock = ss.accept();
            connectionCount++;
            InputStream is = sock.getInputStream();
            OutputStream os = sock.getOutputStream();
            HttpHeaderParser httpHeaderParser = new HttpHeaderParser(is);
            os.write(replyOK.getBytes("UTF-8"));
            httpHeaderParser = new HttpHeaderParser(is);
            // If we get here then we received a second request.
            connectionCount++;
            os.write(replyOK.getBytes("UTF-8"));
            sock.close();
        } catch (Exception e) {
            //e.printStackTrace();
            if (sock != null && !sock.isClosed()) {
                try { sock.close();
                } catch (IOException ioe) {}
            }
        }
    }

    public int getConnectionCount() {
        return connectionCount;
    }

    public void terminate() {
        if (sock != null && !sock.isClosed()) {
            try { sock.close();
            } catch (IOException ioe) {}
        }
    }
}
