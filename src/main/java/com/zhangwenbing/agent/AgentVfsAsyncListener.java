package com.zhangwenbing.agent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * VFS 异步文件监听器
 * 实现 AsyncFileListener 接口，接收 VFS 事件并委托给 Service 处理
 * 注册为 vfs.asyncListener
 */
public class AgentVfsAsyncListener implements AsyncFileListener {

    private static final Logger LOG = Logger.getInstance(AgentVfsAsyncListener.class);

    public AgentVfsAsyncListener() {
        LOG.debug("AgentVfsAsyncListener 已初始化");
    }

    @Override
    public @Nullable ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
        // 过滤出我们关心的文件变更事件
        List<VFileEvent> relevantEvents = events.stream()
                .filter(this::isRelevantEvent)
                .collect(Collectors.toList());

        if (relevantEvents.isEmpty()) {
            return null;
        }

        LOG.debug("接收到 " + relevantEvents.size() + " 个相关 VFS 事件");

        // 收集变更的文件路径
        List<String> filePaths = relevantEvents.stream()
                .map(this::getEventPath)
                .filter(path -> path != null)
                .collect(Collectors.toList());

        if (filePaths.isEmpty()) {
            return null;
        }

        // 委托给 Service 处理
        AgentVfsRefreshService service = AgentVfsRefreshService.getInstance();
        if (service == null) {
            LOG.warn("AgentVfsRefreshService 未初始化");
            return null;
        }

        // 添加文件到待刷新队列
        boolean shouldRefreshAll = service.addPendingFiles(filePaths);

        if (shouldRefreshAll) {
            // 达到批量阈值，返回 ChangeApplier 在 afterVfsChange 中执行全刷新
            return new ChangeApplier() {
                @Override
                public void afterVfsChange() {
                    service.refreshAllProjects();
                }
            };
        }

        // 返回 ChangeApplier，但实际刷新由 Service 的防抖定时器触发
        return new ChangeApplier() {
            @Override
            public void afterVfsChange() {
                // 变更已在 prepareChange 中记录到 Service
                // 实际刷新由 Service 的 Alarm 定时器触发
                LOG.debug("VFS 变更已记录，等待防抖刷新");
            }
        };
    }

    /**
     * 判断事件是否值得处理
     * 只处理创建、修改、删除事件
     */
    private boolean isRelevantEvent(@NotNull VFileEvent event) {
        return event instanceof VFileCreateEvent
                || event instanceof VFileContentChangeEvent
                || event instanceof VFileDeleteEvent;
    }

    /**
     * 从事件中提取文件路径
     */
    private @Nullable String getEventPath(@NotNull VFileEvent event) {
        try {
            VirtualFile file = event.getFile();
            if (file != null) {
                return file.getPath();
            }
            return event.getPath();
        } catch (Exception e) {
            LOG.warn("获取事件路径失败", e);
            return null;
        }
    }
}
