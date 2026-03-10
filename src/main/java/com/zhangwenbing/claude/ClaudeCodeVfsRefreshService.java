package com.zhangwenbing.claude;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * VFS 刷新服务
 * 负责维护待刷新文件队列、防抖调度和执行实际的 VFS 刷新操作
 * 注册为 applicationService
 */
public class ClaudeCodeVfsRefreshService implements Disposable {

    private static final Logger LOG = Logger.getInstance(ClaudeCodeVfsRefreshService.class);

    /**
     * 防抖延迟（毫秒）
     * 在此窗口期内的事件将被聚合为一次刷新
     */
    private static final int DEBOUNCE_DELAY_MS = 300;

    /**
     * 批量阈值：超过此数量的文件变更将触发全目录刷新
     */
    private static final int BATCH_THRESHOLD = 10;

    /**
     * 待处理文件上限：超过此数量强制触发全刷新，防止内存压力
     */
    private static final int MAX_PENDING_FILES = 1000;

    private final Alarm alarm;
    private final Set<String> pendingFiles = ConcurrentHashMap.newKeySet();
    private volatile boolean isDisposed = false;
    private volatile Future<?> currentRefreshTask;

    public ClaudeCodeVfsRefreshService() {
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    }

    /**
     * 获取全局实例
     */
    public static ClaudeCodeVfsRefreshService getInstance() {
        return ApplicationManager.getApplication().getService(ClaudeCodeVfsRefreshService.class);
    }

    /**
     * 状态机枚举
     */
    private enum State {
        IDLE,           // 空闲状态
        COLLECTING,     // 收集变更中
        REFRESHING,     // 刷新执行中
        DISPOSED        // 已释放
    }

    private volatile State currentState = State.IDLE;

    /**
     * 添加待刷新文件
     * 由 Listener 调用
     *
     * @param filePath 文件路径
     * @return 如果达到批量阈值返回 true，表示需要立即全刷新
     */
    public boolean addPendingFile(@NotNull String filePath) {
        if (isDisposed || currentState == State.DISPOSED) {
            return false;
        }

        // 输入验证
        if (filePath.trim().isEmpty()) {
            LOG.warn("收到无效的文件路径: 空字符串");
            return false;
        }

        // 内存压力保护：超过上限强制触发全刷新
        if (pendingFiles.size() >= MAX_PENDING_FILES) {
            LOG.warn("待刷新文件数量超过上限(" + MAX_PENDING_FILES + ")，触发全刷新");
            return true;
        }

        pendingFiles.add(filePath);
        LOG.debug("添加待刷新文件: " + filePath);

        // 如果达到批量阈值，立即执行全刷新
        if (pendingFiles.size() >= BATCH_THRESHOLD) {
            LOG.debug("达到批量阈值，触发全刷新");
            return true;
        }

        // 启动防抖定时器
        scheduleRefresh();
        return false;
    }

    /**
     * 添加多个待刷新文件
     * 由 Listener 调用
     *
     * @param filePaths 文件路径列表
     * @return 如果达到批量阈值返回 true，表示需要立即全刷新
     */
    public boolean addPendingFiles(@NotNull List<String> filePaths) {
        if (isDisposed || currentState == State.DISPOSED) {
            return false;
        }

        // 过滤无效路径
        List<String> validPaths = filePaths.stream()
                .filter(path -> path != null && !path.trim().isEmpty())
                .collect(Collectors.toList());

        if (validPaths.isEmpty()) {
            return false;
        }

        // 内存压力保护：超过上限强制触发全刷新
        if (pendingFiles.size() + validPaths.size() > MAX_PENDING_FILES) {
            LOG.warn("待刷新文件数量将超过上限，触发全刷新");
            return true;
        }

        pendingFiles.addAll(validPaths);
        LOG.debug("批量添加待刷新文件，当前数量: " + pendingFiles.size());

        // 如果达到批量阈值，立即执行全刷新
        if (pendingFiles.size() >= BATCH_THRESHOLD) {
            LOG.debug("达到批量阈值，触发全刷新");
            return true;
        }

        // 启动防抖定时器
        scheduleRefresh();
        return false;
    }

    /**
     * 安排延迟刷新（防抖）
     */
    private void scheduleRefresh() {
        if (currentState == State.DISPOSED) {
            return;
        }

        currentState = State.COLLECTING;
        alarm.cancelAllRequests();
        alarm.addRequest(this::executeRefresh, DEBOUNCE_DELAY_MS);
    }

