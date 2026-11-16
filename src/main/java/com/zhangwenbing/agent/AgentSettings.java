package com.zhangwenbing.agent;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 应用级配置，存储 Agent Terminal 的设置
 */
@Service(Service.Level.APP)
@State(name = "AgentSettings", storages = @Storage("agentSettings.xml"))
public final class AgentSettings implements PersistentStateComponent<AgentSettings.State> {

    public static final String DEFAULT_COMMAND = "agy";

    public static class State {
        public String command = DEFAULT_COMMAND;
    }

    private State state = new State();

    public static AgentSettings getInstance() {
        return ApplicationManager.getApplication().getService(AgentSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    @NotNull
    public String getCommand() {
        String cmd = state.command;
        return (cmd == null || cmd.trim().isEmpty()) ? DEFAULT_COMMAND : cmd.trim();
    }

    public void setCommand(@NotNull String command) {
        state.command = command.trim();
    }
}
