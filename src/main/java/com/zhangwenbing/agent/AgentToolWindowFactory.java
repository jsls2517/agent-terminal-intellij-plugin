package com.zhangwenbing.agent;

import com.intellij.ide.IdeEventQueue;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.ShellStartupOptions;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Objects;

public class AgentToolWindowFactory implements ToolWindowFactory {
    public static final String TOOL_WINDOW_ID = "Agent";
    public static final com.intellij.openapi.util.Key<TerminalWidget> WIDGET_KEY =
            com.intellij.openapi.util.Key.create("AGENT_WIDGET");
    private static final com.intellij.openapi.util.Key<Boolean> AUTORUN_DONE_KEY =
            com.intellij.openapi.util.Key.create("AGENT_AUTORUN_DONE");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        String workDir = project.getBasePath() != null ? project.getBasePath() : System.getProperty("user.home");

        // 使用 LocalTerminalDirectRunner 创建独立终端，不通过 TerminalView
        // 这样不会在底部 Terminal 工具窗显示
        LocalTerminalDirectRunner runner = LocalTerminalDirectRunner.createTerminalRunner(project);

        // 配置启动选项
        ShellStartupOptions.Builder optionsBuilder = new ShellStartupOptions.Builder();
        optionsBuilder.setWorkingDirectory(workDir);

        // 创建独立的终端 widget
        TerminalWidget widget = runner.startShellTerminalWidget(toolWindow.getDisposable(), optionsBuilder.build(), true);

        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(widget.getComponent(), java.awt.BorderLayout.CENTER);

        Content content = ContentFactory.getInstance().createContent(panel, "", false);
        content.putUserData(WIDGET_KEY, widget);
        toolWindow.getContentManager().addContent(content);

        // Register key handlers:
        // - ESC to forward to terminal (Agent CLI uses ESC to interrupt)
        // - Shift+Enter to insert newline (without sending command)
        // JetBrains intercepts ESC by default to switch focus to editor, so we need to catch it first
        IdeEventQueue.getInstance().addDispatcher(e -> {
            if (e instanceof KeyEvent ke) {
                if (ke.getID() == KeyEvent.KEY_PRESSED) {
                    ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
                    if (tw != null && tw.isActive()) {
                        // ESC: send interrupt character (0x1B) to terminal
                        if (ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
                            try {
                                Objects.requireNonNull(widget.getTtyConnector()).write(new byte[]{0x1B});
                            } catch (Exception ex) {
                                AgentUtils.notify(project, "发送失败：" + ex.getMessage(), NotificationType.ERROR);
                            }
                            return true; // Consume the event
                        }
                        // Shift+Enter: insert newline without sending command
                        if (ke.getKeyCode() == KeyEvent.VK_ENTER && (ke.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                            try {
                                Objects.requireNonNull(widget.getTtyConnector()).write(new byte[]{'\n'});
                            } catch (Exception ex) {
                                AgentUtils.notify(project, "发送失败：" + ex.getMessage(), NotificationType.ERROR);
                            }
                            return true; // Consume the event
                        }
                    }
                }
            }
            return false;
        }, toolWindow.getDisposable());

        if (Boolean.TRUE.equals(content.getUserData(AUTORUN_DONE_KEY))) return;
        content.putUserData(AUTORUN_DONE_KEY, true);

        ApplicationManager.getApplication().invokeLater(() -> autorun(project, widget, workDir));
    }

    private void autorun(Project project, TerminalWidget widget, String ignoredWorkDir) {
        Alarm alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, widget);

        alarm.addRequest(new Runnable() {
            @Override
            public void run() {
                if (widget.getTtyConnector() != null && widget.getTtyConnector().isConnected()) {
                    String command = AgentSettings.getInstance().getCommand();
                    ApplicationManager.getApplication().invokeLater(() ->
                            AgentUtils.exec(project, widget, command)
                    );
                    return; // 成功，停止轮询
                }
                // 还没 ready，继续轮询
                alarm.addRequest(this, 100);
            }
        }, 0);
    }

}
