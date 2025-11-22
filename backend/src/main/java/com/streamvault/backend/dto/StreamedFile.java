package com.streamvault.backend.dto;

public record StreamedFile(
    byte[] data,
    long start,
    long end,
    long totalSize,
    String contentType,
    boolean partial
) {}
