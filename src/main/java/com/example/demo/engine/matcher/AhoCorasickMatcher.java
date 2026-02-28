package com.example.demo.engine.matcher;

import lombok.extern.slf4j.Slf4j;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Aho-Corasick 多模式匹配器
 * <p>
 * 性能热点组件 — 使用 ReadWriteLock 保证高并发读（日志匹配）与低频写（规则重建）的安全性。
 * <p>
 * 一次扫描即可匹配所有 CONTAINS 模式关键字，时间复杂度 O(n + m)。
 */
@Slf4j
public class AhoCorasickMatcher {

    private volatile Trie trie;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 根据关键字列表重建 AC 自动机（写操作，规则变更时调用）
     *
     * @param keywords 所有需要匹配的关键字
     */
    public void rebuild(List<String> keywords) {
        Lock writeLock = lock.writeLock();
        writeLock.lock();
        try {
            if (keywords == null || keywords.isEmpty()) {
                this.trie = null;
                log.info("[AC Matcher] 关键字列表为空，已清空自动机");
                return;
            }
            Trie.TrieBuilder builder = Trie.builder()
                    .ignoreCase()
                    .ignoreOverlaps();
            for (String keyword : keywords) {
                if (keyword != null && !keyword.isEmpty()) {
                    builder.addKeyword(keyword);
                }
            }
            this.trie = builder.build();
            log.info("[AC Matcher] AC 自动机重建完成，关键字数量: {}", keywords.size());
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 对单行日志执行多模式匹配（读操作，高并发安全）
     *
     * @param logLine 原始日志行
     * @return 命中的关键字列表（可能为空列表）
     */
    public List<String> match(String logLine) {
        Lock readLock = lock.readLock();
        readLock.lock();
        try {
            if (trie == null || logLine == null || logLine.isEmpty()) {
                return Collections.emptyList();
            }
            Collection<Emit> emits = trie.parseText(logLine);
            return emits.stream()
                    .map(Emit::getKeyword)
                    .distinct()
                    .collect(Collectors.toList());
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 返回第一个命中的关键字，或 null
     */
    public String matchFirst(String logLine) {
        List<String> results = match(logLine);
        return results.isEmpty() ? null : results.get(0);
    }
}
