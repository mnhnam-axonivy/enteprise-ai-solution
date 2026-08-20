package com.axonivy.utils.smart.workflow.model.litellm.internal;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.axonivy.utils.smart.workflow.client.SmartHttpClientBuilderFactory;
import com.axonivy.utils.smart.workflow.model.litellm.internal.entity.LiteLlmVirtualKey;
import com.axonivy.utils.smart.workflow.model.litellm.internal.utils.VariableUtils;

import ch.ivyteam.ivy.environment.Ivy;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;

public class LiteLlmServiceConnector {

  private static final String FALLBACK_BASE_URL = "http://localhost:4000/v1";

  /** A model alias resolved to the one virtual key allowed to call it. */
  record Route(LiteLlmVirtualKey key, String model) {}

  /**
   * @param model the Agent element's 'Model' field: a model alias published by the proxy,
   *        optionally qualified as {@code virtualKey/model} to choose between two keys
   *        that both publish that alias. Mandatory - this provider has no default model.
   * @throws IllegalStateException when the field is blank, names an unknown model, or
   *         names one held by several keys without qualifying it. Deliberately not
   *         {@code null}: the caller dereferences the built model straight away, so a
   *         message naming what to fix is the only useful outcome.
   */
  public static OpenAiChatModelBuilder buildOpenAiModel(String model) {
    Route route = resolve(model);

    OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
        .httpClientBuilder(new SmartHttpClientBuilderFactory().create())
        .baseUrl(baseUrl(route.key()))
        .logRequests(true)
        .logResponses(true);

    apiKey(route.key()).ifPresentOrElse(builder::apiKey, () -> {
      // only a proxy started without a master key accepts unauthenticated requests, so
      // a missing key is far more often a typo than a deliberate choice
      Ivy.log().warn("No '" + LiteLlmConf.API_KEY_FIELD + "' configured for " + describe(route.key())
          + ". Calling the proxy unauthenticated.");
      builder.customHeaders(Map.of("X-Requested-By", "ivy"));
    });

    // the bare alias, never the 'virtualKey/' qualifier: that prefix is Ivy-side routing
    // and must reach neither the proxy nor AgentCallExecutor, which reads the name back
    // from defaultRequestParameters() for observability.
    // Temperature is deliberately absent - a LiteLLM alias is opaque and may resolve to a
    // reasoning model that rejects the parameter, so per-model defaults belong on the
    // proxy ('litellm_params'), which is also where they apply to every caller.
    builder.defaultRequestParameters(ChatRequestParameters.builder()
        .modelName(route.model())
        .build());

    return builder;
  }

  /**
   * Resolves the 'Model' field against the configured keys. An exact match on a declared
   * alias is tried before the {@code virtualKey/model} form, because a LiteLLM alias may
   * itself contain a slash: 'openai/gpt-4o' is a model name, not key 'openai'. That order
   * is safe because a key alias is a variable key and can never contain a slash.
   */
  private static Route resolve(String model) {
    return resolve(VariableUtils.getVirtualKeys(), model);
  }

  /** Package-private so the resolution rules can be exercised without an Ivy runtime. */
  static Route resolve(List<LiteLlmVirtualKey> keys, String model) {
    String selected = StringUtils.strip(model);
    if (StringUtils.isBlank(selected)) {
      throw new IllegalStateException("No LiteLLM model selected. Set the Agent's 'Model' field to one of: "
          + VariableUtils.modelNames(keys) + ".");
    }

    var holders = keys.stream().filter(key -> key.holds(selected)).toList();
    if (holders.size() == 1) {
      return new Route(holders.get(0), selected);
    }
    if (holders.size() > 1) {
      throw new IllegalStateException("Model '" + selected + "' is held by more than one LiteLLM virtual"
          + " key: " + aliases(holders) + ". Qualify the Agent's 'Model' field as "
          + qualifiedForms(holders, selected) + ".");
    }
    return resolveQualified(keys, selected);
  }

  /**
   * The {@code virtualKey/model} form, plus the two mistakes it invites: naming a key that
   * does not hold the model, and naming a key where a model was expected.
   */
  private static Route resolveQualified(List<LiteLlmVirtualKey> keys, String selected) {
    String alias = StringUtils.substringBefore(selected, LiteLlmConf.QUALIFIER);
    String model = StringUtils.substringAfter(selected, LiteLlmConf.QUALIFIER);
    var qualified = keys.stream()
        .filter(key -> key.getAlias().equals(alias))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Unknown LiteLLM model '" + selected
            + "'. Configured models: " + VariableUtils.modelNames(keys) + "."));

    if (StringUtils.isEmpty(model)) {
      throw new IllegalStateException("'" + selected + "' is a LiteLLM virtual key, not a model. Set the"
          + " Agent's 'Model' field to one of the models it holds: " + qualified.getModels() + ".");
    }
    if (!qualified.holds(model)) {
      throw new IllegalStateException("LiteLLM virtual key '" + alias + "' does not hold model '" + model
          + "'. Models held by '" + alias + "': " + qualified.getModels() + ".");
    }
    return new Route(qualified, model);
  }

  private static List<String> aliases(List<LiteLlmVirtualKey> keys) {
    return keys.stream().map(LiteLlmVirtualKey::getAlias).toList();
  }

  private static String qualifiedForms(List<LiteLlmVirtualKey> keys, String model) {
    return keys.stream()
        .map(key -> "'" + key.getAlias() + LiteLlmConf.QUALIFIER + model + "'")
        .collect(Collectors.joining(" or "));
  }

  /** Key override, else the shared proxy URL, else the local default. */
  private static String baseUrl(LiteLlmVirtualKey key) {
    return StringUtils.firstNonBlank(
        StringUtils.trim(key.getBaseUrl()),
        StringUtils.trim(Ivy.var().get(LiteLlmConf.BASE_URL)),
        FALLBACK_BASE_URL);
  }

  /** The key's own value. Empty only for an unauthenticated proxy. */
  private static Optional<String> apiKey(LiteLlmVirtualKey key) {
    return Optional.ofNullable(StringUtils.trimToNull(key.getApiKey()));
  }

  private static String describe(LiteLlmVirtualKey key) {
    return "LiteLLM virtual key '" + key.getAlias() + "'";
  }
}
