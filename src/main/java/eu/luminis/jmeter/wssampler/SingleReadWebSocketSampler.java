/*
 * Copyright © 2016, 2017, 2018 Peter Doornbosch
 *
 * This file is part of JMeter-WebSocket-Samplers, a JMeter add-on for load-testing WebSocket applications.
 *
 * JMeter-WebSocket-Samplers is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * JMeter-WebSocket-Samplers is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package eu.luminis.jmeter.wssampler;

import eu.luminis.websocket.*;
import org.apache.jmeter.samplers.SampleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;


public class SingleReadWebSocketSampler extends WebsocketSampler {

    private static final Logger log = LoggerFactory.getLogger(SingleReadWebSocketSampler.class);

    public SingleReadWebSocketSampler() {
        super.setName("Read WebSocket Sampler");
    }

    @Override
    protected WebSocketClient prepareWebSocketClient(SampleResult result) {
        String connectionId = WebsocketSampler.multipleConnectionsEnabled? getConnectionId().trim(): "";
        if (getCreateNewConnection()) {
            dispose(threadLocalCachedConnection.get().get(connectionId));
            try {
                URL url = new URL(getTLS()? "https": "http", getServer(), Integer.parseInt(getPort()), getPath());   // java.net.URL does not support "ws" protocol....
                return new WebSocketClient(url);
            } catch (MalformedURLException e) {
                // Impossible
                throw new RuntimeException();
            }
        }
        else {
            WebSocketClient wsClient = threadLocalCachedConnection.get().get(connectionId);
            if (wsClient != null) {
                return wsClient;
            }
            else {
                log.error("Sampler '"+ getName() + "': there is no connection to re-use");
                result.setResponseCode("Sampler error");
                result.setResponseMessage("Sampler configured for using existing connection, but there is no connection");
                return null;
            }
        }
    }

    @Override
    protected Frame doSample(WebSocketClient wsClient, SampleResult result) throws IOException, UnexpectedFrameException, SamplingAbortedException {
        try {
            Frame receivedFrame;
            if (! frameFilters.isEmpty()) {
                receivedFrame = frameFilters.get(0).receiveFrame(frameFilters.subList(1, frameFilters.size()), wsClient, readTimeout, result);
                if ((getBinary() && receivedFrame.isBinary()) || (!getBinary() && receivedFrame.isText()))
                    return receivedFrame;
                else
                    throw new UnexpectedFrameException(receivedFrame);
            } else
                return getBinary() ? wsClient.receiveBinaryData(readTimeout) : wsClient.receiveText(readTimeout);
        }
        catch (SocketTimeoutException readTimeout) {
            if (getOptional())
                return null;
            else
                throw readTimeout;
        }
    }

    @Override
    protected void postProcessResponse(Frame response, SampleResult result) {
        if (response == null && getOptional()) {
            log.debug("Sampler '" + getName() + "' received no response (read timeout).");
            result.setSuccessful(true);
            result.setResponseCode("No response");
            result.setResponseMessage("Read timeout, no response received.");
        }
        else
            processDefaultReadResponse((DataFrame) response, getBinary(), result);
    }


    @Override
    protected Logger getLogger() {
        return log;
    }

    @Override
    protected String validateArguments() {
        String errorMsg = null;
        if (getCreateNewConnection()) {
            errorMsg = validatePortNumber(getPort());
            if (errorMsg == null)
                errorMsg = validateConnectionTimeout(getConnectTimeout());
        }
        if (errorMsg == null)
            errorMsg = validateReadTimeout(getReadTimeout());

        return errorMsg;
    }

    public String getServer() {
        return getPropertyAsString("server");
    }

    public void setServer(String server) {
        setProperty("server", server);
    }

    public String getPort() {
        return getPropertyAsString("port", "" + DEFAULT_WS_PORT).trim();
    }

    public void setPort(String port) {
        setProperty("port", port);
    }

    public String getPath() {
        return getPropertyAsString("path");
    }

    public void setPath(String path) {
        setProperty("path", path);
    }

    public boolean getBinary() {
        return getPropertyAsBoolean("binaryPayload");
    }

    public void setBinary(boolean binary) {
        setProperty("binaryPayload", binary);
    }

    public String toString() {
        return "WS Read sampler '" + getName() + "'";
    }

    public boolean getCreateNewConnection() {
        return getPropertyAsBoolean("createNewConnection");
    }

    public void setCreateNewConnection(boolean value) {
        setProperty("createNewConnection", value);
    }

    public String getConnectTimeout() {
        return getPropertyAsString("connectTimeout", "" + WebSocketClient.DEFAULT_CONNECT_TIMEOUT).trim();
    }

    public void setConnectTimeout(String connectTimeout) {
        setProperty("connectTimeout", connectTimeout, "" + WebSocketClient.DEFAULT_CONNECT_TIMEOUT);
    }

    public String getReadTimeout() {
        return getPropertyAsString("readTimeout", "" +WebSocketClient.DEFAULT_READ_TIMEOUT).trim();
    }

    public void setReadTimeout(String readTimeout) {
        setProperty("readTimeout", readTimeout, "" + WebSocketClient.DEFAULT_READ_TIMEOUT);
    }

    public boolean getOptional() {
        return getPropertyAsBoolean("optional");
    }

    public void setOptional(boolean optional) {
        setProperty("optional", optional);
    }
}
