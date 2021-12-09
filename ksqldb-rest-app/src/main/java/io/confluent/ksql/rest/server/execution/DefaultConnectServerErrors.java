/*
 * Copyright 2018 Confluent Inc.
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

package io.confluent.ksql.rest.server.execution;

import io.confluent.ksql.parser.tree.CreateConnector;
import io.confluent.ksql.rest.entity.ErrorEntity;
import io.confluent.ksql.rest.entity.KsqlEntity;
import io.confluent.ksql.services.ConnectClient.ConnectResponse;
import io.confluent.ksql.statement.ConfiguredStatement;
import java.util.Optional;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorInfo;

public class DefaultConnectServerErrors implements ConnectServerErrors {

  @Override
  public Optional<KsqlEntity> handleForbidden(
      final ConfiguredStatement<CreateConnector> statement,
      final ConnectResponse<ConnectorInfo> response) {
    return handleDefault(statement, response);
  }

  @Override
  public Optional<KsqlEntity> handleUnauthorized(
      final ConfiguredStatement<CreateConnector> statement,
      final ConnectResponse<ConnectorInfo> response) {
    return handleDefault(statement, response);
  }

  @Override
  public Optional<KsqlEntity> handleDefault(
      final ConfiguredStatement<CreateConnector> statement,
      final ConnectResponse<ConnectorInfo> response) {
    return response.error().map(err -> new ErrorEntity(statement.getStatementText(), err));
  }
}
