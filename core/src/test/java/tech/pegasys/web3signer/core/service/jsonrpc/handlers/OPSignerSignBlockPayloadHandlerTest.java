/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.web3signer.core.service.jsonrpc.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.INVALID_PARAMS;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT;

import tech.pegasys.web3signer.core.service.http.handlers.signing.SignerForIdentifier;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonDecoder;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequest;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequestId;
import tech.pegasys.web3signer.core.service.jsonrpc.exceptions.JsonRpcException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OPSignerSignBlockPayloadHandlerTest {

  @Mock private JsonDecoder jsonDecoder;
  @Mock private SignerForIdentifier secpSigner;

  private OPSignerSignBlockPayloadHandler handler;

  @BeforeEach
  void setUp() {
    handler = new OPSignerSignBlockPayloadHandler(jsonDecoder, secpSigner);
  }

  @Test
  public void testValidRequest() {
    // Create test data
    final String address = "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6";
    final String domain = "0x0000000000000000000000000000000000000000000000000000000000000001";
    final String chainId = "0xa"; // 10 in hex
    final String payloadHash = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    final String senderAddress = "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6";

    // Create BlockPayloadArgs map
    final Map<String, Object> blockPayloadArgs = new HashMap<>();
    blockPayloadArgs.put("domain", domain);
    blockPayloadArgs.put("chainId", chainId);
    blockPayloadArgs.put("payloadHash", payloadHash);
    blockPayloadArgs.put("senderAddress", senderAddress);

    // Create request
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "opsigner_signBlockPayload");
    request.setId(new JsonRpcRequestId(1));
    request.setParams(List.of(address, blockPayloadArgs));

    // Mock signer behavior
    when(secpSigner.isSignerAvailable(anyString())).thenReturn(true);
    doReturn(
            Optional.of(
                "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"))
        .when(secpSigner)
        .sign(anyString(), any(Bytes.class));

    // Test the handler
    final String result = handler.createResponseResult(request);
    assertThat(result).isNotNull();
    assertThat(result).startsWith("0x");
    assertThat(result).hasSize(130); // 64 bytes = 128 hex chars + "0x" prefix
  }

  @Test
  public void testInvalidParams() {
    final JsonRpcRequest request = new JsonRpcRequest("2.0", "opsigner_signBlockPayload");
    request.setId(new JsonRpcRequestId(1));
    request.setParams(List.of("invalid")); // Only one parameter instead of two

    final Throwable thrown = catchThrowable(() -> handler.createResponseResult(request));
    assertThat(thrown).isInstanceOf(JsonRpcException.class);
    final JsonRpcException rpcException = (JsonRpcException) thrown;
    assertThat(rpcException.getJsonRpcError()).isEqualTo(INVALID_PARAMS);
  }

  @Test
  public void testAddressNotAvailable() {
    final String address = "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6";
    final Map<String, Object> blockPayloadArgs = new HashMap<>();
    blockPayloadArgs.put(
        "domain", "0x0000000000000000000000000000000000000000000000000000000000000001");
    blockPayloadArgs.put("chainId", "0xa");
    blockPayloadArgs.put(
        "payloadHash", "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    blockPayloadArgs.put("senderAddress", "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6");

    final JsonRpcRequest request = new JsonRpcRequest("2.0", "opsigner_signBlockPayload");
    request.setId(new JsonRpcRequestId(1));
    request.setParams(List.of(address, blockPayloadArgs));

    when(secpSigner.isSignerAvailable(anyString())).thenReturn(false);

    final Throwable thrown = catchThrowable(() -> handler.createResponseResult(request));
    assertThat(thrown).isInstanceOf(JsonRpcException.class);
    final JsonRpcException rpcException = (JsonRpcException) thrown;
    assertThat(rpcException.getJsonRpcError()).isEqualTo(SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT);
  }

  @Test
  public void testMissingRequiredFields() {
    final String address = "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6";
    final Map<String, Object> blockPayloadArgs = new HashMap<>();
    // Missing required fields
    blockPayloadArgs.put(
        "domain", "0x0000000000000000000000000000000000000000000000000000000000000001");
    // Missing chainId, payloadHash, senderAddress

    final JsonRpcRequest request = new JsonRpcRequest("2.0", "opsigner_signBlockPayload");
    request.setId(new JsonRpcRequestId(1));
    request.setParams(List.of(address, blockPayloadArgs));

    when(secpSigner.isSignerAvailable(anyString())).thenReturn(true);

    final Throwable thrown = catchThrowable(() -> handler.createResponseResult(request));
    assertThat(thrown).isInstanceOf(JsonRpcException.class);
    final JsonRpcException rpcException = (JsonRpcException) thrown;
    assertThat(rpcException.getJsonRpcError()).isEqualTo(INVALID_PARAMS);
  }

  @Test
  public void testInvalidDomainSize() {
    final String address = "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6";
    final Map<String, Object> blockPayloadArgs = new HashMap<>();
    blockPayloadArgs.put("domain", "0x1234"); // Too short, should be 32 bytes
    blockPayloadArgs.put("chainId", "0xa");
    blockPayloadArgs.put(
        "payloadHash", "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    blockPayloadArgs.put("senderAddress", "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6");

    final JsonRpcRequest request = new JsonRpcRequest("2.0", "opsigner_signBlockPayload");
    request.setId(new JsonRpcRequestId(1));
    request.setParams(List.of(address, blockPayloadArgs));

    when(secpSigner.isSignerAvailable(anyString())).thenReturn(true);

    final Throwable thrown = catchThrowable(() -> handler.createResponseResult(request));
    assertThat(thrown).isInstanceOf(JsonRpcException.class);
    final JsonRpcException rpcException = (JsonRpcException) thrown;
    assertThat(rpcException.getJsonRpcError()).isEqualTo(INVALID_PARAMS);
  }

  @Test
  public void testChainIdTooLarge() {
    final String address = "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6";
    final Map<String, Object> blockPayloadArgs = new HashMap<>();
    blockPayloadArgs.put(
        "domain", "0x0000000000000000000000000000000000000000000000000000000000000001");
    blockPayloadArgs.put("chainId", "0x" + "1".repeat(65)); // 65 hex chars = 260 bits > 256
    blockPayloadArgs.put(
        "payloadHash", "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    blockPayloadArgs.put("senderAddress", "0x742d35Cc6634C0532925a3b8D4C9db96C4b4d8b6");

    final JsonRpcRequest request = new JsonRpcRequest("2.0", "opsigner_signBlockPayload");
    request.setId(new JsonRpcRequestId(1));
    request.setParams(List.of(address, blockPayloadArgs));

    when(secpSigner.isSignerAvailable(anyString())).thenReturn(true);

    final Throwable thrown = catchThrowable(() -> handler.createResponseResult(request));
    assertThat(thrown).isInstanceOf(JsonRpcException.class);
    final JsonRpcException rpcException = (JsonRpcException) thrown;
    assertThat(rpcException.getJsonRpcError()).isEqualTo(INVALID_PARAMS);
  }
}
