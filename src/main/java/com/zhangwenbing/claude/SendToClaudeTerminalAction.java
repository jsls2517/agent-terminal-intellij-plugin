package com.zhangwenbing.claude;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import com.intellij.terminal.ui.TerminalWidget;

import java.util.Objects;

public class SendToClaudeTerminalAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (editor == null || file == null) {
            ClaudeCodeUtils.notify(project, "无法获取文件信息", NotificationType.ERROR);
            return;
        }

        // 获取行范围
        int startLine = Objects.requireNonNull(editor.getSelectionModel().getSelectionStartPosition()).line + 1;
        int endLine = Objects.requireNonNull(editor.getSelectionModel().getSelectionEndPosition()).line + 1;

        // 如果没有选中文本，使用当前行
        if (!editor.getSelectionModel().hasSelection()) {
            startLine = editor.getCaretModel().getLogicalPosition().line + 1;
            endLine = startLine;
        }

        // 构建文件引用格式：filePath:L1-L2
        String filePath = file.getCanonicalPath();
        String fileRef = startLine == endLine ? filePath : filePath + ":L" + startLine + "-" + endLine;

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
                        // 发送文件引用到终端
                        ClaudeCodeUtils.exec(project, widget, fileRef);
                    }
                }
            } catch (Throwable ex) {
                ClaudeCodeUtils.notify(project, "发送失败：" + ex.getMessage(), NotificationType.ERROR);
            }
        });
    }
}
