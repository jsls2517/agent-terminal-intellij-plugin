package com.zhangwenbing.claude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 应用级配置，存储 Claude Code Terminal 的设置
 */
@Service(Service.Level.APP)
@State(name = "ClaudeCodeSettings", storages = @Storage("claudeCodeSettings.xml"))
public final class ClaudeCodeSettings implements PersistentStateComponent<ClaudeCodeSettings.State> {

    public static final String DEFAULT_COMMAND = "claude";

    public static class State {
        public String command = DEFAULT_COMMAND;
    }

    private State state = new State();

    public static ClaudeCodeSettings getInstance() {
        return ApplicationManager.getApplication().getService(ClaudeCodeSettings.class);
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
