package com.project.service;

import com.project.model.dto.FileDTO;
import com.project.repository.FileRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class FileService {

    private final FileRepository fileRepository;

    public FileService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    public UUID saveFile(FileDTO dto, byte[] data) throws Exception {
        UUID fileId = fileRepository.saveFile(dto, data);
        if (fileId == null) {
            throw new Exception("Failed to save file");
        }
        return fileId;
    }

    public byte[] getFileData(UUID fileId) throws Exception {
        byte[] fileData = fileRepository.getFileData(fileId);
        if (fileData == null) {
            throw new Exception("Failed to get file data");
        }
        return fileData;
    }
}

