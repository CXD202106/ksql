/*
 * Copyright 2019 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"; you may not use
 * this file except in compliance with the License. You may obtain a copy of the
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

import static io.confluent.ksql.rest.server.KsqlRestConfig.KSQL_CONNECT_SERVER_ERROR_HANDLER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.confluent.ksql.config.SessionConfig;
import io.confluent.ksql.execution.expression.tree.StringLiteral;
import io.confluent.ksql.parser.KsqlParser.PreparedStatement;
import io.confluent.ksql.parser.tree.CreateConnector;
import io.confluent.ksql.parser.tree.CreateConnector.Type;
import io.confluent.ksql.rest.SessionProperties;
import io.confluent.ksql.rest.entity.CreateConnectorEntity;
import io.confluent.ksql.rest.entity.ErrorEntity;
import io.confluent.ksql.rest.entity.KsqlEntity;
import io.confluent.ksql.rest.entity.WarningEntity;
import io.confluent.ksql.rest.server.KsqlRestConfig;
import io.confluent.ksql.services.ConnectClient;
import io.confluent.ksql.services.ConnectClient.ConnectResponse;
import io.confluent.ksql.services.ServiceContext;
import io.confluent.ksql.statement.ConfiguredStatement;
import io.confluent.ksql.util.KsqlConfig;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorInfo;
import org.apache.kafka.connect.runtime.rest.entities.ConnectorType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ConnectExecutorTest {

  private static final KsqlConfig CONFIG = new KsqlConfig(ImmutableMap.of());
  private static final KsqlRestConfig REST_CONFIG = new KsqlRestConfig(ImmutableMap.of());

  private static final CreateConnector CREATE_CONNECTOR = new CreateConnector(
      "foo", ImmutableMap.of("foo", new StringLiteral("bar")), Type.SOURCE,
      false);

  private static final ConfiguredStatement<CreateConnector> CREATE_CONNECTOR_CONFIGURED =
      ConfiguredStatement.of(PreparedStatement.of(
          "CREATE SOURCE CONNECTOR foo WITH ('foo'='bar');",
          CREATE_CONNECTOR), SessionConfig.of(CONFIG, ImmutableMap.of()));

  private static final CreateConnector CREATE_DUPLICATE_CONNECTOR = new CreateConnector(
      "foo", ImmutableMap.of("foo", new StringLiteral("bar")), Type.SOURCE,
      true);

  private static final ConfiguredStatement<CreateConnector> CREATE_DUPLICATE_CONNECTOR_CONFIGURED =
      ConfiguredStatement.of(PreparedStatement.of(
          "CREATE SOURCE CONNECTOR IF NOT EXISTS foo WITH ('foo'='bar');",
          CREATE_DUPLICATE_CONNECTOR), SessionConfig.of(CONFIG, ImmutableMap.of())
      );

  @Mock
  private ServiceContext serviceContext;
  @Mock
  private ConnectClient connectClient;

  @Before
  public void setUp() {
    when(serviceContext.getConnectClient()).thenReturn(connectClient);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldPassInCorrectArgsToConnectClient() {
    // Given:
    givenSuccess();

    // When:
    ConnectExecutor
        .execute(CREATE_CONNECTOR_CONFIGURED,
            mock(SessionProperties.class),
            null,
            serviceContext,
            mock(KsqlRestConfig.class));

    // Then:
    verify(connectClient).create(eq("foo"), (Map<String, String>) argThat(hasEntry("foo", "bar")));
  }

  @Test
  public void shouldReturnConnectorInfoEntityOnSuccess() {
    // Given:
    givenSuccess();

    // When:
    final Optional<KsqlEntity> entity = ConnectExecutor
        .execute(CREATE_CONNECTOR_CONFIGURED,
            mock(SessionProperties.class),
            null,
            serviceContext,
            REST_CONFIG).getEntity();

    // Then:
    assertThat("Expected non-empty response", entity.isPresent());
    assertThat(entity.get(), instanceOf(CreateConnectorEntity.class));
  }

  @Test
  public void shouldReturnErrorEntityOnError() {
    // Given:
    givenError();

    // When:
    final Optional<KsqlEntity> entity = ConnectExecutor
        .execute(CREATE_CONNECTOR_CONFIGURED,
            mock(SessionProperties.class),
            null,
            serviceContext,
            REST_CONFIG).getEntity();

    // Then:
    assertThat("Expected non-empty response", entity.isPresent());
    assertThat(entity.get(), instanceOf(ErrorEntity.class));
  }

  @Test
  public void shouldReturnWarningWhenIfNotExistsSetConnectorExists() {
    //Given:
    givenConnectorExists();

    //When
    final Optional<KsqlEntity> entity = ConnectExecutor
        .execute(CREATE_DUPLICATE_CONNECTOR_CONFIGURED,
            mock(SessionProperties.class),
            null,
            serviceContext,
            REST_CONFIG).getEntity();
    //Then
    assertThat("Expected non-empty response", entity.isPresent());
    assertThat(entity.get(), instanceOf(WarningEntity.class));
  }

  @Test
  public void shouldReturnErrorIfConnectorExists() {
    //Given:
    when(connectClient.create(anyString(), anyMap()))
        .thenReturn(
            ConnectResponse.failure("Connector foo already exists", HttpStatus.SC_CONFLICT));

    // When:
    final Optional<KsqlEntity> entity = ConnectExecutor
        .execute(CREATE_CONNECTOR_CONFIGURED,
            mock(SessionProperties.class),
            null,
            serviceContext,
            REST_CONFIG).getEntity();

    // Then:
    assertThat("Expected non-empty response", entity.isPresent());
    assertThat(entity.get(), instanceOf(ErrorEntity.class));
  }

  @Test
  public void shouldReturnPluggableErrors() {
    //Given:
    when(connectClient.create(anyString(), anyMap()))
        .thenReturn(
            ConnectResponse.failure("FORBIDDEN", HttpStatus.SC_FORBIDDEN));

    final KsqlRestConfig restConfig = new KsqlRestConfig(ImmutableMap.of(
        KSQL_CONNECT_SERVER_ERROR_HANDLER, DummyConnectServerErrors.class));

    // When:
    final Optional<KsqlEntity> entity = ConnectExecutor
        .execute(CREATE_CONNECTOR_CONFIGURED,
            mock(SessionProperties.class),
            null,
            serviceContext,
            restConfig).getEntity();

    // Then:
    assertThat("Expected non-empty response", entity.isPresent());
    assertThat(entity.get(), instanceOf(ErrorEntity.class));
    assertThat(((ErrorEntity) entity.get()).getErrorMessage(),
        is(DummyConnectServerErrors.FORBIDDEN_ERR));
  }

  private void givenSuccess() {
    when(connectClient.create(anyString(), anyMap()))
        .thenReturn(ConnectResponse.success(
            new ConnectorInfo(
                "foo",
                ImmutableMap.of(),
                ImmutableList.of(),
                ConnectorType.SOURCE), HttpStatus.SC_OK));

  }

  private void givenError() {
    when(connectClient.create(anyString(), anyMap()))
        .thenReturn(ConnectResponse.failure("error!", HttpStatus.SC_BAD_REQUEST));
  }

  private void givenConnectorExists() {
    when(connectClient.connectors())
        .thenReturn(ConnectResponse.success(
            Arrays.asList("foo", "bar"), HttpStatus.SC_OK));
  }

  // must be public to allow KsqlRestConfig to create an instance
  public static class DummyConnectServerErrors implements ConnectServerErrors {

    static final String FORBIDDEN_ERR = "FORBIDDEN";
    static final String UNAUTHORIZED_ERR = "UNAUTHORIZED";
    static final String DEFAULT_ERR = "DEFAULT";

    @Override
    public Optional<KsqlEntity> handleForbidden(
        final ConfiguredStatement<CreateConnector> statement,
        final ConnectResponse<ConnectorInfo> response) {
      return Optional.of(new ErrorEntity(statement.getStatementText(), FORBIDDEN_ERR));
    }

    @Override
    public Optional<KsqlEntity> handleUnauthorized(
        final ConfiguredStatement<CreateConnector> statement,
        final ConnectResponse<ConnectorInfo> response) {
      return Optional.of(new ErrorEntity(statement.getStatementText(), UNAUTHORIZED_ERR));
    }

    @Override
    public Optional<KsqlEntity> handleDefault(
        final ConfiguredStatement<CreateConnector> statement,
        final ConnectResponse<ConnectorInfo> response) {
      return Optional.of(new ErrorEntity(statement.getStatementText(), DEFAULT_ERR));
    }
  }
}