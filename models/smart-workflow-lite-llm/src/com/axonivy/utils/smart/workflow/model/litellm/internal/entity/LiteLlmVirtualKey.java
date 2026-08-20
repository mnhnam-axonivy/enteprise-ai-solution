package com.axonivy.utils.smart.workflow.model.litellm.internal.entity;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * One LiteLLM virtual key and the model aliases it may call. A model is only callable by
 * a key that holds it, so the two always travel together - but a key usually holds
 * several models, which is why the key owns the list and not the other way round.
 * <p>
 * A key's organization, team, budget, rate limits and model scoping are all resolved by
 * the proxy when the key is presented. Ivy only holds the key value and the aliases it
 * routes.
 * <p>
 * {@code baseUrl} is the only setting that falls back to the provider-level variable, so
 * several keys can share one proxy without repeating its URL. Nothing else is inherited.
 */
public class LiteLlmVirtualKey {
  private final String alias;
  private String apiKey;
  private String baseUrl;
  private List<String> models = List.of();

  public LiteLlmVirtualKey(String alias) {
    this.alias = alias;
  }

  /**
   * The 'key_alias' of the virtual key in the LiteLLM UI, which is this entry's variable
   * name. Because {@code apiKey} is stored encrypted and therefore unrecognisable in the
   * config, this is what makes rotation and auditing possible at all.
   */
  public String getAlias() {
    return alias;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public List<String> getModels() {
    return models;
  }

  /**
   * The aliases arrive as one comma-separated value rather than as nested variable keys,
   * which is what lets them be written verbatim: 'gpt-4.1-mini', 'openai/gpt-4o' and
   * 'llama3:8b' all contain characters that a YAML variable key may not.
   */
  public void setModels(String commaSeparated) {
    this.models = Arrays.stream(StringUtils.split(StringUtils.defaultString(commaSeparated), ','))
        .map(String::strip)
        .filter(StringUtils::isNotBlank)
        .distinct()
        .toList();
  }

  public boolean holds(String model) {
    return models.contains(model);
  }
}
