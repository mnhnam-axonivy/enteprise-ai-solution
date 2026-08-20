package com.axonivy.utils.smart.workflow.model.litellm.internal;

public interface LiteLlmConf {
  String PREFIX = "AI.Providers.LiteLLM.";
  String BASE_URL = PREFIX + "BaseUrl";
  String VIRTUAL_KEYS = PREFIX + "VirtualKeys";

  String API_KEY_FIELD = "APIKey";
  String MODELS_FIELD = "Models";

  /**
   * Separates a virtual key alias from a model alias in the Agent's 'Model' field, for
   * the case where two keys publish the same model.
   */
  String QUALIFIER = "/";
}
