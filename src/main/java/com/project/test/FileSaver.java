package com.project.test;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileSaver {
    public static String saveFileToProjectRoot(String fileName, byte[] data) {
        String projectRoot = System.getProperty("user.dir");
        Path filePath = Paths.get(projectRoot, fileName);

        try {
            Files.write(filePath, data);
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return filePath.toAbsolutePath().toString();
    }
}
