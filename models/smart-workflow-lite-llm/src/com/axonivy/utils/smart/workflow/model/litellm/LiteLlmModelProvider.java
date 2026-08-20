package com.axonivy.utils.smart.workflow.model.litellm;

import java.util.List;

import com.axonivy.utils.smart.workflow.model.litellm.internal.LiteLlmConf;
import com.axonivy.utils.smart.workflow.model.litellm.internal.LiteLlmServiceConnector;
import com.axonivy.utils.smart.workflow.model.litellm.internal.utils.VariableUtils;
import com.axonivy.utils.smart.workflow.model.spi.ChatModelProvider;

import ch.ivyteam.ivy.vars.Variable;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;

public class LiteLlmModelProvider implements ChatModelProvider {

  public static final String NAME = "LiteLLM";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public ChatModel setup(ModelOptions options) {
    var builder = LiteLlmServiceConnector.buildOpenAiModel(options.modelName());
    if (options.structuredOutput()) {
      builder.supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
      builder.strictJsonSchema(true);
      builder.responseFormat("json_schema");
    }
    builder.listeners(options.listeners());
    return builder.build();
  }

  @Override
  public List<String> models() {
    return VariableUtils.modelNames();
  }

  @Override
  public List<String> secretsVars() {
    return VariableUtils.virtualKeysVars().stream()
        .map(Variable::name)
        .filter(name -> name.endsWith("." + LiteLlmConf.API_KEY_FIELD))
        .distinct()
        .toList();
  }
}
