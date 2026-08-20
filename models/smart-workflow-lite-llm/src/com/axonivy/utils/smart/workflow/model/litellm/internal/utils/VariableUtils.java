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

  // AI.Providers.LiteLLM.VirtualKeys.<alias>.<field>
  private static final int FIELD_VAR_SEGMENTS = 6;
  // AI.Providers.LiteLLM.VirtualKeys.<alias> - an entry that declares no field at all
  private static final int NAME_VAR_SEGMENTS = 5;
  private static final int NAME_SEGMENT = 4;
  private static final int FIELD_SEGMENT = 5;

  private VariableUtils() {}

  /**
   * Reads all configured virtual keys from the Ivy variables. Deliberately not cached:
   * variables are application configuration and may be edited at runtime in the Engine
   * Cockpit without a redeployment.
   */
  public static List<LiteLlmVirtualKey> getVirtualKeys() {
    var keyVars = virtualKeysVars();
    if (keyVars.isEmpty()) {
      return List.of();
    }
    Map<String, LiteLlmVirtualKey> keys = new LinkedHashMap<>();
    for (Variable variable : keyVars) {
      String[] parts = variable.name().split("\\.");
      if (parts.length != NAME_VAR_SEGMENTS && parts.length != FIELD_VAR_SEGMENTS) {
        Ivy.log().warn("Ignoring LiteLLM variable '" + variable.name() + "'. Expected '"
            + LiteLlmConf.VIRTUAL_KEYS + ".<keyAlias>[.<field>]'; key aliases must not contain dots.");
        continue;
      }
      // every field of an entry maps to the same key; an entry with no field at all
      // still yields one, so that it can be named in the 'declares no models' warning
      LiteLlmVirtualKey key = keys.computeIfAbsent(parts[NAME_SEGMENT], LiteLlmVirtualKey::new);
      if (parts.length == NAME_VAR_SEGMENTS) {
        continue;
      }
      switch (parts[FIELD_SEGMENT]) {
        case LiteLlmConf.API_KEY_FIELD -> key.setApiKey(variable.value());
        case LiteLlmConf.MODELS_FIELD -> key.setModels(variable.value());
        default -> Ivy.log().warn("Unknown LiteLLM virtual key field in variable '" + variable.name() + "'.");
      }
    }

    var configured = keys.values().stream()
        .filter(key -> StringUtils.isNotBlank(key.getAlias()))
        .toList();
    warnAboutKeysWithoutModels(configured);
    return configured;
  }

  /**
   * A key that lists no model cannot be selected by any Agent, since nothing routes to
   * it. Worth a log line precisely because the entry looks complete: the secret is there.
   */
  private static void warnAboutKeysWithoutModels(List<LiteLlmVirtualKey> keys) {
    var unusable = keys.stream()
        .filter(key -> key.getModels().isEmpty())
        .map(LiteLlmVirtualKey::getAlias)
        .toList();
    if (!unusable.isEmpty()) {
      Ivy.log().warn("LiteLLM virtual keys " + unusable + " declare no '" + LiteLlmConf.MODELS_FIELD
          + "'. No Agent can select them; list the model aliases each key is allowed to call.");
    }
  }

  public static List<Variable> virtualKeysVars() {
    String prefix = LiteLlmConf.VIRTUAL_KEYS + ".";
    return Ivy.var().all().stream()
        .filter(variable -> variable.name().startsWith(prefix))
        .toList();
  }

  /** Every model alias published by any virtual key, in declaration order. */
  public static List<String> modelNames() {
    return modelNames(getVirtualKeys());
  }

  /** Overload for callers that already read the keys, so the variables are walked once. */
  public static List<String> modelNames(List<LiteLlmVirtualKey> keys) {
    return keys.stream()
        .map(LiteLlmVirtualKey::getModels)
        .flatMap(List::stream)
        .distinct()
        .toList();
  }
}
