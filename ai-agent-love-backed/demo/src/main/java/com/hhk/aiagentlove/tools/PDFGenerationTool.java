package com.hhk.aiagentlove.tools;

import cn.hutool.core.io.FileUtil;
import com.hhk.aiagentlove.constant.FileConstant;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.IOException;

@Slf4j
public class PDFGenerationTool {

    @Tool(description = """
            Generate a PDF file from plain text content. Use after you have collected all information.
            fileName must end with .pdf. content should be the full Chinese report text.""")
    public String generatePDF(
            @ToolParam(description = "PDF file name, must end with .pdf, e.g. beijing_dating.pdf") String fileName,
            @ToolParam(description = "Full text content to write into the PDF") String content) {
        if (content == null || content.isBlank()) {
            return "Error generating PDF: content is empty";
        }

        String safeName = normalizeFileName(fileName);
        String filePath = FileConstant.PDF_SAVE_DIR + File.separator + safeName;

        try {
            FileUtil.mkdir(FileConstant.PDF_SAVE_DIR);
            PdfFont font = createPdfFont();

            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                document.setFont(font);
                for (String line : content.split("\\n")) {
                    document.add(new Paragraph(line.isBlank() ? " " : line));
                }
            }

            File saved = new File(filePath);
            if (!saved.exists() || saved.length() == 0) {
                return "Error generating PDF: file was not created on disk";
            }

            String absolutePath = saved.getAbsolutePath();
            log.info("PDF 已写入: {}", absolutePath);
            // 仅返回磁盘绝对路径，供模型原样回复给用户
            return absolutePath;
        } catch (Exception e) {
            log.error("PDF 生成失败, path={}", filePath, e);
            return "Error generating PDF: " + e.getMessage();
        }
    }

    /** 只保留文件名部分，并确保 .pdf 后缀 */
    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "report_" + System.currentTimeMillis() + ".pdf";
        }
        String name = fileName.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return name;
    }

    private PdfFont createPdfFont() throws IOException {
        try {
            return PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
        } catch (Exception e1) {
            log.warn("STSongStd-Light 不可用，尝试 STSong-Light: {}", e1.getMessage());
            try {
                return PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
            } catch (Exception e2) {
                log.warn("STSong-Light 不可用，使用 Helvetica: {}", e2.getMessage());
                return PdfFontFactory.createFont(StandardFonts.HELVETICA);
            }
        }
    }
}
