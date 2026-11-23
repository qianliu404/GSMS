package com.gsms.module.report.support;

import com.alibaba.excel.write.handler.AbstractCellWriteHandler;
import com.alibaba.excel.write.handler.SheetWriteHandler;
import com.alibaba.excel.write.handler.context.CellWriteHandlerContext;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteWorkbookHolder;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * 通用表格式导出：第 1 行大标题、第 2 行导出留痕、第 3 行表头、第 4 行起正文，
 * 统一列宽、冻结表头、合并标题，长文本列按内容自适应行高。
 */
public class TableSheetHandler extends AbstractCellWriteHandler implements SheetWriteHandler {

    public static final int TITLE_ROW = 0;
    public static final int NOTE_ROW = 1;
    public static final int HEAD_ROW = 2;
    public static final int FIRST_DATA_ROW = 3;

    private final int cols;
    private final int[] widths;
    /** 需要左对齐并自动换行的列，-1 表示无 */
    private final int wrapCol;

    private final Map<Workbook, CellStyle[]> styleCache = new IdentityHashMap<>();

    public TableSheetHandler(int[] widths) {
        this(widths, -1);
    }

    public TableSheetHandler(int[] widths, int wrapCol) {
        this.widths = widths;
        this.cols = widths.length;
        this.wrapCol = wrapCol;
    }

    @Override
    public void afterSheetCreate(WriteWorkbookHolder workbookHolder, WriteSheetHolder sheetHolder) {
        Sheet sheet = sheetHolder.getSheet();
        for (int i = 0; i < cols; i++) {
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        sheet.addMergedRegion(new CellRangeAddress(TITLE_ROW, TITLE_ROW, 0, cols - 1));
        sheet.addMergedRegion(new CellRangeAddress(NOTE_ROW, NOTE_ROW, 0, cols - 1));
        sheet.createFreezePane(0, FIRST_DATA_ROW);
        sheet.setDefaultRowHeightInPoints(22f);
    }

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        Cell cell = context.getCell();
        if (cell == null) {
            return;
        }
        int rowIndex = cell.getRowIndex();
        int columnIndex = cell.getColumnIndex();
        CellStyle style = styleFor(context.getWriteWorkbookHolder().getWorkbook(), rowIndex,
                columnIndex);
        cell.setCellStyle(style);
        if (rowIndex == TITLE_ROW) {
            cell.getRow().setHeightInPoints(34f);
        } else if (rowIndex == NOTE_ROW) {
            cell.getRow().setHeightInPoints(18f);
        } else if (rowIndex == HEAD_ROW) {
            cell.getRow().setHeightInPoints(40f);
        } else if (columnIndex == wrapCol) {
            // 受伤原因长短差异大：按列宽粗估换行行数，避免长文本被行高截断
            cell.getRow().setHeightInPoints(estimateHeight(cell.toString()));
        }
    }

    private float estimateHeight(String text) {
        if (text == null || text.isEmpty()) {
            return 24f;
        }
        int textWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            textWidth += text.charAt(i) > 255 ? 2 : 1;
        }
        int capacity = Math.max(10, widths[wrapCol] - 4);
        int lines = Math.max(1, (int) Math.ceil(textWidth / (double) capacity));
        return Math.max(24f, 16f * lines + 8f);
    }

    private CellStyle styleFor(Workbook workbook, int rowIndex, int columnIndex) {
        CellStyle[] styles = styleCache.get(workbook);
        if (styles == null) {
            styles = buildStyles(workbook);
            styleCache.put(workbook, styles);
        }
        if (rowIndex == TITLE_ROW) {
            return styles[0];
        }
        if (rowIndex == NOTE_ROW) {
            return styles[1];
        }
        if (rowIndex == HEAD_ROW) {
            return styles[2];
        }
        return columnIndex == wrapCol ? styles[4] : styles[3];
    }

    private CellStyle[] buildStyles(Workbook workbook) {
        CellStyle[] styles = new CellStyle[5];

        Font titleFont = workbook.createFont();
        titleFont.setFontName("黑体");
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        CellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);
        title.setAlignment(HorizontalAlignment.CENTER);
        title.setVerticalAlignment(VerticalAlignment.CENTER);
        styles[0] = title;

        Font noteFont = workbook.createFont();
        noteFont.setFontName("宋体");
        noteFont.setFontHeightInPoints((short) 9);
        noteFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        CellStyle note = workbook.createCellStyle();
        note.setFont(noteFont);
        note.setAlignment(HorizontalAlignment.RIGHT);
        note.setVerticalAlignment(VerticalAlignment.CENTER);
        styles[1] = note;

        Font headFont = workbook.createFont();
        headFont.setFontName("宋体");
        headFont.setBold(true);
        headFont.setFontHeightInPoints((short) 11);
        CellStyle head = base(workbook, headFont);
        head.setWrapText(true);
        styles[2] = head;

        Font bodyFont = workbook.createFont();
        bodyFont.setFontName("宋体");
        bodyFont.setFontHeightInPoints((short) 10);
        CellStyle body = base(workbook, bodyFont);
        styles[3] = body;

        CellStyle reason = base(workbook, bodyFont);
        reason.setAlignment(HorizontalAlignment.LEFT);
        reason.setWrapText(true);
        styles[4] = reason;
        return styles;
    }

    private CellStyle base(Workbook workbook, Font font) {
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_50_PERCENT.getIndex());
        return style;
    }
}
