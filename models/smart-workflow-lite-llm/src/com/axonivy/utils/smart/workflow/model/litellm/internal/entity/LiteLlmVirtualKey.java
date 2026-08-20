package com.axonivy.utils.smart.workflow.model.litellm.internal.entity;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class LiteLlmVirtualKey {
  private final String alias;
  private String apiKey;
  private List<String> models = List.of();

  public LiteLlmVirtualKey(String alias) {
    this.alias = alias;
  }

  public String getAlias() {
    return alias;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public List<String> getModels() {
    return models;
  }

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
