/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.local.store;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.io.CharSource;
import org.immutables.value.Value;

import org.glowroot.collector.Aggregate;
import org.glowroot.collector.LazyHistogram;
import org.glowroot.common.ObjectMappers;

public class AggregateMerging {

    private static final ObjectMapper mapper = ObjectMappers.create();

    private AggregateMerging() {}

    public static TimerMergedAggregate getTimerMergedAggregate(List<Aggregate> aggregates)
            throws Exception {
        long transactionCount = 0;
        AggregateTimer syntheticRootTimer = AggregateTimer.createSyntheticRootTimer();
        for (Aggregate aggregate : aggregates) {
            transactionCount += aggregate.transactionCount();
            AggregateTimer toBeMergedSyntheticRootTimer =
                    mapper.readValue(aggregate.timers(), AggregateTimer.class);
            syntheticRootTimer.mergeMatchedTimer(toBeMergedSyntheticRootTimer);
        }
        if (syntheticRootTimer.getNestedTimers().size() == 1) {
            // strip off synthetic root node
            return new TimerMergedAggregate(syntheticRootTimer.getNestedTimers().get(0),
                    transactionCount);
        } else {
            return new TimerMergedAggregate(syntheticRootTimer, transactionCount);
        }
    }

    public static HistogramMergedAggregate getHistogramMergedAggregate(List<Aggregate> aggregates)
            throws Exception {
        long transactionCount = 0;
        long totalMicros = 0;
        LazyHistogram histogram = new LazyHistogram();
        for (Aggregate aggregate : aggregates) {
            transactionCount += aggregate.transactionCount();
            totalMicros += aggregate.totalMicros();
            histogram.decodeFromByteBuffer(ByteBuffer.wrap(aggregate.histogram()));
        }
        return new HistogramMergedAggregate(histogram, transactionCount, totalMicros);
    }

    public static ThreadInfoAggregate getThreadInfoAggregate(List<Aggregate> aggregates) {
        Long totalCpuMicros = null;
        Long totalBlockedMicros = null;
        Long totalWaitedMicros = null;
        Long totalAllocatedBytes = null;
        for (Aggregate aggregate : aggregates) {
            totalCpuMicros = nullAwareAdd(totalCpuMicros, aggregate.totalCpuMicros());
            totalBlockedMicros = nullAwareAdd(totalBlockedMicros, aggregate.totalBlockedMicros());
            totalWaitedMicros = nullAwareAdd(totalWaitedMicros, aggregate.totalWaitedMicros());
            totalAllocatedBytes = nullAwareAdd(totalAllocatedBytes,
                    aggregate.totalAllocatedBytes());
        }
        return ImmutableThreadInfoAggregate.builder()
                .totalCpuMicros(totalCpuMicros)
                .totalBlockedMicros(totalBlockedMicros)
                .totalWaitedMicros(totalWaitedMicros)
                .totalAllocatedBytes(totalAllocatedBytes)
                .build();
    }

    public static AggregateProfileNode getProfile(List<CharSource> profiles,
            double truncateLeafPercentage) throws IOException {
        AggregateProfileNode syntheticRootNode = AggregateProfileNode.createSyntheticRootNode();
        for (CharSource profile : profiles) {
            String profileContent = profile.read();
            if (profileContent.equals(AggregateDao.OVERWRITTEN)) {
                continue;
            }
            AggregateProfileNode toBeMergedRootNode = ObjectMappers.readRequiredValue(mapper,
                    profileContent, AggregateProfileNode.class);
            syntheticRootNode.mergeMatchedNode(toBeMergedRootNode);
        }
        if (truncateLeafPercentage != 0) {
            int minSamples = (int) (syntheticRootNode.getSampleCount() * truncateLeafPercentage);
            truncateLeafs(syntheticRootNode, minSamples);
        }
        if (syntheticRootNode.getChildNodes().size() == 1) {
            // strip off synthetic root node
            return syntheticRootNode.getChildNodes().get(0);
        } else {
            return syntheticRootNode;
        }
    }

    private static @Nullable Long nullAwareAdd(@Nullable Long x, @Nullable Long y) {
        if (x == null) {
            return y;
        }
        if (y == null) {
            return x;
        }
        return x + y;
    }

    private static void truncateLeafs(AggregateProfileNode node, int minSamples) {
        for (Iterator<AggregateProfileNode> i = node.getChildNodes().iterator(); i.hasNext();) {
            AggregateProfileNode childNode = i.next();
            if (childNode.getSampleCount() < minSamples) {
                i.remove();
                node.setEllipsed();
            } else {
                truncateLeafs(childNode, minSamples);
            }
        }
    }

    // could use @Value.Immutable, but it's not technically immutable since it contains
    // non-immutable state (AggregateTimer)
    public static class TimerMergedAggregate {

        private final AggregateTimer rootTimer;
        private final long transactionCount;

        private TimerMergedAggregate(AggregateTimer rootTimer, long transactionCount) {
            this.rootTimer = rootTimer;
            this.transactionCount = transactionCount;
        }

        public AggregateTimer getTimers() {
            return rootTimer;
        }

        public long getTransactionCount() {
            return transactionCount;
        }
    }

    // could use @Value.Immutable, but it's not technically immutable since it contains
    // non-immutable state (LazyHistogram)
    public static class HistogramMergedAggregate {

        private final long totalMicros;
        private final long transactionCount;
        private final LazyHistogram histogram;

        private HistogramMergedAggregate(LazyHistogram histogram, long transactionCount,
                long totalMicros) {
            this.histogram = histogram;
            this.transactionCount = transactionCount;
            this.totalMicros = totalMicros;
        }

        public long getTransactionCount() {
            return transactionCount;
        }

        public long getTotalMicros() {
            return totalMicros;
        }

        public long getPercentile1() {
            return histogram.getValueAtPercentile(50);
        }

        public long getPercentile2() {
            return histogram.getValueAtPercentile(95);
        }

        public long getPercentile3() {
            return histogram.getValueAtPercentile(99);
        }
    }

    @Value.Immutable
    @JsonSerialize(as = ImmutableThreadInfoAggregate.class)
    @JsonDeserialize(as = ImmutableThreadInfoAggregate.class)
    public abstract static class ThreadInfoAggregate {
        abstract @Nullable Long totalCpuMicros();
        abstract @Nullable Long totalBlockedMicros();
        abstract @Nullable Long totalWaitedMicros();
        abstract @Nullable Long totalAllocatedBytes();

        public boolean isEmpty() {
            return totalCpuMicros() == null && totalBlockedMicros() == null
                    && totalWaitedMicros() == null && totalAllocatedBytes() == null;
        }
    }
}