package org.apache.flume.interceptor;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by noodles on 16/12/23 下午3:53.
 */
public class LoggerInterceptor implements Interceptor{

    private static final Logger logger = LoggerFactory.getLogger(LoggerInterceptor.class);

    private boolean available;

    public LoggerInterceptor(boolean available){
        logger.info("LoggerInterceptor init..." + available);
        this.available = available;
    }

    @Override
    public void initialize() {

    }

    @Override
    public Event intercept(Event event) {
        if(available){
            logger.info("Event:" + toStringValue(event));
        }
        return event;
    }

    @Override
    public List<Event> intercept(List<Event> events) {
        for (Event event : events) {
            intercept(event);
        }
        return events;
    }

    @Override
    public void close() {

    }

    public static class Builder implements Interceptor.Builder {

        private boolean available;

        @Override
        public Interceptor build() {
            return new LoggerInterceptor(available);
        }

        @Override
        public void configure(Context context) {
            this.available = context.getBoolean("available", true);
        }

    }

    private String toStringValue(Event event) {
        if (event == null) {
            return "";
        }

        StringBuilder headers = new StringBuilder();
        final Map<String, String> eventHeaders = event.getHeaders();
        if(eventHeaders.size() > 0){
            final Set<String> keySet = eventHeaders.keySet();
            for(String key : keySet){
                headers.append(key).append(":").append(eventHeaders.get(key)).append(",");
            }
            headers.deleteCharAt(headers.length() - 1);
        }

        final byte[] body = event.getBody();

        final String bodyS = new String(body);
        return "headers=["+ headers.toString() + "]" + " body=[" + bodyS.substring(0,bodyS.length() - 1) + "]";
    }
}
