package com.axonivy.utils.smart.workflow.model.litellm.internal.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.axonivy.utils.smart.workflow.model.litellm.internal.LiteLlmConf;
import com.axonivy.utils.smart.workflow.model.litellm.internal.entity.LiteLlmVirtualKey;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.vars.Variable;

public final class VariableUtils {

  interface LiteLlmExceptions {
    String IGNORED_VARIABLE = "Ignoring LiteLLM variable '%s'. Expected '%s.<keyAlias>[.<field>]';"
        + " key aliases must not contain dots.";
    String UNKNOWN_FIELD = "Unknown LiteLLM virtual key field in variable '%s'.";
    String NO_MODELS_DECLARED = "LiteLLM virtual keys %s declare no '%s'. No Agent can select them;"
        + " list the model aliases each key is allowed to call.";
  }

  private static final int FIELD_VAR_SEGMENTS = 6;
  private static final int NAME_VAR_SEGMENTS = 5;
  private static final int NAME_SEGMENT = 4;
  private static final int FIELD_SEGMENT = 5;

  private VariableUtils() {}

  public static List<LiteLlmVirtualKey> getVirtualKeys() {
    var keyVars = virtualKeysVars();
    if (keyVars.isEmpty()) {
      return List.of();
    }
    Map<String, LiteLlmVirtualKey> keys = new LinkedHashMap<>();
    for (Variable variable : keyVars) {
      String[] parts = variable.name().split("\\.");
      if (parts.length != NAME_VAR_SEGMENTS && parts.length != FIELD_VAR_SEGMENTS) {
        Ivy.log().warn(String.format(LiteLlmExceptions.IGNORED_VARIABLE, variable.name(),
            LiteLlmConf.VIRTUAL_KEYS));
        continue;
      }
      LiteLlmVirtualKey key = keys.computeIfAbsent(parts[NAME_SEGMENT], LiteLlmVirtualKey::new);
      if (parts.length == NAME_VAR_SEGMENTS) {
        continue;
      }
      switch (parts[FIELD_SEGMENT]) {
        case LiteLlmConf.API_KEY_FIELD -> key.setApiKey(variable.value());
        case LiteLlmConf.MODELS_FIELD -> key.setModels(variable.value());
        default -> Ivy.log().warn(String.format(LiteLlmExceptions.UNKNOWN_FIELD, variable.name()));
      }
    }

    var configured = keys.values().stream()
        .filter(key -> StringUtils.isNotBlank(key.getAlias()))
        .toList();
    warnAboutKeysWithoutModels(configured);
    return configured;
  }

  private static void warnAboutKeysWithoutModels(List<LiteLlmVirtualKey> keys) {
    var unusable = keys.stream()
        .filter(key -> key.getModels().isEmpty())
        .map(LiteLlmVirtualKey::getAlias)
        .toList();
    if (!unusable.isEmpty()) {
      Ivy.log().warn(String.format(LiteLlmExceptions.NO_MODELS_DECLARED, unusable,
          LiteLlmConf.MODELS_FIELD));
    }
  }

  public static List<Variable> virtualKeysVars() {
    String prefix = LiteLlmConf.VIRTUAL_KEYS + ".";
    return Ivy.var().all().stream()
        .filter(variable -> variable.name().startsWith(prefix))
        .toList();
  }

  public static List<String> modelNames() {
    return modelNames(getVirtualKeys());
  }

  public static List<String> modelNames(List<LiteLlmVirtualKey> keys) {
    return keys.stream()
        .map(LiteLlmVirtualKey::getModels)
        .flatMap(List::stream)
        .distinct()
        .toList();
  }
}
