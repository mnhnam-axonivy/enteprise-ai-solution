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

  interface LiteLlmExceptions {
    String NO_API_KEY_CONFIGURED = "No '%s' configured for %s. Calling the proxy unauthenticated.";
    String NO_BASE_URL_CONFIGURED = "No LiteLLM proxy configured. Set the variable '%s' to the URL of"
        + " your LiteLLM instance.";
    String NO_MODEL_SELECTED = "No LiteLLM model selected. Set the Agent's 'Model' field to one of: %s.";
    String AMBIGUOUS_MODEL = "Model '%s' is held by more than one LiteLLM virtual key: %s. Qualify the"
        + " Agent's 'Model' field as %s.";
    String UNKNOWN_MODEL = "Unknown LiteLLM model '%s'. Configured models: %s.";
    String KEY_IS_NOT_A_MODEL = "'%s' is a LiteLLM virtual key, not a model. Set the Agent's 'Model'"
        + " field to one of the models it holds: %s.";
    String MODEL_NOT_HELD = "LiteLLM virtual key '%1$s' does not hold model '%2$s'. Models held by"
        + " '%1$s': %3$s.";
  }

  record Route(LiteLlmVirtualKey key, String model) {}

  public static OpenAiChatModelBuilder buildOpenAiModel(String model) {
    Route route = resolve(model);

    OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
        .httpClientBuilder(new SmartHttpClientBuilderFactory().create())
        .baseUrl(baseUrl())
        .logRequests(true)
        .logResponses(true);

    apiKey(route.key()).ifPresentOrElse(builder::apiKey, () -> {
      Ivy.log().warn(String.format(LiteLlmExceptions.NO_API_KEY_CONFIGURED, LiteLlmConf.API_KEY_FIELD,
          describe(route.key())));
      builder.customHeaders(Map.of("X-Requested-By", "ivy"));
    });

    builder.defaultRequestParameters(ChatRequestParameters.builder()
        .modelName(route.model())
        .build());

    return builder;
  }

  private static Route resolve(String model) {
    return resolve(VariableUtils.getVirtualKeys(), model);
  }

  static Route resolve(List<LiteLlmVirtualKey> keys, String model) {
    String selected = StringUtils.strip(model);
    if (StringUtils.isBlank(selected)) {
      throw new IllegalStateException(String.format(LiteLlmExceptions.NO_MODEL_SELECTED,
          VariableUtils.modelNames(keys)));
    }

    var holders = keys.stream().filter(key -> key.holds(selected)).toList();
    if (holders.size() == 1) {
      return new Route(holders.get(0), selected);
    }
    if (holders.size() > 1) {
      throw new IllegalStateException(String.format(LiteLlmExceptions.AMBIGUOUS_MODEL, selected,
          aliases(holders), qualifiedForms(holders, selected)));
    }
    return resolveQualified(keys, selected);
  }

  private static Route resolveQualified(List<LiteLlmVirtualKey> keys, String selected) {
    String alias = StringUtils.substringBefore(selected, LiteLlmConf.QUALIFIER);
    String model = StringUtils.substringAfter(selected, LiteLlmConf.QUALIFIER);
    var qualified = keys.stream()
        .filter(key -> key.getAlias().equals(alias))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException(String.format(LiteLlmExceptions.UNKNOWN_MODEL,
            selected, VariableUtils.modelNames(keys))));

    if (StringUtils.isEmpty(model)) {
      throw new IllegalStateException(String.format(LiteLlmExceptions.KEY_IS_NOT_A_MODEL, selected,
          qualified.getModels()));
    }
    if (!qualified.holds(model)) {
      throw new IllegalStateException(String.format(LiteLlmExceptions.MODEL_NOT_HELD, alias, model,
          qualified.getModels()));
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

  private static String baseUrl() {
    String baseUrl = StringUtils.trimToNull(Ivy.var().get(LiteLlmConf.BASE_URL));
    if (baseUrl == null) {
      throw new IllegalStateException(String.format(LiteLlmExceptions.NO_BASE_URL_CONFIGURED,
          LiteLlmConf.BASE_URL));
    }
    return baseUrl;
  }

  private static Optional<String> apiKey(LiteLlmVirtualKey key) {
    return Optional.ofNullable(StringUtils.trimToNull(key.getApiKey()));
  }

  private static String describe(LiteLlmVirtualKey key) {
    return "LiteLLM virtual key '" + key.getAlias() + "'";
  }
}
