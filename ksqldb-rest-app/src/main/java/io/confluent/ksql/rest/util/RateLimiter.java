/*
 * Copyright 2021 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.rest.util;

import io.confluent.ksql.util.KsqlException;
import java.util.Map;
import org.apache.kafka.common.metrics.Metrics;

@SuppressWarnings("UnstableApiUsage")
public class RateLimiter {

  private final com.google.common.util.concurrent.RateLimiter rateLimiter;

  public RateLimiter(final double permitsPerSecond,
      final Metrics metrics,
      final Map<String, String> metricsTags) {
    this.rateLimiter = com.google.common.util.concurrent.RateLimiter.create(permitsPerSecond);
  }

  public void checkLimit() {
    if (!rateLimiter.tryAcquire()) {
      throw new KsqlException("Host is at rate limit for pull queries. Currently set to "
          + rateLimiter.getRate() + " qps.");
    }
  }
}
