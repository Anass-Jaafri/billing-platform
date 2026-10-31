package com.anass.billing.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ProviderSimulator {
    private final WebhookSender sender;

    public ProviderSimulator(WebhookSender sender){
        this.sender = sender;
    }

    public List<WebhookSender.SendResult> run(SimulatorConfig config) throws Exception{
        if(config.delaySeconds()>0){
            // A real provider reports the payment a little after the charge is created
            TimeUnit.SECONDS.sleep(config.delaySeconds());
        }
        int copies = 1 + config.duplicates();
        return config.simultaneous()
                ? sendSimultaneously(config,copies)
                : sendOneByOne(config,copies);
    }

    private List<WebhookSender.SendResult> sendOneByOne(SimulatorConfig config, int copies) throws Exception{
        List<WebhookSender.SendResult> results = new ArrayList<>();
        for (int i = 0; i<copies; i++){
            results.add(sender.send(config.eventId(), config.chargeId(), config.outcome()));
        }
        return results;

    }
    private List<WebhookSender.SendResult> sendSimultaneously(SimulatorConfig config, int copies) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(copies);
        CountDownLatch onTheLine = new CountDownLatch(copies); // runners reporting ready
        CountDownLatch startingGun = new CountDownLatch(1);    // released once, for everyone
        try {
            List<Future<WebhookSender.SendResult>> futures = new ArrayList<>();
            for (int i = 0; i < copies; i++) {
                futures.add(pool.submit(() -> {
                    onTheLine.countDown();   // "I'm on the starting line"
                    startingGun.await();     // wait for the gun
                    return sender.send(config.eventId(), config.chargeId(), config.outcome());
                }));
            }
            onTheLine.await();        // wait until every runner is on the line
            startingGun.countDown();  // fire the gun: all requests leave together

            List<WebhookSender.SendResult> results = new ArrayList<>();
            for (Future<WebhookSender.SendResult> future : futures) {
                results.add(future.get()); // wait for each runner to finish
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }
}
