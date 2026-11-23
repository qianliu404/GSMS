package com.gsms.module.doc.service;

import com.gsms.common.BizException;
import com.gsms.common.ErrorCode;
import com.gsms.config.GsmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘文件存储。
 * 目录结构：{root}/{yyyy}/{MM}/{uuid}.{ext}，数据库存相对路径 /files/{yyyy}/{MM}/{name}。
 * 文件名一律使用 UUID，不信任原始文件名，避免路径穿越与中文乱码。
 */
@Slf4j
@Service
public class StorageService {

    private static final String URL_PREFIX = "/files/";

    private final Path root;
    private final Path temp;
    private final GsmsProperties properties;

    public StorageService(GsmsProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.getStorage().getRoot()).toAbsolutePath().normalize();
        this.temp = Paths.get(properties.getStorage().getTemp()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            Files.createDirectories(temp);
        } catch (IOException e) {
            throw new IllegalStateException("初始化存储目录失败: " + root, e);
        }
    }

    public static class StoredFile {
        public String relativePath;
        public String originalName;
        public long size;
        public String sha256;
        public String extension;
    }

    public StoredFile store(MultipartFile file, String bizName) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "上传文件为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = extensionOf(original);
        if (!properties.getDoc().getAllowedExtensions().contains(ext)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED,
                    "不支持的文件类型: " + (ext.isEmpty() ? "未知" : ext));
        }
        long maxBytes = properties.getDoc().getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.FILE_TOO_LARGE, "文件超出限制 " + properties.getDoc().getMaxFileSizeMb() + "MB");
        }

        LocalDate today = LocalDate.now();
        String year = DateTimeFormatter.ofPattern("yyyy").format(today);
        String month = DateTimeFormatter.ofPattern("MM").format(today);
        String fileName = UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);

        Path dir = root.resolve(year).resolve(month);
        Path target = dir.resolve(fileName).normalize();
        // 双保险：规范化后必须仍在 root 内
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "非法的存储路径");
        }
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("文件写入失败: {}", target, e);
            throw new BizException(ErrorCode.STORAGE_ERROR, "文件保存失败");
        }

        StoredFile stored = new StoredFile();
        stored.relativePath = URL_PREFIX + year + "/" + month + "/" + fileName;
        stored.originalName = original;
        stored.size = file.getSize();
        stored.sha256 = sha256(target);
        stored.extension = ext;
        return stored;
    }

    /** 相对路径 -> 物理路径，拒绝越权访问 */
    public Path resolve(String relativePath) {
        if (relativePath == null || !relativePath.startsWith(URL_PREFIX)) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "非法的文件路径");
        }
        String sub = relativePath.substring(URL_PREFIX.length());
        Path target = root.resolve(sub).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.STORAGE_ERROR, "非法的文件路径");
        }
        if (!Files.exists(target)) {
            throw new BizException(ErrorCode.NOT_FOUND, "文件不存在或已被清理");
        }
        return target;
    }

    public void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (Exception e) {
            log.warn("删除文件失败(忽略): {}", relativePath);
        }
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算文件哈希失败", e);
            return null;
        }
    }
}
