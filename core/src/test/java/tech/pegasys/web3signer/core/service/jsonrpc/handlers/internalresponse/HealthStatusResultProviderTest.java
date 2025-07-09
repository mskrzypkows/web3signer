/*
 * Copyright 2024 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.web3signer.core.service.jsonrpc.handlers.internalresponse;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.web3signer.common.ApplicationInfo;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequest;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequestId;

import org.junit.jupiter.api.Test;

class HealthStatusResultProviderTest {

  @Test
  void shouldReturnWeb3SignerVersion() {
    final HealthStatusResultProvider provider = new HealthStatusResultProvider();
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "health_status");
    request.setId(new JsonRpcRequestId(1));

    final String result = provider.createResponseResult(request);

    assertThat(result).isEqualTo(ApplicationInfo.version());
    assertThat(result).startsWith("web3signer/v");
  }
}