    /**
     * 执行实际的刷新操作
     */
    private void executeRefresh() {
        if (isDisposed || pendingFiles.isEmpty() || currentState == State.DISPOSED) {
            return;
        }

        currentState = State.REFRESHING;

        // 原子地获取并清空待处理文件（修复竞态条件）
        final Set<String> filesToRefresh;
        synchronized (pendingFiles) {
            if (pendingFiles.isEmpty()) {
                currentState = State.IDLE;
                return;
            }
            filesToRefresh = new HashSet<>(pendingFiles);
            pendingFiles.clear();
        }

        // 在后台线程中执行
        currentRefreshTask = AppExecutorUtil.getAppExecutorService().submit(() -> {
            try {
                refreshFiles(filesToRefresh);
            } catch (Exception e) {
                LOG.warn("文件刷新失败", e);
            } finally {
                currentRefreshTask = null;
                if (!isDisposed && currentState != State.DISPOSED) {
                    currentState = State.IDLE;
                }
            }
        });
    }

    /**
     * 刷新指定文件集合
     */
    private void refreshFiles(@NotNull Set<String> filePaths) {
        if (isDisposed) {
            return;
        }

        // 获取所有打开的项目
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        if (openProjects.length == 0) {
            return;
        }

        for (Project project : openProjects) {
            if (project.isDisposed() || isDisposed) {
                continue;
            }

            // 筛选属于当前项目的文件
            Set<VirtualFile> projectFiles = filePaths.stream()
                    .map(path -> findVirtualFile(project, path))
                    .filter(vFile -> vFile != null && isUnderProject(project, vFile))
                    .collect(Collectors.toSet());

            if (projectFiles.isEmpty()) {
                continue;
            }

            // 异步刷新 VFS（必须在 EDT 执行 UI 操作）
            ApplicationManager.getApplication().invokeLater(() -> {
                if (project.isDisposed() || isDisposed) {
                    return;
                }

                for (VirtualFile vFile : projectFiles) {
                    refreshVirtualFile(vFile);
                }
            });
        }
    }

    /**
     * 查找虚拟文件
     * 必须在 ReadAction 中执行 VFS 操作
     */
    private @Nullable VirtualFile findVirtualFile(@NotNull Project project, @NotNull String path) {
        return ReadAction.compute(() -> {
            try {
                VirtualFileManager vfm = VirtualFileManager.getInstance();
                VirtualFile vFile = vfm.findFileByUrl("file://" + path);
                if (vFile == null) {
                    // 尝试从项目根目录解析相对路径
                    VirtualFile baseDir = project.getBaseDir();
                    if (baseDir != null) {
                        vFile = baseDir.findFileByRelativePath(path);
                    }
                }
                return vFile;
            } catch (Exception e) {
                LOG.warn("查找虚拟文件失败: " + path, e);
                return null;
            }
        });
    }

    /**
     * 判断文件是否属于项目
     * 必须在 ReadAction 中执行 VFS 操作
     */
    private boolean isUnderProject(@NotNull Project project, @NotNull VirtualFile file) {
        return ReadAction.compute(() -> {
            try {
                VirtualFile baseDir = project.getBaseDir();
                if (baseDir == null) {
                    return false;
                }
                String basePath = baseDir.getPath();
                return file.getPath().startsWith(basePath);
            } catch (Exception e) {
                LOG.warn("判断项目边界失败: " + file.getPath(), e);
                return false;
            }
        });
    }

    /**
     * 刷新单个虚拟文件
     * 包括 VFS 刷新和编辑器刷新
     */
    private void refreshVirtualFile(@NotNull VirtualFile vFile) {
        try {
            // 1. 刷新 VFS 缓存
            vFile.refresh(false, false);

            // 2. 如果文件在编辑器中打开，检查是否需要重新加载
            Document document = FileDocumentManager.getInstance().getDocument(vFile);
            if (document != null) {
                FileDocumentManager.getInstance().reloadFromDisk(document);
            }

            LOG.debug("文件已刷新: " + vFile.getPath());
        } catch (Exception e) {
            LOG.warn("刷新文件失败: " + vFile.getPath(), e);
        }
    }

    /**
     * 刷新所有项目的 VFS
     * 用于批量变更场景（如 git checkout）
     */
    public void refreshAllProjects() {
        if (isDisposed || currentState == State.DISPOSED) {
            return;
        }

        // 取消待执行的刷新请求
        alarm.cancelAllRequests();

        // 原子地获取并清空待处理文件
        synchronized (pendingFiles) {
            if (!pendingFiles.isEmpty()) {
                LOG.debug("全刷新时清除了 " + pendingFiles.size() + " 个待刷新文件");
                pendingFiles.clear();
            }
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            if (!isDisposed && currentState != State.DISPOSED) {
                VirtualFileManager.getInstance().asyncRefresh(null);
                LOG.debug("执行全刷新");
            }
        });
    }

    @Override
    public void dispose() {
        isDisposed = true;
        currentState = State.DISPOSED;
        alarm.cancelAllRequests();

        // 取消正在执行的后台任务
        Future<?> task = currentRefreshTask;
        if (task != null) {
            task.cancel(false);
        }

        // 原子地清空待处理文件
        synchronized (pendingFiles) {
            pendingFiles.clear();
        }

        LOG.info("ClaudeCodeVfsRefreshService 已释放");
    }
}
