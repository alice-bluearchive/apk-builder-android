package com.example.androidstudio;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GitManager {

    private File projectDir;

    public GitManager(File projectDir) {
        this.projectDir = projectDir;
    }

    /** 初始化仓库 */
    public String init() throws Exception {
        File gitDir = new File(projectDir, ".git");
        if (gitDir.exists()) return "Repo already exists.";
        Git.init().setDirectory(projectDir).call().close();
        return "Initialized empty Git repository.";
    }

    /** 检查是否已初始化 */
    public boolean isRepo() {
        return new File(projectDir, ".git").exists();
    }

    /** 获取当前修改状态 */
    public String status() throws Exception {
        if (!isRepo()) return "Not a git repository.";
        try (Git git = Git.open(projectDir)) {
            Status status = git.status().call();
            StringBuilder sb = new StringBuilder();

            Set<String> added = status.getAdded();
            Set<String> modified = status.getModified();
            Set<String> missing = status.getMissing();
            Set<String> untracked = status.getUntracked();

            if (added.isEmpty() && modified.isEmpty() && missing.isEmpty() && untracked.isEmpty()) {
                return "Nothing to commit, working tree clean.";
            }

            if (!added.isEmpty()) sb.append("Added:\n  ").append(String.join("\n  ", added)).append("\n");
            if (!modified.isEmpty()) sb.append("Modified:\n  ").append(String.join("\n  ", modified)).append("\n");
            if (!missing.isEmpty()) sb.append("Deleted:\n  ").append(String.join("\n  ", missing)).append("\n");
            if (!untracked.isEmpty()) sb.append("Untracked:\n  ").append(String.join("\n  ", untracked)).append("\n");

            return sb.toString();
        }
    }

    /** 添加所有修改到暂存区 */
    public String addAll() throws Exception {
        if (!isRepo()) throw new Exception("Not a git repository.");
        try (Git git = Git.open(projectDir)) {
            git.add().addFilepattern(".").call();
            // 处理删除的文件
            git.add().addFilepattern(".").setUpdate(true).call();
            return "All changes added to staging area.";
        }
    }

    /** 提交更改 */
    public String commit(String message, String authorName, String authorEmail) throws Exception {
        if (!isRepo()) throw new Exception("Not a git repository.");
        try (Git git = Git.open(projectDir)) {
            PersonIdent author = new PersonIdent(authorName, authorEmail);
            RevCommit commit = git.commit()
                    .setMessage(message)
                    .setAuthor(author)
                    .setCommitter(author)
                    .call();
            return "Committed: " + commit.getId().getName().substring(0, 7) + " - " + message;
        }
    }

    /** 获取提交日志 */
    public List<String> getLog() throws Exception {
        List<String> logs = new ArrayList<>();
        if (!isRepo()) return logs;
        try (Git git = Git.open(projectDir)) {
            Iterable<RevCommit> commits = git.log().setMaxCount(20).call();
            for (RevCommit commit : commits) {
                String time = new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
                        .format(new java.util.Date(commit.getCommitTime() * 1000L));
                logs.add("[" + time + "] " + commit.getShortMessage());
            }
        }
        return logs;
    }
}