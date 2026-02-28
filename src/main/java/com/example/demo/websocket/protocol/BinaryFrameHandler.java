package com.example.demo.websocket.protocol;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 二进制帧处理器 — 负责文件传输的分块接收与重组
 *
 * 帧格式: [transferId(36B)] [direction(1B)] [fileName(variable)] [chunkIdx(4B)]
 * [totalIdx(4B)] [isLastChunk(1B)] [body...]
 */
@Slf4j
@Component
public class BinaryFrameHandler {

    /** transferId → 已接收的数据块 */
    private final Map<String, byte[][]> transferBuffers = new ConcurrentHashMap<>();

    /**
     * 处理接收到的二进制帧
     */
    public void handleBinaryFrame(String agentId, BinaryMessage message) {
        ByteBuffer buffer = message.getPayload();

        if (buffer.remaining() < 46) {
            log.warn("[Binary] 帧太短，无法解析 header: agentId={}, size={}", agentId, buffer.remaining());
            return;
        }

        // 解析 transferId (36 bytes, UUID 字符串)
        byte[] transferIdBytes = new byte[36];
        buffer.get(transferIdBytes);
        String transferId = new String(transferIdBytes, StandardCharsets.UTF_8).trim();

        // 解析 direction (1 byte: 0=upload, 1=download)
        byte direction = buffer.get();

        // 解析 chunkIdx 和 totalIdx (各 4 bytes)
        int chunkIdx = buffer.getInt();
        int totalChunks = buffer.getInt();

        // 解析 isLastChunk (1 byte)
        boolean isLastChunk = buffer.get() == 1;

        // 剩余为数据体
        byte[] bodyData = new byte[buffer.remaining()];
        buffer.get(bodyData);

        log.info("[Binary] 收到分块: transferId={}, chunk={}/{}, size={}, last={}",
                transferId, chunkIdx, totalChunks, bodyData.length, isLastChunk);

        // 存储分块
        byte[][] chunks = transferBuffers.computeIfAbsent(transferId, k -> new byte[totalChunks][]);
        if (chunkIdx >= 0 && chunkIdx < totalChunks) {
            chunks[chunkIdx] = bodyData;
        }

        // 如果是最后一块，检查是否所有块都已到达
        if (isLastChunk) {
            boolean complete = true;
            int totalSize = 0;
            for (byte[] chunk : chunks) {
                if (chunk == null) {
                    complete = false;
                    break;
                }
                totalSize += chunk.length;
            }

            if (complete) {
                byte[] fileData = new byte[totalSize];
                int offset = 0;
                for (byte[] chunk : chunks) {
                    System.arraycopy(chunk, 0, fileData, offset, chunk.length);
                    offset += chunk.length;
                }
                transferBuffers.remove(transferId);
                log.info("[Binary] 文件接收完成: transferId={}, totalSize={}", transferId, totalSize);
                // TODO: 保存文件或交由业务逻辑处理
            }
        }
    }
}
