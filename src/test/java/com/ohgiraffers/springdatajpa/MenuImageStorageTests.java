package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.exception.InvalidMenuImageException;
import com.ohgiraffers.springdatajpa.service.MenuImageStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuImageStorageTests {
    @TempDir Path directory;
    MenuImageStorage storage;

    @BeforeEach void begin() {
        storage = new MenuImageStorage(directory.toString());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach void rollbackRemaining() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) finish(false);
    }

    void finish(boolean commit) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (commit) synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(s -> s.afterCompletion(commit
                ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
    }

    static MockMultipartFile photo(String format) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(32, 24, BufferedImage.TYPE_INT_RGB), format, out);
        return new MockMultipartFile("image", "../../same-name.jpg", "image/" + format, out.toByteArray());
    }

    @Test void validatesActualFormatAndGeneratesUniqueNames() throws Exception {
        String first = storage.store(photo("png"));
        String second = storage.store(photo("png"));
        assertNotEquals(first, second);
        assertTrue(first.endsWith(".png"));
        assertNotNull(storage.load(first));
        assertEquals("/api/menu-images/" + first, storage.publicUrl(first));
        assertNull(storage.load("../application.yaml"));
        assertNull(storage.publicUrl(null));
    }

    @Test void supportsJpeg() throws Exception {
        String name = storage.store(photo("jpeg"));
        assertTrue(name.endsWith(".jpg"));
        assertNotNull(ImageIO.read(directory.resolve(name).toFile()));
    }

    @Test void rejectsFakeEmptyAndUnsupportedImages() throws Exception {
        assertThrows(InvalidMenuImageException.class, () -> storage.store(
                new MockMultipartFile("image", "fake.jpg", "image/jpeg", "not an image".getBytes())));
        assertThrows(InvalidMenuImageException.class, () -> storage.store(
                new MockMultipartFile("image", "empty.png", "image/png", new byte[0])));
        assertThrows(InvalidMenuImageException.class, () -> storage.store(photo("gif")));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test void rejectsMoreThanFiveMegabytes() {
        assertThrows(InvalidMenuImageException.class, () -> storage.store(
                new MockMultipartFile("image", "large.png", "image/png", new byte[(int) MenuImageStorage.MAX_BYTES + 1])));
    }

    @Test void rollbackRemovesNewFile() throws Exception {
        String name = storage.store(photo("png"));
        finish(false);
        assertNull(storage.load(name));
    }

    @Test void replacementDeletesOldOnlyAfterCommit() throws Exception {
        String old = storage.store(photo("png"));
        finish(true);
        TransactionSynchronizationManager.initSynchronization();
        String next = storage.store(photo("jpeg"));
        storage.deleteAfterCommit(old);
        assertNotNull(storage.load(old));
        finish(true);
        assertNull(storage.load(old));
        assertNotNull(storage.load(next));
    }

    @Test void failedReplacementKeepsOldPhoto() throws Exception {
        String old = storage.store(photo("png"));
        finish(true);
        TransactionSynchronizationManager.initSynchronization();
        String next = storage.store(photo("jpeg"));
        storage.deleteAfterCommit(old);
        finish(false);
        assertNotNull(storage.load(old));
        assertNull(storage.load(next));
    }
}
