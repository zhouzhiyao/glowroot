/*
 * Copyright 2013-2014 the original author or authors.
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

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.glowroot.collector.AggregateBuilder;
import org.glowroot.local.store.AggregateDao.SortDirection;
import org.glowroot.local.store.AggregateDao.TransactionAggregateSortColumn;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public class AggregateDaoTest {

    private DataSource dataSource;
    private AggregateDao aggregateDao;

    @Before
    public void beforeEachTest() throws Exception {
        dataSource = new DataSource();
        if (dataSource.tableExists("snapshot")) {
            dataSource.execute("drop table snapshot");
        }
        aggregateDao = new AggregateDao(dataSource);
    }

    @After
    public void afterEachTest() throws Exception {
        dataSource.close();
    }

    @Test
    public void shouldReadAggregates() {
        // given
        AggregateBuilder aggregate = new AggregateBuilder(1000000, 10);
        AggregateBuilder bgAggregate = new AggregateBuilder(0, 0);
        Map<String, AggregateBuilder> transactionAggregates = Maps.newHashMap();
        transactionAggregates.put("one", new AggregateBuilder(100000, 1));
        transactionAggregates.put("two", new AggregateBuilder(300000, 2));
        transactionAggregates.put("seven", new AggregateBuilder(1400000, 7));
        Map<String, AggregateBuilder> bgTransactionAggregates = ImmutableMap.of();
        aggregateDao.store(10000, aggregate, transactionAggregates, bgAggregate,
                bgTransactionAggregates);
        aggregateDao.store(20000, aggregate, transactionAggregates, bgAggregate,
                bgTransactionAggregates);
        // when
        List<Aggregate> aggregatePoints = aggregateDao.readAggregates(0, 100000);
        List<TransactionAggregate> storedTransactionAggregates =
                aggregateDao.readTransactionAggregates(0, 100000,
                        TransactionAggregateSortColumn.AVERAGE, SortDirection.DESC, 10);
        // then
        assertThat(aggregatePoints).hasSize(2);
        assertThat(storedTransactionAggregates).hasSize(3);
        assertThat(storedTransactionAggregates.get(0).getTransactionName()).isEqualTo("seven");
        assertThat(storedTransactionAggregates.get(0).getTotalMicros()).isEqualTo(2800000);
        assertThat(storedTransactionAggregates.get(0).getCount()).isEqualTo(14);
        assertThat(storedTransactionAggregates.get(1).getTransactionName()).isEqualTo("two");
        assertThat(storedTransactionAggregates.get(1).getTotalMicros()).isEqualTo(600000);
        assertThat(storedTransactionAggregates.get(1).getCount()).isEqualTo(4);
        assertThat(storedTransactionAggregates.get(2).getTransactionName()).isEqualTo("one");
        assertThat(storedTransactionAggregates.get(2).getTotalMicros()).isEqualTo(200000);
        assertThat(storedTransactionAggregates.get(2).getCount()).isEqualTo(2);
    }

    @Test
    public void shouldReadBgAggregates() {
        // given
        AggregateBuilder aggregate = new AggregateBuilder(0, 0);
        AggregateBuilder bgAggregate = new AggregateBuilder(1000000, 10);
        Map<String, AggregateBuilder> transactionAggregates = ImmutableMap.of();
        Map<String, AggregateBuilder> bgTransactionAggregates = Maps.newHashMap();
        bgTransactionAggregates.put("one", new AggregateBuilder(100000, 1));
        bgTransactionAggregates.put("two", new AggregateBuilder(300000, 2));
        bgTransactionAggregates.put("seven", new AggregateBuilder(1400000, 7));
        aggregateDao.store(10000, aggregate, transactionAggregates, bgAggregate,
                bgTransactionAggregates);
        aggregateDao.store(20000, aggregate, transactionAggregates, bgAggregate,
                bgTransactionAggregates);
        // when
        List<Aggregate> aggregatePoints = aggregateDao.readBgAggregates(0, 100000);
        List<TransactionAggregate> storedBgTransactionAggregates =
                aggregateDao.readBgTransactionAggregate(0, 100000,
                        TransactionAggregateSortColumn.AVERAGE, SortDirection.DESC, 10);
        // then
        assertThat(aggregatePoints).hasSize(2);
        assertThat(storedBgTransactionAggregates).hasSize(3);
        assertThat(storedBgTransactionAggregates.get(0).getTransactionName()).isEqualTo("seven");
        assertThat(storedBgTransactionAggregates.get(0).getTotalMicros()).isEqualTo(2800000);
        assertThat(storedBgTransactionAggregates.get(0).getCount()).isEqualTo(14);
        assertThat(storedBgTransactionAggregates.get(1).getTransactionName()).isEqualTo("two");
        assertThat(storedBgTransactionAggregates.get(1).getTotalMicros()).isEqualTo(600000);
        assertThat(storedBgTransactionAggregates.get(1).getCount()).isEqualTo(4);
        assertThat(storedBgTransactionAggregates.get(2).getTransactionName()).isEqualTo("one");
        assertThat(storedBgTransactionAggregates.get(2).getTotalMicros()).isEqualTo(200000);
        assertThat(storedBgTransactionAggregates.get(2).getCount()).isEqualTo(2);
    }
}
