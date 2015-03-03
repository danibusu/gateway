/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 * 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.service.broadcast;

import java.nio.channels.ClosedChannelException;
import java.util.Collection;
import java.util.Collections;

import org.apache.mina.core.filterchain.IoFilter;
import org.apache.mina.core.service.IoHandler;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.util.ConcurrentHashSet;
import org.slf4j.Logger;

import org.kaazing.gateway.transport.io.filter.IoMessageCodecFilter;

class BroadcastServiceHandler extends IoHandlerAdapter {

    private final IoFilter codec;
    private final Collection<IoSession> clients;
    private final IoHandler handler;
    private final Logger logger;
    private IoSession connectSession;

    BroadcastServiceHandler(boolean disconnectClientsOnReconnect, long maximumScheduledWriteBytes, Logger logger)
            throws Exception {
        this.clients = new ConcurrentHashSet<IoSession>();
        this.handler = new BroadcastListenHandler(Collections.unmodifiableCollection(clients),
                disconnectClientsOnReconnect, maximumScheduledWriteBytes, logger);
        this.codec = new IoMessageCodecFilter();
        this.logger = logger;
    }

    IoSession getConnectSession() {
        return connectSession;
    }

    void setConnectSession(IoSession connectSession) {
        this.connectSession = connectSession;
    }

    IoHandler getListenHandler() {
        return handler;
    }

    @Override
    public void exceptionCaught(IoSession session, Throwable cause)
            throws Exception {
        // Do not print endless stack traces if a session is closed (e.g. during gateway destroy) with pending writes
        if (session.isClosing() && cause instanceof ClosedChannelException) {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("BroadcastServiceHandler: caught exception %s, probably because session was closed with pending writes", cause));
            }
        }
        else {
            logger.warn("Unexpected exception in broadcast service handler", cause);
        }
    }


    @Override
    public void sessionOpened(IoSession session) throws Exception {
        session.getFilterChain().addLast("io", codec);
        clients.add(session);
    }

    @Override
    public void sessionClosed(IoSession session) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("BroadcastServiceHandler: session closed on %s", session.toString()));
        }

        clients.remove(session);
    }
}