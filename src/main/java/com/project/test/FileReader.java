package com.project.test;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class FileReader {
    public static byte[] readFileFromProjectRoot(String fileName) {
        String projectRoot = System.getProperty("user.dir");
        Path filePath = Paths.get(projectRoot, fileName);
        byte[] data = null;

        try {
            data = Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return data;
    }
}