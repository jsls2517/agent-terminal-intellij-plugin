package com.zhangwenbing.claude;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NotNull;

/**
 * 将终端选中的文本发送到 Claude Code Terminal
 */
public class SendConsoleSelectionToClaudeAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean condition = editor != null && editor.getSelectionModel().hasSelection();
        e.getPresentation().setEnabledAndVisible(condition);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) return;

        String selectedText = editor.getSelectionModel().getSelectedText();
        if (selectedText == null || selectedText.trim().isEmpty()) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                ToolWindow tw = ToolWindowManager.getInstance(project)
                        .getToolWindow(ClaudeCodeToolWindowFactory.TOOL_WINDOW_ID);
                if (tw == null) {
                    ClaudeCodeUtils.notify(project, "未找到 Claude Code 工具窗", NotificationType.ERROR);
                    return;
                }

                // 激活工具窗
                tw.activate(null, true);

                var cm = tw.getContentManager();

                // 如果没有内容，创建一次
                if (cm.getContentCount() == 0) {
                    new ClaudeCodeToolWindowFactory().createToolWindowContent(project, tw);
                }

                // 获取 terminal widget 并发送命令
                Content content = cm.getContent(0);
                if (content != null) {
                    TerminalWidget widget = content.getUserData(ClaudeCodeToolWindowFactory.WIDGET_KEY);
                    if (widget != null) {
                        // 发送选中的文本到终端
                        ClaudeCodeUtils.exec(project, widget, selectedText.trim());
                    }
                }
            } catch (Throwable ex) {
                ClaudeCodeUtils.notify(project, "发送失败：" + ex.getMessage(), NotificationType.ERROR);
            }
        });
    }

}
