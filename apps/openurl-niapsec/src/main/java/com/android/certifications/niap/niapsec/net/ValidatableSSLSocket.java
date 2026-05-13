/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.certifications.niap.niapsec.net;


import android.util.Log;

import androidx.annotation.NonNull;

import com.android.certifications.niap.niapsec.SecureConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.security.Security;
import java.util.List;

import javax.net.ssl.HandshakeCompletedEvent;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;


/**
 * Custom SSLSocket that automatically handles OCSP validation.
 * <p>
 * Internal Only
 */
class ValidatableSSLSocket extends SSLSocket {

    private static final String TAG = "ValidatableSSLSocket";

    private final SSLSocket sslSocket;
    private final String hostname;
    private final SecureURL secureURL;
    private boolean handshakeStarted = false;
    private final SecureConfig secureConfig;

    private static class CNSAAlgorithmConstraints implements java.security.AlgorithmConstraints {
        @Override
        public boolean permits(java.util.Set<java.security.CryptoPrimitive> primitives, String algorithm, java.security.AlgorithmParameters parameters) {
            return !algorithm.toUpperCase().contains("SHA256") && !algorithm.toUpperCase().contains("SHA-256");
        }
        @Override
        public boolean permits(java.util.Set<java.security.CryptoPrimitive> primitives, java.security.Key key) {
            return true;
        }
        @Override
        public boolean permits(java.util.Set<java.security.CryptoPrimitive> primitives, String algorithm, java.security.Key key, java.security.AlgorithmParameters parameters) {
            return !algorithm.toUpperCase().contains("SHA256") && !algorithm.toUpperCase().contains("SHA-256");
        }
    }

    public ValidatableSSLSocket(SecureURL secureURL,
                                Socket sslSocket,
                                SecureConfig secureConfig,
                                String[] supportedCipherSuites) throws IOException {
        this.secureURL = secureURL;
        this.hostname = secureURL.getHostname();
        this.sslSocket = (SSLSocket) sslSocket;
        this.secureConfig = secureConfig;
        setSecureCiphers(supportedCipherSuites);

        javax.net.ssl.SSLParameters params = this.sslSocket.getSSLParameters();
        params.setAlgorithmConstraints(new CNSAAlgorithmConstraints());
        this.sslSocket.setSSLParameters(params);

        //Check OCSP stapling status from the handshake response.
        this.sslSocket.addHandshakeCompletedListener(new HandshakeCompletedListener() {
            @Override
            public void handshakeCompleted(HandshakeCompletedEvent event) {


                if(!CertificateValidation.enableOCSPStaplingCheck)
                    return;
                SSLSession session = event.getSession();
                try {
                    Method m = session.getClass().getMethod("getStatusResponses");
                    m.setAccessible(true);
                    List<byte[]> resp = (List<byte[]>)m.invoke(session);
                    android.util.Log.d("ValidatableSSLSocket", "NIAPSEC_DEBUG: OCSP Responses size: " + (resp != null ? resp.size() : "null"));
                } catch (NoSuchMethodException | InvocationTargetException |
                         IllegalAccessException e) {
                    throw new RuntimeException("ConscryptSSLSession.getStatusResponses() is not supported.",e);
                }
            }
        });
        java.security.Security.setProperty("ocsp.enable", "true");
    }

    private void setSecureCiphers(String[] supportedCipherSuites) {
        if (secureConfig.isUseStrongSSLCiphersEnabled()) {
            this.sslSocket.setEnabledCipherSuites(supportedCipherSuites);
        }
    }

    

