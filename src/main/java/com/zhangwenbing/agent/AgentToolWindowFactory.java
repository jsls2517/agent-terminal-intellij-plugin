package com.zhangwenbing.agent;

import com.intellij.ide.IdeEventQueue;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.ui.TerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.LocalTerminalDirectRunner;
import org.jetbrains.plugins.terminal.ShellStartupOptions;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class AgentToolWindowFactory implements ToolWindowFactory {
    public static final String TOOL_WINDOW_ID = "Agent";
    public static final com.intellij.openapi.util.Key<TerminalWidget> WIDGET_KEY =
            com.intellij.openapi.util.Key.create("AGENT_WIDGET");

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 配置齿轮/设置菜单项（Gear Actions）
        DefaultActionGroup gearGroup = new DefaultActionGroup();
        gearGroup.add(new RestartAgentTerminalAction());
        toolWindow.setAdditionalGearActions(gearGroup);

        // 注册全局键盘监听器（仅一次，生命周期绑定到 toolWindow）
        setupKeyDispatcher(project, toolWindow);

        // 初始化终端界面
        initTerminalContent(project, toolWindow);
    }

    /**
     * 重启 Agent 终端
     */
    public static void restartTerminal(@NotNull Project project) {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
        if (toolWindow == null) {
            AgentUtils.notify(project, "未找到 Agent 工具窗", NotificationType.ERROR);
            return;
        }
        restartTerminal(project, toolWindow);
    }

    /**
     * 重启 Agent 终端并刷新内容
     */
    public static void restartTerminal(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                initTerminalContent(project, toolWindow);
                AgentUtils.notify(project, "终端已重启", NotificationType.INFORMATION);
            } catch (Exception ex) {
                AgentUtils.notify(project, "重启失败：" + ex.getMessage(), NotificationType.ERROR);
            }
        });
    }

    /**
     * 初始化或重新创建终端内容
     */
    public static void initTerminalContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 移除并释放旧的 content 及其持有的资源
        toolWindow.getContentManager().removeAllContents(true);

        String workDir = project.getBasePath() != null ? project.getBasePath() : System.getProperty("user.home");

        // 使用 LocalTerminalDirectRunner 创建独立终端，不通过 TerminalView
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
        toolWindow.getContentManager().setSelectedContent(content);

        ApplicationManager.getApplication().invokeLater(() -> autorun(project, widget));
    }

    /**
     * 注册键盘分发器：
     * - ESC 转发给终端（Agent CLI 使用 ESC 中断）
     * - Shift+Enter 插入换行符
     */
    private void setupKeyDispatcher(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        IdeEventQueue.getInstance().addDispatcher(e -> {
            if (e instanceof KeyEvent ke) {
                if (ke.getID() == KeyEvent.KEY_PRESSED) {
                    ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
                    if (tw != null && tw.isActive()) {
                        Content selectedContent = tw.getContentManager().getSelectedContent();
                        if (selectedContent == null && tw.getContentManager().getContentCount() > 0) {
                            selectedContent = tw.getContentManager().getContent(0);
                        }
                        if (selectedContent != null) {
                            TerminalWidget currentWidget = selectedContent.getUserData(WIDGET_KEY);
                            if (currentWidget != null && currentWidget.getTtyConnector() != null) {
                                // ESC: 发送中断字符 (0x1B) 到终端
                                if (ke.getKeyCode() == KeyEvent.VK_ESCAPE) {
                                    try {
                                        currentWidget.getTtyConnector().write(new byte[]{0x1B});
                                    } catch (Exception ex) {
                                        AgentUtils.notify(project, "发送失败：" + ex.getMessage(), NotificationType.ERROR);
                                    }
                                    return true; // 消费该事件
                                }
                                // Shift+Enter: 插入换行符而不发送命令
                                if (ke.getKeyCode() == KeyEvent.VK_ENTER && (ke.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                                    try {
                                        currentWidget.getTtyConnector().write(new byte[]{'\n'});
                                    } catch (Exception ex) {
                                        AgentUtils.notify(project, "发送失败：" + ex.getMessage(), NotificationType.ERROR);
                                    }
                                    return true; // 消费该事件
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }, toolWindow.getDisposable());
    }

    private static void autorun(Project project, TerminalWidget widget) {
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
