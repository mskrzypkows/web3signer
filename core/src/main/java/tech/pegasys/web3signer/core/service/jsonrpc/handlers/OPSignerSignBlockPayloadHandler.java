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

import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.INVALID_PARAMS;
import static tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcError.SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT;
import static tech.pegasys.web3signer.signing.util.IdentifierUtils.normaliseIdentifier;

import tech.pegasys.web3signer.core.service.http.handlers.signing.SignerForIdentifier;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonDecoder;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequest;
import tech.pegasys.web3signer.core.service.jsonrpc.JsonRpcRequestHandler;
import tech.pegasys.web3signer.core.service.jsonrpc.exceptions.JsonRpcException;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcErrorResponse;
import tech.pegasys.web3signer.core.service.jsonrpc.response.JsonRpcSuccessResponse;

import java.math.BigInteger;
import java.util.List;

import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;

public class OPSignerSignBlockPayloadHandler implements JsonRpcRequestHandler {

  private static final Logger LOG = LogManager.getLogger();
  private final SignerForIdentifier secpSigner;

  public OPSignerSignBlockPayloadHandler(
      final JsonDecoder jsonDecoder, final SignerForIdentifier secpSigner) {
    this.secpSigner = secpSigner;
  }

  @Override
  public void handle(final RoutingContext context, final JsonRpcRequest request) {
    LOG.debug("Processing opsigner_signBlockPayload request {}", request.getId());

    try {
      final String signature = createResponseResult(request);
      final JsonRpcSuccessResponse response =
          new JsonRpcSuccessResponse(request.getId(), signature);
      context.response().end(Json.encodeToBuffer(response));
    } catch (final JsonRpcException e) {
      final JsonRpcErrorResponse errorResponse =
          new JsonRpcErrorResponse(request.getId(), e.getJsonRpcError());
      context.response().end(Json.encodeToBuffer(errorResponse));
    } catch (final Exception e) {
      LOG.error("Unexpected error processing opsigner_signBlockPayload request", e);
      final JsonRpcErrorResponse errorResponse =
          new JsonRpcErrorResponse(request.getId(), INVALID_PARAMS);
      context.response().end(Json.encodeToBuffer(errorResponse));
    }
  }

  String createResponseResult(final JsonRpcRequest request) {
    final List<?> params = validateAndGetParams(request);

    // Parse parameters
    final Object blockPayloadArgsParam = params.get(0);
    if (!(blockPayloadArgsParam instanceof java.util.Map)) {
      LOG.debug("blockPayloadArgsParam is not a map: {}", blockPayloadArgsParam);
      throw new JsonRpcException(INVALID_PARAMS);
    }

    @SuppressWarnings("unchecked")
    final java.util.Map<String, Object> blockPayloadArgs =
        (java.util.Map<String, Object>) blockPayloadArgsParam;

    // Extract parameters from the map
    final Object domainObj = blockPayloadArgs.get("domain");
    final Object chainIdObj = blockPayloadArgs.get("chainId");
    final Object payloadHashObj = blockPayloadArgs.get("payloadHash");
    final String senderAddressHex = (String) blockPayloadArgs.get("senderAddress");

    LOG.debug("domainObj: {}", domainObj);
    LOG.debug("chainIdObj: {}", chainIdObj);
    LOG.debug("payloadHashObj: {}", payloadHashObj);
    LOG.debug("senderAddressHex: {}", senderAddressHex);

    if (domainObj == null
        || chainIdObj == null
        || payloadHashObj == null
        || senderAddressHex == null) {
      LOG.debug("Missing required fields");
      throw new JsonRpcException(INVALID_PARAMS);
    }

    // Convert parameters to the correct format
    final Bytes domain = parseDomain(domainObj);
    final BigInteger chainId = parseChainId(chainIdObj);
    final Bytes payloadHash = parsePayloadHash(payloadHashObj);
    final String senderAddress = normaliseIdentifier(senderAddressHex);

    if (!secpSigner.isSignerAvailable(senderAddress)) {
      LOG.debug("Address {} not available for signing", senderAddress);
      throw new JsonRpcException(SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT);
    }

    // Create signing hash according to Go implementation
    final Bytes messageToSign = createMessageToSign(domain, chainId, payloadHash);

    // Sign the hash
    final String result =
        secpSigner
            .sign(senderAddress, messageToSign)
            .orElseThrow(
                () -> {
                  LOG.debug("Unexpected failure signing for {}", senderAddress);
                  return new JsonRpcException(SIGNING_FROM_IS_NOT_AN_UNLOCKED_ACCOUNT);
                });

    // Sign.signedMessageToKey(messageToSign, )
    // Signature signature = new Signature(new BigInteger(result, 16));
    LOG.debug("result: {}", result);
    byte[] resultBytes = org.apache.tuweni.bytes.Bytes.fromHexString(result).toArray();
    resultBytes[64] -= 27;
    final String result2 = org.apache.tuweni.bytes.Bytes.wrap(resultBytes).toHexString();

    LOG.debug("result2: {}", result2);
    return result2;
  }

