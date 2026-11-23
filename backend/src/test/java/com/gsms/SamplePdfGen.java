package com.gsms;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;

/**
 * 一次性工具：生成冒烟测试用的样例 PDF。
 * 用法：java ... SamplePdfGen E:/GSMS/data/tmp/sample.pdf
 */
public class SamplePdfGen {

    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "sample.pdf";
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 16);
                stream.newLineAtOffset(80, 720);
                stream.showText("GSMS Sample Evidence Document");
                stream.newLineAtOffset(0, -24);
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.showText("Case: injury accident report, generated for smoke test.");
                stream.endText();
            }
            File file = new File(path);
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            document.save(file);
            System.out.println("SAVED=" + file.getAbsolutePath() + " size=" + file.length());
        }
    }
}
