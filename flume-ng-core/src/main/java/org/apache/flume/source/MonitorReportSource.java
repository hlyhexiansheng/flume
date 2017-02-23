package org.apache.flume.source;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.channel.ChannelProcessor;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.instrumentation.util.JMXPollUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by noodles on 16/12/3 上午9:55.
 */
public class MonitorReportSource extends AbstractSource implements EventDrivenSource, Configurable {

    public static final int DEFAULT_INTERVAL = 120; //默认120秒上报一次

    private int reportInterval;

    private ScheduledExecutorService scheduledExecutorService;

    @Override
    public synchronized void start() {


        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        this.scheduledExecutorService.scheduleAtFixedRate(new PollJMXInfoCommand(getChannelProcessor()),reportInterval,reportInterval, TimeUnit.SECONDS);

        super.start();

    }

    @Override
    public void configure(Context context) {
        reportInterval = context.getInteger("reportInterval",DEFAULT_INTERVAL);
        final ImmutableMap<String, String> parameters = context.getParameters();
        final ImmutableSet<String> keyAtt = parameters.keySet();
        for(String key : keyAtt){
            System.out.println(key + " " + parameters.get(key));
        }
    }

    private static class PollJMXInfoCommand implements Runnable{

        private ChannelProcessor channelProcessor;

        public PollJMXInfoCommand(ChannelProcessor channelProcessor){
            this.channelProcessor = channelProcessor;
        }
        @Override
        public void run() {
            try {

                Map map = new HashMap();


                final Map<String, Map<String, String>> allMBeans = JMXPollUtil.getAllMBeans();

                map.put("flume",allMBeans);
                System.out.println(new Gson().toJson(map));

                final Event event = EventBuilder.withBody(new Gson().toJson(allMBeans).getBytes());
                this.channelProcessor.processEvent(event);
            }catch (Throwable e){

            }
        }
    }

}