  private Bytes createMessageToSign(
      final Bytes domain, final BigInteger chainId, final Bytes payloadHash) {
    // Create message input: [32 + 32 + 32] bytes
    final MutableBytes msgInput = MutableBytes.create(96);

    // domain: first 32 bytes
    domain.copyTo(msgInput, 0);

    // chain_id: second 32 bytes (big-endian, padded to 32 bytes)
    final byte[] chainIdBytes = chainId.toByteArray();
    if (chainIdBytes.length > 32) {
      LOG.debug("chainId is too large");
      throw new JsonRpcException(INVALID_PARAMS);
    }
    // Copy chainId bytes to the end of the 32-byte section (big-endian)
    System.arraycopy(chainIdBytes, 0, msgInput.toArrayUnsafe(), 32, chainIdBytes.length);

    // payload_hash: third 32 bytes
    payloadHash.copyTo(msgInput, 64);

    return Bytes.wrap(msgInput.toArrayUnsafe());
  }

  private List<?> validateAndGetParams(final JsonRpcRequest request) {
    final Object params = request.getParams();
    if (params == null) {
      LOG.debug("params is null");
      throw new JsonRpcException(INVALID_PARAMS);
    }
    // params list with BlockPayloadArgs object
    if (params instanceof List<?> paramList) {
      if (paramList.size() != 1) {
        LOG.debug("paramList size is not 1");
        throw new JsonRpcException(INVALID_PARAMS);
      }
      return paramList;
    } else {
      LOG.debug("params is not a list: {}", params);
      throw new JsonRpcException(INVALID_PARAMS);
    }
  }

  private Bytes parseDomain(final Object domainObj) {
    if (domainObj instanceof List<?>) {
      // Handle array format: [0, 0, 0, ...]
      final List<?> domainList = (List<?>) domainObj;
      if (domainList.size() != 32) {
        LOG.debug("domainList size is not 32");
        throw new JsonRpcException(INVALID_PARAMS);
      }
      final byte[] domainBytes = new byte[32];
      for (int i = 0; i < 32; i++) {
        final Object element = domainList.get(i);
        if (element instanceof Number) {
          domainBytes[i] = ((Number) element).byteValue();
        } else {
          LOG.debug("domainList element is not a number: {}", element);
          throw new JsonRpcException(INVALID_PARAMS);
        }
      }
      return Bytes.wrap(domainBytes);
    } else if (domainObj instanceof String) {
      // Handle hex string format: "0x..."
      final String domainHex = (String) domainObj;
      if (!domainHex.startsWith("0x")) {
        LOG.debug("domainHex does not start with 0x");
        throw new JsonRpcException(INVALID_PARAMS);
      }
      final Bytes domain = Bytes.fromHexString(domainHex);
      if (domain.size() != 32) {
        LOG.debug("domain is not 32 bytes");
        throw new JsonRpcException(INVALID_PARAMS);
      }
      return domain;
    } else {
      LOG.debug("domainObj is not a list or string: {}", domainObj);
      throw new JsonRpcException(INVALID_PARAMS);
    }
  }

  private BigInteger parseChainId(final Object chainIdObj) {
    if (chainIdObj instanceof Number) {
      // Handle number format: 167001
      final BigInteger chainId = BigInteger.valueOf(((Number) chainIdObj).longValue());
      if (chainId.bitLength() > 256) {
        LOG.debug("chainId is too large");
        throw new JsonRpcException(INVALID_PARAMS);
      }
      return chainId;
    } else if (chainIdObj instanceof String) {
      // Handle hex string format: "0xa"
      final String chainIdHex = (String) chainIdObj;
      if (!chainIdHex.startsWith("0x")) {
        LOG.debug("chainIdHex does not start with 0x");
        throw new JsonRpcException(INVALID_PARAMS);
      }
      final BigInteger chainId = new BigInteger(chainIdHex.substring(2), 16);
      if (chainId.bitLength() > 256) {
        LOG.debug("chainId is too large");
        throw new JsonRpcException(INVALID_PARAMS);
      }
      return chainId;
    } else {
      LOG.debug("chainIdObj is not a number or string: {}", chainIdObj);
      throw new JsonRpcException(INVALID_PARAMS);
    }
  }

  private Bytes parsePayloadHash(final Object payloadHashObj) {
    if (payloadHashObj instanceof String) {
      final String payloadHashStr = (String) payloadHashObj;
      if (payloadHashStr.startsWith("0x")) {
        // Handle hex string format: "0x..."
        final Bytes payloadHash = Bytes.fromHexString(payloadHashStr);
        if (payloadHash.size() != 32) {
          LOG.debug("payloadHash is not 32 bytes");
          throw new JsonRpcException(INVALID_PARAMS);
        }
        return payloadHash;
      } else {
        // Handle base64 format: "NU63txpobdLMqmU7GmQ1q9u2n/Z4mxKvI93a8sKT23o="
        try {
          final byte[] payloadHashBytes = java.util.Base64.getDecoder().decode(payloadHashStr);
          if (payloadHashBytes.length != 32) {
            LOG.debug("payloadHashBytes is not 32 bytes");
            throw new JsonRpcException(INVALID_PARAMS);
          }
          return Bytes.wrap(payloadHashBytes);
        } catch (final IllegalArgumentException e) {
          LOG.debug("payloadHashStr is not a valid base64 string");
          throw new JsonRpcException(INVALID_PARAMS);
        }
      }
    } else {
      LOG.debug("payloadHashObj is not a string: {}", payloadHashObj);
      throw new JsonRpcException(INVALID_PARAMS);
    }
  }
}
