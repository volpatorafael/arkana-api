package com.arkana.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArkanaMetricsService {
    private final MeterRegistry meterRegistry;

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopTimer(Timer.Sample sample, ArkanaMetric metric, String... tags) {
        sample.stop(Timer.builder(metric.metricName())
                .description(metric.getDescription())
                .tags(tags)
                .register(meterRegistry));
    }

    public void incrementCounter(ArkanaMetric metric, String... tags) {
        Counter.builder(metric.metricName())
                .description(metric.getDescription())
                .tags(tags)
                .register(meterRegistry)
                .increment();
    }
}
