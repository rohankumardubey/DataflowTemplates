/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.datadog;

import static org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.Preconditions.checkArgument;
import static org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import org.apache.beam.sdk.coders.BigEndianIntegerCoder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.MoreObjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DatadogIO} class provides a {@link PTransform} that allows writing {@link DatadogEvent}
 * messages into a Datadog HTTP Event Collector end point.
 */
public class DatadogIO {

  private static final Logger LOG = LoggerFactory.getLogger(DatadogIO.class);

  private DatadogIO() {}

  public static Write.Builder writeBuilder() {
    return new AutoValue_DatadogIO_Write.Builder();
  }

  /**
   * Class {@link Write} provides a {@link PTransform} that allows writing {@link DatadogEvent}
   * records into a Datadog HTTP Event Collector end-point using HTTP POST requests. In the event of
   * an error, a {@link PCollection} of {@link DatadogWriteError} records are returned for further
   * processing or storing into a deadletter sink.
   */
  @AutoValue
  public abstract static class Write
      extends PTransform<PCollection<DatadogEvent>, PCollection<DatadogWriteError>> {

    @Nullable
    abstract ValueProvider<String> url();

    @Nullable
    abstract ValueProvider<String> token();

    @Nullable
    abstract ValueProvider<Integer> batchCount();

    @Nullable
    abstract ValueProvider<Integer> parallelism();

    @Nullable
    abstract ValueProvider<Boolean> enableBatchLogs();

    @Nullable
    abstract ValueProvider<Boolean> enableGzipHttpCompression();

    @Override
    public PCollection<DatadogWriteError> expand(PCollection<DatadogEvent> input) {

      LOG.info("Configuring DatadogEventWriter.");
      DatadogEventWriter.Builder builder =
          DatadogEventWriter.newBuilder()
              .withUrl(url())
              .withInputBatchCount(batchCount())
              .withToken((token()))
              .withEnableBatchLogs(enableBatchLogs())
              .withEnableGzipHttpCompression(enableGzipHttpCompression());

      DatadogEventWriter writer = builder.build();
      LOG.info("DatadogEventWriter configured");

      // Return a PCollection<DatadogWriteError>
      return input
          .apply("Create KV pairs", CreateKeys.of(parallelism()))
          .apply("Write Datadog events", ParDo.of(writer))
          .setCoder(DatadogWriteErrorCoder.of());
    }

    /** A builder for creating {@link Write} objects. */
    @AutoValue.Builder
    public abstract static class Builder {

      abstract Builder setUrl(ValueProvider<String> url);

      abstract ValueProvider<String> url();

      abstract Builder setToken(ValueProvider<String> token);

      abstract ValueProvider<String> token();

      abstract Builder setBatchCount(ValueProvider<Integer> batchCount);

      abstract Builder setParallelism(ValueProvider<Integer> parallelism);

      abstract Builder setEnableBatchLogs(ValueProvider<Boolean> enableBatchLogs);

      abstract Builder setEnableGzipHttpCompression(
          ValueProvider<Boolean> enableGzipHttpCompression);

      abstract Write autoBuild();

      /**
       * Method to set the url for HEC event collector.
       *
       * @param url for HEC event collector
       * @return {@link Builder}
       */
      public Builder withUrl(ValueProvider<String> url) {
        checkArgument(url != null, "withURL(url) called with null input.");
        return setUrl(url);
      }

      /**
       * Same as {@link Builder#withUrl(ValueProvider)} but without {@link ValueProvider}.
       *
       * @param url for HEC event collector
       * @return {@link Builder}
       */
      public Builder withUrl(String url) {
        checkArgument(url != null, "withURL(url) called with null input.");
        return setUrl(ValueProvider.StaticValueProvider.of(url));
      }

      /**
       * Method to set the authentication token for HEC.
       *
       * @param token Authentication token for HEC event collector
       * @return {@link Builder}
       */
      public Builder withToken(ValueProvider<String> token) {
        checkArgument(token != null, "withToken(token) called with null input.");
        return setToken(token);
      }

      /**
       * Same as {@link Builder#withToken(ValueProvider)} but without {@link ValueProvider}.
       *
       * @param token for HEC event collector
       * @return {@link Builder}
       */
      public Builder withToken(String token) {
        checkArgument(token != null, "withToken(token) called with null input.");
        return setToken(ValueProvider.StaticValueProvider.of(token));
      }

      /**
       * Method to set the Batch Count.
       *
       * @param batchCount for batching post requests.
       * @return {@link Builder}
       */
      public Builder withBatchCount(ValueProvider<Integer> batchCount) {
        checkArgument(batchCount != null, "withBatchCount(batchCount) called with null input.");
        return setBatchCount(batchCount);
      }

      /**
       * Same as {@link Builder#withBatchCount(ValueProvider)} but without {@link ValueProvider}.
       *
       * @param batchCount for batching post requests.
       * @return {@link Builder}
       */
      public Builder withBatchCount(Integer batchCount) {
        checkArgument(
            batchCount != null,
            "withMaxEventsBatchCount(maxEventsBatchCount) called with null input.");
        return setBatchCount(ValueProvider.StaticValueProvider.of(batchCount));
      }

      /**
       * Method to set the parallelism.
       *
       * @param parallelism for controlling the number of http client connections.
       * @return {@link Builder}
       */
      public Builder withParallelism(ValueProvider<Integer> parallelism) {
        checkArgument(parallelism != null, "withParallelism(parallelism) called with null input.");
        return setParallelism(parallelism);
      }

      /**
       * Same as {@link Builder#withParallelism(ValueProvider)} but without {@link ValueProvider}.
       *
       * @param parallelism controlling the number of http client connections.
       * @return {@link Builder}
       */
      public Builder withParallelism(Integer parallelism) {
        checkArgument(parallelism != null, "withParallelism(parallelism) called with null input.");
        return setParallelism(ValueProvider.StaticValueProvider.of(parallelism));
      }

      /**
       * Method to enable batch logs.
       *
       * @param enableBatchLogs for enabling batch logs.
       * @return {@link Builder}
       */
      public Builder withEnableBatchLogs(ValueProvider<Boolean> enableBatchLogs) {
        return setEnableBatchLogs(enableBatchLogs);
      }

      /**
       * Same as {@link Builder#withEnableBatchLogs(ValueProvider)} but without a {@link
       * ValueProvider}.
       *
       * @param enableBatchLogs for enabling batch logs.
       * @return {@link Builder}
       */
      public Builder withEnableBatchLogs(Boolean enableBatchLogs) {
        return setEnableBatchLogs(ValueProvider.StaticValueProvider.of((enableBatchLogs)));
      }

      /**
       * Method to specify if HTTP requests sent to Datadog should be GZIP encoded.
       *
       * @param enableGzipHttpCompression whether to enable Gzip encoding.
       * @return {@link Builder}
       */
      public Builder withEnableGzipHttpCompression(
          ValueProvider<Boolean> enableGzipHttpCompression) {
        return setEnableGzipHttpCompression(enableGzipHttpCompression);
      }

      /**
       * Same as {@link Builder#withEnableGzipHttpCompression(ValueProvider)} but without a {@link
       * ValueProvider}.
       *
       * @param enableGzipHttpCompression whether to enable Gzip encoding.
       * @return {@link Builder}
       */
      public Builder withEnableGzipHttpCompression(Boolean enableGzipHttpCompression) {
        return setEnableGzipHttpCompression(
            ValueProvider.StaticValueProvider.of(enableGzipHttpCompression));
      }

      public Write build() {
        checkNotNull(url(), "HEC url is required.");
        checkNotNull(token(), "Authorization token is required.");

        return autoBuild();
      }
    }

    private static class CreateKeys
        extends PTransform<PCollection<DatadogEvent>, PCollection<KV<Integer, DatadogEvent>>> {

      private static final Integer DEFAULT_PARALLELISM = 1;

      private ValueProvider<Integer> requestedKeys;

      private CreateKeys(ValueProvider<Integer> requestedKeys) {
        this.requestedKeys = requestedKeys;
      }

      static CreateKeys of(ValueProvider<Integer> requestedKeys) {
        return new CreateKeys(requestedKeys);
      }

      @Override
      public PCollection<KV<Integer, DatadogEvent>> expand(PCollection<DatadogEvent> input) {

        return input
            .apply("Inject Keys", ParDo.of(new CreateKeysFn(this.requestedKeys)))
            .setCoder(KvCoder.of(BigEndianIntegerCoder.of(), DatadogEventCoder.of()));
      }

      private class CreateKeysFn extends DoFn<DatadogEvent, KV<Integer, DatadogEvent>> {

        private ValueProvider<Integer> specifiedParallelism;
        private Integer calculatedParallelism;

        CreateKeysFn(ValueProvider<Integer> specifiedParallelism) {
          this.specifiedParallelism = specifiedParallelism;
        }

        @Setup
        public void setup() {

          if (calculatedParallelism == null) {

            if (specifiedParallelism != null) {
              calculatedParallelism = specifiedParallelism.get();
            }

            calculatedParallelism =
                MoreObjects.firstNonNull(calculatedParallelism, DEFAULT_PARALLELISM);

            LOG.info("Parallelism set to: {}", calculatedParallelism);
          }
        }

        @ProcessElement
        public void processElement(ProcessContext context) {
          context.output(
              KV.of(ThreadLocalRandom.current().nextInt(calculatedParallelism), context.element()));
        }
      }
    }
  }
}
