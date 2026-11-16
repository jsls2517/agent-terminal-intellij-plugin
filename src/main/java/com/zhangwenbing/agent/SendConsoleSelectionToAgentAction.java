package com.zhangwenbing.agent;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.terminal.JBTerminalWidget;
import com.intellij.ui.content.Content;
import com.intellij.terminal.ui.TerminalWidget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 将终端或控制台选中的文本发送到 Agent Terminal
 */
public class SendConsoleSelectionToAgentAction extends AnAction {

    private static final DataKey<Editor> TERMINAL_EDITOR_KEY = DataKey.create("TERMINAL_EDITOR");

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Editor editor = getEditor(e);
        boolean hasEditorSelection = editor != null && editor.getSelectionModel().hasSelection();
        boolean hasWidgetSelection = false;
        if (!hasEditorSelection) {
            String text = e.getData(JBTerminalWidget.SELECTED_TEXT_DATA_KEY);
            hasWidgetSelection = text != null && !text.trim().isEmpty();
        }
        e.getPresentation().setEnabledAndVisible(hasEditorSelection || hasWidgetSelection);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        String selectedText = getSelectedText(e);
        if (selectedText == null || selectedText.trim().isEmpty()) {
            return;
        }

        final String textToSend = selectedText.trim();

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                ToolWindow tw = ToolWindowManager.getInstance(project)
                        .getToolWindow(AgentToolWindowFactory.TOOL_WINDOW_ID);
                if (tw == null) {
                    AgentUtils.notify(project, "未找到 Agent 工具窗", NotificationType.ERROR);
                    return;
                }

                // 激活工具窗
                tw.activate(null, true);

                var cm = tw.getContentManager();

                // 如果没有内容，创建一次
                if (cm.getContentCount() == 0) {
                    new AgentToolWindowFactory().createToolWindowContent(project, tw);
                }

                // 获取 terminal widget 并发送命令
                Content content = cm.getContent(0);
                if (content != null) {
                    TerminalWidget widget = content.getUserData(AgentToolWindowFactory.WIDGET_KEY);
                    if (widget != null) {
                        // 发送选中的文本到终端
                        AgentUtils.exec(project, widget, textToSend);
                    }
                }
            } catch (Throwable ex) {
                AgentUtils.notify(project, "发送失败：" + ex.getMessage(), NotificationType.ERROR);
            }
        });
    }

    @Nullable
    private Editor getEditor(@NotNull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            editor = e.getData(TERMINAL_EDITOR_KEY);
        }
        return editor;
    }

    @Nullable
    private String getSelectedText(@NotNull AnActionEvent e) {
        Editor editor = getEditor(e);
        if (editor != null && editor.getSelectionModel().hasSelection()) {
            return editor.getSelectionModel().getSelectedText();
        }
        return e.getData(JBTerminalWidget.SELECTED_TEXT_DATA_KEY);
    }

}
