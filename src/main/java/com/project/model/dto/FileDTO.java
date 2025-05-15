package com.project.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigInteger;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileDTO {
    UUID fileId;
    String fileName;
    String extension;
    BigInteger size;
}
