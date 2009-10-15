/*
 * ################################################################
 *
 * ProActive: The Java(TM) library for Parallel, Distributed,
 *            Concurrent computing with Security and Mobility
 *
 * Copyright (C) 1997-2009 INRIA/University of Nice-Sophia Antipolis
 * Contact: proactive@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version
 * 2 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 *  Initial developer(s):               The ActiveEon Team
 *                        http://www.activeeon.com/
 *  Contributor(s):
 *
 *
 * ################################################################
 * $$ACTIVEEON_INITIAL_DEV$$
 */
package org.ow2.proactive.scheduler.common.util.logforwarder.appenders;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.log4j.net.SocketAppender;
import org.objectweb.proactive.core.ssh.SshConfig;
import org.objectweb.proactive.core.ssh.SshConnection;
import org.objectweb.proactive.core.ssh.SshTunnel;


public class SocketAppenderWithSSHTunneling extends SocketAppender {

    // connection to the log server
    SshConnection sshConnection;
    SshTunnel tunnel;

    /**
     * Create a new SocketAppenderWithSSHTunneling with the current username on remote ssh port 22.
     * @param remoteHost the host running the log server.
     * @param port the remote listening port.
     * @throws IOException
     */
    public SocketAppenderWithSSHTunneling(String remoteHost, int port) throws IOException {
        this(System.getProperty("user.name"), remoteHost, port, 22);
    }

    /**
     * Create a new SocketAppenderWithSSHTunneling with the current username.
     * @param remoteHost the host running the log server.
     * @param port the remote listening port.
     * @param remoteSSHPort the remote ssh port (usually 22).
     * @throws IOException
     */
    public SocketAppenderWithSSHTunneling(String remoteHost, int port, int remoteSSHPort) throws IOException {
        this(System.getProperty("user.name"), remoteHost, port, remoteSSHPort);
    }

    /**
     * Create a new SocketAppenderWithSSHTunneling.
     * @param username username for ssh connection.
     * @param remoteHost the host running the log server.
     * @param port the remote listening port.
     * @param remoteSSHPort the remote ssh port (usually 22).
     * @throws IOException
     */
    public SocketAppenderWithSSHTunneling(String username, String remoteHost, int port, int remoteSSHPort)
            throws IOException {
        super(remoteHost, port);

        InetAddress localhost = InetAddress.getLocalHost();
        SshConfig config = new SshConfig();
        sshConnection = new SshConnection(username, remoteHost, remoteSSHPort, config
                .getPrivateKeys(remoteHost));
        tunnel = sshConnection.getSSHTunnel(remoteHost, remoteSSHPort);
        this.setRemoteHost(localhost.getHostAddress());
        this.setPort(tunnel.getPort());
    }

    public void close() {
        super.close();
        try {
            this.tunnel.close();
            this.sshConnection.close();
        } catch (Exception e) {
            //well, if we are here it's difficult to log the error somewhere ...
            e.printStackTrace();
        }
    }
}