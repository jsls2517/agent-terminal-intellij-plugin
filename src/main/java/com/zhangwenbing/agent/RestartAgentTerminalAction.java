package com.zhangwenbing.agent;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * 重启终端操作
 */
public class RestartAgentTerminalAction extends AnAction implements DumbAware {

    public RestartAgentTerminalAction() {
        super("重启终端", "重新启动终端会话并运行启动命令", null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        ToolWindow toolWindow = ToolWindowManager.getInstance(project)
                .getToolWindow(AgentToolWindowFactory.TOOL_WINDOW_ID);
        e.getPresentation().setEnabledAndVisible(toolWindow != null && toolWindow.isAvailable());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        AgentToolWindowFactory.restartTerminal(project);
    }
}
