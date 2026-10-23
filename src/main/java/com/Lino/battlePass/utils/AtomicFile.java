package com.Lino.battlePass.utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;

/** Flush a temporary sibling before replacing one file; preserve existing symlink destinations. */
public final class AtomicFile {
    private AtomicFile() {}

    public static void write(Path file, byte[] bytes) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            if (!Files.isRegularFile(target)) throw new IOException("Configuration target is not a file: " + target);
            target = target.toRealPath();
        }
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".tmp-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
