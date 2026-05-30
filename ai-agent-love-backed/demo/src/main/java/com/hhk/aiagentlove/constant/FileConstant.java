package com.hhk.aiagentlove.constant;

import java.io.File;

/**
 * 文件保存目录：固定到 aigent-love/demo/tmp/pdf
 */
public final class FileConstant {

    public static final String FILE_SAVE_DIR;
    public static final String PDF_SAVE_DIR;

    static {
        String baseDir = System.getProperty("user.dir");
        String normalized = baseDir.replace('\\', '/');
        if (normalized.endsWith("/target")) {
            baseDir = baseDir.substring(0, baseDir.length() - "target".length());
            normalized = baseDir.replace('\\', '/');
        }
        File demoDir;
        if (normalized.endsWith("/demo")) {
            demoDir = new File(baseDir);
        } else {
            demoDir = new File(baseDir, "demo");
        }
        FILE_SAVE_DIR = new File(demoDir, "tmp").getAbsolutePath();
        PDF_SAVE_DIR = new File(FILE_SAVE_DIR, "pdf").getAbsolutePath();
        new File(PDF_SAVE_DIR).mkdirs();
    }

    private FileConstant() {
    }
}
