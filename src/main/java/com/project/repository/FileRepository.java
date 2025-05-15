package com.project.repository;

import com.project.DatabaseManager;
import com.project.jooq.tables.Files;
import com.project.model.dto.FileDTO;
import org.jooq.DSLContext;

import java.util.UUID;

public class FileRepository {
    public boolean updateFileEntityId(UUID fileId, UUID messageId) {
        DSLContext dsl = DatabaseManager.dsl();
        Files f = Files.FILES;

        int rowsUpdated = dsl.update(f)
                .set(f.ENTITY_ID, messageId)
                .where(f.ID.eq(fileId))
                .execute();

        return rowsUpdated > 0;
    }

    public UUID saveFile(FileDTO dto, byte[] data) {
        DSLContext dsl = DatabaseManager.dsl();
        Files f = Files.FILES;

        return dsl.insertInto(f)
                .set(f.FILE_NAME, dto.getFileName())
                .set(f.FILE_SIZE, dto.getSize().longValue())
                .set(f.DATA, data)
                .set(f.EXTENSION, dto.getExtension())
                .returning(f.ID)
                .fetchOne()
                .getId();
    }

    public byte[] getFileData(UUID fileId) {
        DSLContext dsl = DatabaseManager.dsl();
        Files f = Files.FILES;

        return dsl.select(f.DATA)
                .from(f)
                .where(f.ID.eq(fileId))
                .fetchOne()
                .get(f.DATA);
    }
}
