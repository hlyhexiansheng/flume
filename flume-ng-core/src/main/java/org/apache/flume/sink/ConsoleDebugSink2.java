package org.apache.flume.sink;

import org.apache.flume.*;
import org.apache.flume.conf.Configurable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Created by noodles on 16/12/1 上午9:17.
 */
public class ConsoleDebugSink2 extends AbstractSink implements Configurable {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleDebugSink2.class);

    @Override
    public void configure(Context context) {
        logger.info("init me");
    }

    @Override
    public Status process() throws EventDeliveryException {
        Status result = Status.READY;
        Channel channel = getChannel();
        Transaction transaction = channel.getTransaction();
        Event event = null;

        try {
            transaction.begin();
            event = channel.take();

            if (event != null) {
                if (logger.isInfoEnabled()) {
                    System.out.println("Event: " + toStringValue(event));
                }
            } else {
                result = Status.BACKOFF;
            }
            transaction.commit();
        } catch (Exception ex) {
            transaction.rollback();
            throw new EventDeliveryException("Failed to log event: " + event, ex);
        } finally {
            transaction.close();
        }

        return result;
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
