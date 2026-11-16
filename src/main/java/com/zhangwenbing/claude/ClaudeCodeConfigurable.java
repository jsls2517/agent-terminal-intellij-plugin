package com.zhangwenbing.claude;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Agent Terminal 设置页面
 * 位置：Settings -> Tools -> Agent Terminal
 */
public class ClaudeCodeConfigurable implements Configurable {

    private JTextField commandField;

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "Agent Terminal";
    }

    @Override
    public @Nullable JComponent createComponent() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Label
        JLabel label = new JLabel("启动命令:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(label, gbc);

        // 输入框
        commandField = new JTextField(30);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(commandField, gbc);

        // 提示文字
        JLabel hintLabel = new JLabel("默认: agy（也可输入 claude、opencode 等其他命令）");
        hintLabel.setForeground(UIManager.getColor("Component.info.foreground"));
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(hintLabel, gbc);

        // 添加填充空间使面板靠上对齐
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        // 加载当前设置
        reset();

        return panel;
    }

    @Override
    public boolean isModified() {
        String currentCommand = ClaudeCodeSettings.getInstance().getCommand();
        String fieldCommand = normalizeCommand(commandField.getText());
        return !currentCommand.equals(fieldCommand);
    }

    @Override
    public void apply() {
        String command = normalizeCommand(commandField.getText());
        ClaudeCodeSettings.getInstance().setCommand(command);
    }

    private String normalizeCommand(String text) {
        String trimmed = text.trim();
        return trimmed.isEmpty() ? ClaudeCodeSettings.DEFAULT_COMMAND : trimmed;
    }

    @Override
    public void reset() {
        commandField.setText(ClaudeCodeSettings.getInstance().getCommand());
    }
}
