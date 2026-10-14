package com.example.evrazinventarizasiya;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportGenerator {

    private Context context;
    private DatabaseHelper dbHelper;
    private static final String TAG = "ReportGenerator";

    public ReportGenerator(Context context, DatabaseHelper dbHelper) {
        this.context = context;
        this.dbHelper = dbHelper;
    }

    private Font getTitleFont() {
        try {
            BaseFont baseFont = BaseFont.createFont("assets/fonts/arialamu.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            return new Font(baseFont, 18, Font.BOLD);
        } catch (Exception e) {
            e.printStackTrace();
            return FontFactory.getFont(FontFactory.HELVETICA, 18, Font.BOLD);
        }
    }

    private Font getHeaderFont() {
        try {
            BaseFont baseFont = BaseFont.createFont("assets/fonts/arialamu.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            return new Font(baseFont, 12, Font.BOLD);
        } catch (Exception e) {
            e.printStackTrace();
            return FontFactory.getFont(FontFactory.HELVETICA, 12, Font.BOLD);
        }
    }

    private Font getNormalFont() {
        try {
            BaseFont baseFont = BaseFont.createFont("assets/fonts/arialamu.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            return new Font(baseFont, 10, Font.NORMAL);
        } catch (Exception e) {
            e.printStackTrace();
            return FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL);
        }
    }

    public File generateMovementReport(String startDate, String endDate, String sortBy) {
        try {
            File reportDir = new File(context.getExternalFilesDir(null), "reports");
            if (!reportDir.exists()) reportDir.mkdirs();
            String fileName = "movement_" + startDate + "_to_" + endDate + "_" + sortBy + ".pdf";
            File pdfFile = new File(reportDir, fileName);

            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));
            document.open();

            String title = String.format("Отчёт о инвентаризации за период %s - %s", startDate, endDate);
            Paragraph titlePara = new Paragraph(title, getTitleFont());
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(20);
            document.add(titlePara);

            Cursor cursor = getReportData(startDate, endDate, sortBy);
            if (cursor == null) {
                document.add(new Paragraph("Ошибка выполнения запроса.", getNormalFont()));
                document.close();
                return pdfFile;
            }

            int rowCount = cursor.getCount();
            Log.d(TAG, "Cursor count: " + rowCount);
            if (rowCount == 0) {
                document.add(new Paragraph("Нет данных о перемещениях за указанный период.", getNormalFont()));
                document.close();
                return pdfFile;
            }

            int idxProduct = cursor.getColumnIndex("product_name");
            int idxBrand = cursor.getColumnIndex("brand_name");
            int idxLength = cursor.getColumnIndex("length");
            int idxWeight = cursor.getColumnIndex("weight");
            int idxQuantity = cursor.getColumnIndex("quantity");
            int idxLocation = cursor.getColumnIndex("location_name");
            int idxDate = cursor.getColumnIndex("move_date");
            int idxStatus = cursor.getColumnIndex("status");
            int idxInventors = cursor.getColumnIndex("inventors");

            if (idxProduct == -1 || idxBrand == -1 || idxLength == -1 || idxWeight == -1 ||
                    idxQuantity == -1 || idxLocation == -1 || idxDate == -1 || idxStatus == -1 || idxInventors == -1) {
                Log.e(TAG, "Missing columns");
                document.add(new Paragraph("Ошибка структуры данных.", getNormalFont()));
                document.close();
                cursor.close();
                return pdfFile;
            }

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);
            table.setSpacingAfter(10);
            float[] widths = {0.15f, 0.10f, 0.08f, 0.08f, 0.08f, 0.15f, 0.12f, 0.12f, 0.12f};
            table.setWidths(widths);

            addTableHeader(table, "Продукция", getHeaderFont());
            addTableHeader(table, "Марка", getHeaderFont());
            addTableHeader(table, "Длина (м)", getHeaderFont());
            addTableHeader(table, "Вес (кг)", getHeaderFont());
            addTableHeader(table, "Кол-во", getHeaderFont());
            addTableHeader(table, "Место", getHeaderFont());
            addTableHeader(table, "Дата", getHeaderFont());
            addTableHeader(table, "Статус", getHeaderFont());
            addTableHeader(table, "Инвентаризаторы", getHeaderFont());

            int added = 0;
            while (cursor.moveToNext()) {
                String product = cursor.getString(idxProduct);
                String brand = cursor.getString(idxBrand);
                double length = cursor.getDouble(idxLength);
                double weight = cursor.getDouble(idxWeight);
                int quantity = cursor.getInt(idxQuantity);
                String location = cursor.getString(idxLocation);
                String date = cursor.getString(idxDate);
                String status = cursor.getString(idxStatus);
                String inventors = cursor.getString(idxInventors);

                if (product == null) product = "нет";
                if (brand == null) brand = "нет";
                if (location == null) location = "нет";
                if (status == null) status = "нет";
                if (date == null) date = "нет";
                if (inventors == null) inventors = "не указаны";

                table.addCell(new Phrase(product, getNormalFont()));
                table.addCell(new Phrase(brand, getNormalFont()));
                table.addCell(new Phrase(String.format(Locale.US, "%.2f", length), getNormalFont()));
                table.addCell(new Phrase(String.format(Locale.US, "%.2f", weight), getNormalFont()));
                table.addCell(new Phrase(String.valueOf(quantity), getNormalFont()));
                table.addCell(new Phrase(location, getNormalFont()));
                table.addCell(new Phrase(date, getNormalFont()));
                table.addCell(new Phrase(status, getNormalFont()));
                table.addCell(new Phrase(inventors, getNormalFont()));
                added++;
            }
            cursor.close();
            Log.d(TAG, "Добавлено строк: " + added);

            document.add(table);

            String generationDate = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(new Date());
            Paragraph footer = new Paragraph("Отчёт сгенерирован: " + generationDate, getNormalFont());
            footer.setAlignment(Element.ALIGN_RIGHT);
            footer.setSpacingBefore(20);
            document.add(footer);

            document.close();
            Log.d(TAG, "PDF сохранён: " + pdfFile.getAbsolutePath());
            return pdfFile;
        } catch (Exception e) {
            Log.e(TAG, "Ошибка генерации отчёта", e);
            e.printStackTrace();
            return null;
        }
    }

    private void addTableHeader(PdfPTable table, String headerText, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(headerText, font));
        cell.setBackgroundColor(BaseColor.LIGHT_GRAY);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private Cursor getReportData(String startDate, String endDate, String sortBy) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String orderBy = "move_date";
        if ("product".equals(sortBy)) {
            orderBy = "product_name, move_date";
        } else if ("location".equals(sortBy)) {
            orderBy = "location_name, move_date";
        }

        String sql = "SELECT " +
                "пр.Наименование as product_name, " +
                "м.Название as brand_name, " +
                "п.Длина as length, " +
                "п.Вес as weight, " +
                "п.Количество as quantity, " +
                "(SELECT Название FROM Места_хранения WHERE ID = sess.МестоID) as location_name, " +
                "sess.ДатаИнвентаризации as move_date, " +
                "inv.Статус as status, " +
                "GROUP_CONCAT(inf.ФИО, ', ') as inventors " +
                "FROM ИнвентаризацияСессии sess " +
                "LEFT JOIN Инвентаризация inv ON sess.ИнвентаризацияID = inv.ID " +
                "LEFT JOIN Пакеты п ON inv.ПакетID = п.ID " +
                "LEFT JOIN Продукция пр ON п.ПродукцияID = пр.ID " +
                "LEFT JOIN Марки м ON п.МаркаID = м.ID " +
                "LEFT JOIN ИнвентаризацияСессииУчастники part ON sess.ID = part.СессияID " +
                "LEFT JOIN Инвентаризаторы inf ON part.ИнвентаризаторID = inf.ID " +
                "WHERE sess.ДатаИнвентаризации BETWEEN ? AND ? " +
                "GROUP BY sess.ID " +
                "ORDER BY " + orderBy;
        return db.rawQuery(sql, new String[]{startDate, endDate});
    }
}