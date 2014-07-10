package github.com.azubkov.ptp_proxy.http;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.CompositeChannelBuffer;
import org.jboss.netty.handler.codec.http.DefaultHttpMessage;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class Utils {
    public static final AtomicInteger connectionCounter = new AtomicInteger();

    public static String httpRequestToString(DefaultHttpRequest request) {
        StringBuilder messageBuilder = new StringBuilder();

        messageBuilder.append("method: ").append(request.getMethod());
        messageBuilder.append("\n").append("protocol-version: ").append(request.getProtocolVersion());
        messageBuilder.append("\n").append("uri: ").append(request.getUri());
        for (Map.Entry<String, String> header : request.getHeaders()) {
            messageBuilder.append("\n").append("header: '").append(header.getKey()).append("' -> '").append(header.getValue()).append("'");
        }
        messageBuilder.append("\n").append("content-type: ").append(request.getContent().toString());
        messageBuilder.append("\n").append("content:\n").append(new String(request.getContent().array()));

        String result = messageBuilder.toString();
        result = result.replace(Character.toString(Character.MIN_VALUE), "");
        return result;
    }

    public static String httpResponseToString(DefaultHttpResponse response) {
        StringBuilder messageBuilder = new StringBuilder();

        messageBuilder.append("status: ").append(response.getStatus());
        messageBuilder.append("\n").append("protocol-version: ").append(response.getProtocolVersion());
        for (Map.Entry<String, String> header : response.getHeaders()) {
            messageBuilder.append("\n").append("header: '").append(header.getKey()).append("' -> '").append(header.getValue()).append("'");
        }
        messageBuilder.append("\n").append("content-type: ").append(response.getContent().toString());
        messageBuilder.append("\n").append("content:\n");
        if (response.getContent() instanceof CompositeChannelBuffer) {
            CompositeChannelBuffer ccb = (CompositeChannelBuffer) response.getContent();
            int capacity = ccb.capacity();
            Set<ChannelBuffer> processed = new LinkedHashSet<ChannelBuffer>();
            for (int i = 0; i < capacity; i++) {

                ChannelBuffer cb = ccb.getBuffer(i);
                if (processed.contains(cb)) {
                    continue;
                }
                messageBuilder.append(new String(cb.array()));
                processed.add(cb);
            }
        } else {
            messageBuilder.append(new String(response.getContent().array()));
        }
        String result = messageBuilder.toString();
        result = result.replace(Character.toString(Character.MIN_VALUE), "");
        return result;
    }

    //TODO silly method
    public static boolean isJsonString(byte[] content) {
        return content.length > 2 &&
                content[0] == '{' &&
                content[1] == '"';
    }

    public static final String HTTP_MESSAGE_TYPE = "httpMessageType";
    public static final String HTTP_VERSION      = "httpVersion";
    public static final String HTTP_CONTENT      = "httpContent";
    public static final String HTTP_IS_CHUNKED   = "httpIsChunked";
    public static final String HTTP_HEADERS      = "httpHeaders";
    public static final String HTTP_METHOD       = "httpMethod";
    public static final String HTTP_URI          = "httpUri";
    public static final String HTTP_STATUS       = "httpStatus";

    public static final String HTTP_REQUEST  = "request";
    public static final String HTTP_RESPONSE = "response";

    public static String httpMessageToJson(DefaultHttpMessage message) throws JSONException {
        JSONObject jsonResult = new JSONObject();

        jsonResult.put(HTTP_VERSION, message.getProtocolVersion());
        jsonResult.put(HTTP_CONTENT, new String(message.getContent().array()));
        jsonResult.put(HTTP_IS_CHUNKED, message.isChunked());
        jsonResult.put(HTTP_HEADERS, message.getHeaders());

        if (message instanceof DefaultHttpRequest) {
            jsonResult.put(HTTP_MESSAGE_TYPE, HTTP_REQUEST);
            DefaultHttpRequest request = (DefaultHttpRequest) message;
            jsonResult.put(HTTP_METHOD, request.getMethod());
            jsonResult.put(HTTP_URI, request.getUri());
        } else if (message instanceof DefaultHttpResponse) {
            jsonResult.put(HTTP_MESSAGE_TYPE, HTTP_RESPONSE);
            jsonResult.put(HTTP_STATUS, ((DefaultHttpResponse) message).getStatus().getCode());
        }

        return jsonResult.toString();
    }
}
