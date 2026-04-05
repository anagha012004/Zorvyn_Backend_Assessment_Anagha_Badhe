package com.financeapi.service.impl;

import com.financeapi.domain.Transaction;
import com.financeapi.repository.TransactionRepository;
import com.financeapi.util.TransactionSpecification;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PdfExportService {

    private final TransactionRepository transactionRepository;

    public byte[] exportStatement(LocalDate from, LocalDate to) {
        List<Transaction> transactions = transactionRepository.findAll(
                TransactionSpecification.filter(null, null, from, to, null));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(out);
        PdfDocument pdf = new PdfDocument(writer);
        Document doc = new Document(pdf);

        // Title
        doc.add(new Paragraph("Financial Statement")
                .setFontSize(20).setBold().setTextAlignment(TextAlignment.CENTER));
        String period = (from != null ? from.toString() : "All time") + " – " +
                        (to != null ? to.toString() : LocalDate.now().toString());
        doc.add(new Paragraph(period)
                .setFontSize(11).setTextAlignment(TextAlignment.CENTER).setMarginBottom(16));

        // Summary
        BigDecimal totalIncome = transactions.stream()
                .filter(t -> t.getType().name().equals("INCOME"))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = transactions.stream()
                .filter(t -> t.getType().name().equals("EXPENSE"))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        doc.add(new Paragraph("Total Income:  ₹" + totalIncome).setFontSize(12));
        doc.add(new Paragraph("Total Expenses: ₹" + totalExpense).setFontSize(12));
        doc.add(new Paragraph("Net Balance:   ₹" + totalIncome.subtract(totalExpense))
                .setFontSize(12).setBold().setMarginBottom(16));

        // Table
        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 2, 2, 2, 3, 4}))
                .useAllAvailableWidth();
        String[] headers = {"ID", "Date", "Type", "Amount", "Category", "Notes"};
        for (String h : headers) {
            table.addHeaderCell(new Cell().add(new Paragraph(h).setBold())
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY));
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");
        for (Transaction t : transactions) {
            table.addCell(String.valueOf(t.getId()));
            table.addCell(t.getDate().format(fmt));
            table.addCell(t.getType().name());
            table.addCell("₹" + t.getAmount());
            table.addCell(t.getCategory() != null ? t.getCategory().getName() : "—");
            table.addCell(t.getNotes() != null ? t.getNotes() : "—");
        }
        doc.add(table);
        doc.close();
        return out.toByteArray();
    }
}
