/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.astraea.app.consumer;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;

import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.astraea.app.admin.TopicPartition;

public class PartitionsBuilder<Key, Value> extends Builder<Key, Value> {
  private final Set<TopicPartition> partitions;

  PartitionsBuilder(Set<TopicPartition> partitions) {
    this.partitions = requireNonNull(partitions);
  }

  /**
   * make the consumer read data from beginning. By default, it reads the latest data.
   *
   * @return this builder
   */
  @Override
  public PartitionsBuilder<Key, Value> fromBeginning() {
    super.fromBeginning();
    return this;
  }

  /**
   * make the consumer read data from latest. this is default setting.
   *
   * @return this builder
   */
  @Override
  public PartitionsBuilder<Key, Value> fromLatest() {
    super.fromLatest();
    return this;
  }

  /**
   * set the offset to read from the latest offset. For example, the end offset is 5, and you set
   * distanceFromLatest to 2, then you will read data from offset: 3
   *
   * @param distanceFromLatest the distance from the latest offset
   * @return this builder
   */
  @Override
  public PartitionsBuilder<Key, Value> distanceFromLatest(int distanceFromLatest) {
    super.distanceFromLatest(distanceFromLatest);
    return this;
  }

  @Override
  public <NewKey> PartitionsBuilder<NewKey, Value> keyDeserializer(
      Deserializer<NewKey> keyDeserializer) {
    return (PartitionsBuilder<NewKey, Value>) super.keyDeserializer(keyDeserializer);
  }

  @Override
  public <NewValue> PartitionsBuilder<Key, NewValue> valueDeserializer(
      Deserializer<NewValue> valueDeserializer) {
    return (PartitionsBuilder<Key, NewValue>) super.valueDeserializer(valueDeserializer);
  }

  public PartitionsBuilder<Key, Value> config(String key, String value) {
    this.configs.put(key, value);
    return this;
  }

  public PartitionsBuilder<Key, Value> configs(Map<String, String> configs) {
    this.configs.putAll(configs);
    return this;
  }

  @Override
  public PartitionsBuilder<Key, Value> bootstrapServers(String bootstrapServers) {
    super.bootstrapServers(bootstrapServers);
    return this;
  }

  @Override
  public PartitionsBuilder<Key, Value> isolation(Isolation isolation) {
    super.isolation(isolation);
    return this;
  }

  @SuppressWarnings("unchecked")
  @Override
  public AssignedConsumer<Key, Value> build() {
    var kafkaConsumer =
        new KafkaConsumer<>(
            configs,
            Deserializer.of((Deserializer<Key>) keyDeserializer),
            Deserializer.of((Deserializer<Value>) valueDeserializer));
    kafkaConsumer.assign(partitions.stream().map(TopicPartition::to).collect(toList()));

    // this mode is not supported by kafka, so we have to calculate the offset first
    if (distanceFromLatest > 0) {
      var partitions = kafkaConsumer.assignment();
      // 1) get the end offsets from all subscribed partitions
      var endOffsets = kafkaConsumer.endOffsets(partitions);
      // 2) calculate and then seek to the correct offset (end offset - recent offset)
      endOffsets.forEach(
          (tp, latest) -> kafkaConsumer.seek(tp, Math.max(0, latest - distanceFromLatest)));
    }

    return new AssignedConsumerImpl<>(kafkaConsumer);
  }

  private static class AssignedConsumerImpl<Key, Value> extends Builder.BaseConsumer<Key, Value>
      implements AssignedConsumer<Key, Value> {

    public AssignedConsumerImpl(Consumer<Key, Value> kafkaConsumer) {
      super(kafkaConsumer);
    }
  }
}