    @Override
    public void startHandshake() throws IOException {
        if (!handshakeStarted) {
            sslSocket.startHandshake();
            handshakeStarted = true;
            
            java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate) sslSocket.getSession().getPeerCertificates()[0];
            

            
            // 1. AKID の存在チェック (OID: 2.5.29.35)
            if (cert.getExtensionValue("2.5.29.35") == null) {
                throw new javax.net.ssl.SSLPeerUnverifiedException("Missing Authority Key Identifier extension");
            }
            
            // 2. Subject Key Identifier の存在チェック (OID: 2.5.29.14)
            if (cert.getExtensionValue("2.5.29.14") == null) {
                throw new javax.net.ssl.SSLPeerUnverifiedException("Missing Subject Key Identifier extension");
            }
            
            // 3. KeyUsage の存在チェック
            if (cert.getKeyUsage() == null) {
                throw new javax.net.ssl.SSLPeerUnverifiedException("Missing KeyUsage extension");
            }
            
            // 4. Subject Alternative Name (SAN) の存在チェック (OID: 2.5.29.17)
            if (cert.getExtensionValue("2.5.29.17") == null) {
                throw new javax.net.ssl.SSLPeerUnverifiedException("Missing Subject Alternative Name extension");
            }
        }
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return sslSocket.getSupportedCipherSuites();
    }

    @Override
    public String[] getEnabledCipherSuites() {
        return sslSocket.getEnabledCipherSuites();
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {
        sslSocket.setEnabledCipherSuites(suites);
    }

    @Override
    public String[] getSupportedProtocols() {
        return sslSocket.getSupportedProtocols();
    }

    @Override
    public String[] getEnabledProtocols() {
        return sslSocket.getEnabledProtocols();
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
        sslSocket.setEnabledProtocols(protocols);
    }

    @Override
    public SSLSession getSession() {
        return sslSocket.getSession();
    }

    @Override
    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {
        sslSocket.addHandshakeCompletedListener(listener);
    }

    @Override
    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {
        sslSocket.removeHandshakeCompletedListener(listener);
    }

    @Override
    public void setUseClientMode(boolean mode) {
        sslSocket.setUseClientMode(mode);
    }

    @Override
    public boolean getUseClientMode() {
        return sslSocket.getUseClientMode();
    }

    @Override
    public void setNeedClientAuth(boolean need) {
        sslSocket.setNeedClientAuth(need);
    }

    @Override
    public boolean getNeedClientAuth() {
        return sslSocket.getNeedClientAuth();
    }

    @Override
    public void setWantClientAuth(boolean want) {
        sslSocket.setWantClientAuth(want);
    }

    @Override
    public boolean getWantClientAuth() {
        return sslSocket.getWantClientAuth();
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {
        sslSocket.setEnableSessionCreation(flag);
    }

    @Override
    public boolean getEnableSessionCreation() {
        return sslSocket.getEnableSessionCreation();
    }

    @Override
    public SSLSession getHandshakeSession() {
        return sslSocket.getHandshakeSession();
    }

    @Override
    public SSLParameters getSSLParameters() {
        return sslSocket.getSSLParameters();
    }

    @Override
    public void setSSLParameters(SSLParameters params) {
        sslSocket.setSSLParameters(params);
    }

    @NonNull
    @Override
    public String toString() {
        return sslSocket.toString();
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        sslSocket.connect(endpoint);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        sslSocket.connect(endpoint, timeout);
    }

    @Override
    public void bind(SocketAddress bindpoint) throws IOException {
        sslSocket.bind(bindpoint);
    }

    @Override
    public InetAddress getInetAddress() {
        return sslSocket.getInetAddress();
    }

    @Override
    public InetAddress getLocalAddress() {
        return sslSocket.getLocalAddress();
    }

    @Override
    public int getPort() {
        return sslSocket.getPort();
    }

    @Override
    public int getLocalPort() {
        return sslSocket.getLocalPort();
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return sslSocket.getRemoteSocketAddress();
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
        return sslSocket.getLocalSocketAddress();
    }

    @Override
    public SocketChannel getChannel() {
        return sslSocket.getChannel();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return sslSocket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return sslSocket.getOutputStream();
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        sslSocket.setTcpNoDelay(on);
    }

    @Override
    public boolean getTcpNoDelay() throws SocketException {
        return sslSocket.getTcpNoDelay();
    }

    @Override
    public void setSoLinger(boolean on, int linger) throws SocketException {
        sslSocket.setSoLinger(on, linger);
    }

    @Override
    public int getSoLinger() throws SocketException {
        return sslSocket.getSoLinger();
    }

    @Override
    public void sendUrgentData(int data) throws IOException {
        sslSocket.sendUrgentData(data);
    }

    @Override
    public void setOOBInline(boolean on) throws SocketException {
        sslSocket.setOOBInline(on);
    }

    @Override
    public boolean getOOBInline() throws SocketException {
        return sslSocket.getOOBInline();
    }

    @Override
    public synchronized void setSoTimeout(int timeout) throws SocketException {
        sslSocket.setSoTimeout(timeout);
    }

    @Override
    public synchronized int getSoTimeout() throws SocketException {
        return sslSocket.getSoTimeout();
    }

    @Override
    public synchronized void setSendBufferSize(int size) throws SocketException {
        sslSocket.setSendBufferSize(size);
    }



    @Override
    public synchronized int getSendBufferSize() throws SocketException {
        return sslSocket.getSendBufferSize();
    }

    @Override
    public synchronized void setReceiveBufferSize(int size) throws SocketException {
        sslSocket.setReceiveBufferSize(size);
    }

    @Override
    public synchronized int getReceiveBufferSize() throws SocketException {
        return sslSocket.getReceiveBufferSize();
    }

    @Override
    public void setKeepAlive(boolean on) throws SocketException {
        sslSocket.setKeepAlive(on);
    }

    @Override
    public boolean getKeepAlive() throws SocketException {
        return sslSocket.getKeepAlive();
    }

    @Override
    public void setTrafficClass(int tc) throws SocketException {
        sslSocket.setTrafficClass(tc);
    }

    @Override
    public int getTrafficClass() throws SocketException {
        return sslSocket.getTrafficClass();
    }

    @Override
    public void setReuseAddress(boolean on) throws SocketException {
        sslSocket.setReuseAddress(on);
    }

    @Override
    public boolean getReuseAddress() throws SocketException {
        return sslSocket.getReuseAddress();
    }

    @Override
    public synchronized void close() throws IOException {
        sslSocket.close();
        Log.i(TAG, "TLS session terminated for " + secureURL.getHostInfo());
    }

    @Override
    public void shutdownInput() throws IOException {
        sslSocket.shutdownInput();
    }

    @Override
    public void shutdownOutput() throws IOException {
        sslSocket.shutdownOutput();
    }

    @Override
    public boolean isConnected() {
        return sslSocket.isConnected();
    }

    @Override
    public boolean isBound() {
        return sslSocket.isBound();
    }

    @Override
    public boolean isClosed() {
        return sslSocket.isClosed();
    }

    @Override
    public boolean isInputShutdown() {
        return sslSocket.isInputShutdown();
    }

    @Override
    public boolean isOutputShutdown() {
        return sslSocket.isOutputShutdown();
    }

    @Override
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
        sslSocket.setPerformancePreferences(connectionTime, latency, bandwidth);
    }
}
