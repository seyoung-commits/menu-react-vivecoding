package com.ohgiraffers.springdatajpa.service;

import com.ohgiraffers.springdatajpa.exception.InvalidMenuImageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class MenuImageStorage {
    private static final Logger log = LoggerFactory.getLogger(MenuImageStorage.class);
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 20_000_000;
    private final Path directory;

    public MenuImageStorage(@Value("${app.upload-dir:./uploads/menu-images}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    /* 파일명/확장자를 믿지 않고 실제 이미지 형식과 크기를 검사한 뒤 다시 인코딩한다. */
    public String store(MultipartFile file) {
        if (file.isEmpty()) throw new InvalidMenuImageException("빈 파일은 올릴 수 없어요.");
        if (file.getSize() > MAX_BYTES) throw new InvalidMenuImageException("사진은 5MB 이하로 골라 주세요.");
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("사진 저장은 메뉴 저장 트랜잭션 안에서 실행해야 합니다.");
        }
        BufferedImage image;
        String format;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            if (input == null) throw new InvalidMenuImageException("사진을 읽을 수 없어요.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidMenuImageException("JPG 또는 PNG 사진을 골라 주세요.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw new InvalidMenuImageException("JPG 또는 PNG 사진만 올릴 수 있어요.");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new InvalidMenuImageException("사진 해상도는 2천만 화소 이하로 줄여 주세요.");
                }
                image = reader.read(0);
                if (image == null) throw new InvalidMenuImageException("손상된 사진은 올릴 수 없어요.");
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new InvalidMenuImageException("사진을 읽을 수 없어요. 다른 JPG 또는 PNG 파일을 골라 주세요.");
        }

        String filename = UUID.randomUUID() + (format.equals("jpeg") ? ".jpg" : ".png");
        Path destination = directory.resolve(filename);
        try {
            Files.createDirectories(directory);
            if (!ImageIO.write(image, format, destination.toFile())) {
                throw new IOException("이미지 인코더를 찾을 수 없습니다.");
            }
        } catch (IOException ex) {
            deleteQuietly(filename);
            throw new UncheckedIOException("사진을 저장하지 못했어요.", ex);
        }

        // DB 저장이 실패하면 새 파일도 제거한다. 파일 시스템은 DB가 자동 롤백해 주지 않는다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(filename);
            }
        });
        return filename;
    }

    public String publicUrl(String filename) {
        return filename == null ? null : "/api/menu-images/" + filename;
    }

    public Resource load(String filename) {
        Path path = checkedPath(filename);
        return path != null && Files.isRegularFile(path) ? new FileSystemResource(path) : null;
    }

    public void deleteAfterCommit(String filename) {
        if (filename == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { deleteQuietly(filename); }
        });
    }

    private Path checkedPath(String filename) {
        if (filename == null || !filename.matches("[0-9a-f-]{36}\\.(jpg|png)")) return null;
        Path path = directory.resolve(filename).normalize();
        return path.startsWith(directory) ? path : null;
    }

    private void deleteQuietly(String filename) {
        Path path = checkedPath(filename);
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("메뉴 사진 정리에 실패했습니다: {}", filename, ex);
        }
    }
}
