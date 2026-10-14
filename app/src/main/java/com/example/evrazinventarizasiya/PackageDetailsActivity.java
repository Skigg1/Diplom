package com.example.evrazinventarizasiya;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.example.evrazinventarizasiya.R;

public class PackageDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_PACKAGE_ID = "package_id";

    private DatabaseHelper dbHelper;
    private TableLayout tableLayout;
    private long packageId;
    private long selectedLocationId;
    private String selectedDateFromMain;

    private long currentInventoryId;
    private String editedDate;
    private String editedStatus;
    private final String[] statuses = {"на месте", "перемещён", "отгружен"};
    private boolean isDateChangedByUser = false;

    private int currentQuantity;
    private int originalQuantity;
    private TextView quantityTextView;

    private List<DatabaseHelper.Inventor> allInventors;
    private List<Long> selectedInventorIds = new ArrayList<>();
    private TextView inventorsTextView;

    private final ActivityResultLauncher<Intent> scannerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String scannedData = result.getData().getStringExtra("scanned_data");
                    String[] parts = scannedData.split(";");
                    if (parts.length == 4) {
                        try {
                            long productId = Long.parseLong(parts[0]);
                            long brandId = Long.parseLong(parts[1]);
                            double length = Double.parseDouble(parts[2]);
                            double weight = Double.parseDouble(parts[3]);

                            Cursor pCursor = dbHelper.findPackage(productId, brandId, length, weight);
                            long newPackageId = -1;
                            if (pCursor != null && pCursor.moveToFirst()) {
                                int idColumn = pCursor.getColumnIndex("ID");
                                if (idColumn != -1) {
                                    newPackageId = pCursor.getLong(idColumn);
                                }
                                pCursor.close();
                            } else {
                                if (pCursor != null) pCursor.close();
                                newPackageId = dbHelper.addPackage(productId, brandId, length, weight, 1);
                            }

                            if (newPackageId != -1) {
                                Intent intent = new Intent(PackageDetailsActivity.this, PackageDetailsActivity.class);
                                intent.putExtra(EXTRA_PACKAGE_ID, newPackageId);
                                intent.putExtra("selected_location_id", selectedLocationId);
                                intent.putExtra("selected_date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(PackageDetailsActivity.this, "Ошибка: не удалось определить пакет", Toast.LENGTH_LONG).show();
                            }
                        } catch (NumberFormatException e) {
                            Toast.makeText(PackageDetailsActivity.this, "Неверный формат QR-кода (ожидается: ПродукцияID;МаркаID;Длина;Вес)", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(PackageDetailsActivity.this, "QR-код должен содержать: ПродукцияID;МаркаID;Длина;Вес", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(PackageDetailsActivity.this, "Сканирование отменено", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_package_details);

        tableLayout = findViewById(R.id.tableLayout);
        dbHelper = new DatabaseHelper(this);

        packageId = getIntent().getLongExtra(EXTRA_PACKAGE_ID, -1);
        selectedLocationId = getIntent().getLongExtra("selected_location_id", -1);
        selectedDateFromMain = getIntent().getStringExtra("selected_date");

        if (packageId == -1) {
            Toast.makeText(this, "Ошибка: не передан ID пакета", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Button backButton = findViewById(R.id.backButton);
        Button saveButton = findViewById(R.id.saveButton);
        Button continueButton = findViewById(R.id.continueButton);

        backButton.setOnClickListener(v -> finish());
        saveButton.setOnClickListener(v -> saveInventoryChanges(false));
        continueButton.setOnClickListener(v -> {
            saveInventoryChanges(true);
            Intent intent = new Intent(PackageDetailsActivity.this, ScannerActivity.class);
            intent.putExtra("locationId", selectedLocationId);
            scannerLauncher.launch(intent);
        });

        loadPackageData();
    }

    private void loadPackageData() {
        tableLayout.removeAllViews();
        allInventors = dbHelper.getAllInventors();

        Cursor pCursor = dbHelper.getPackageById(packageId);
        if (pCursor != null && pCursor.moveToFirst()) {
            int idxQuantity = pCursor.getColumnIndex("quantity");
            if (idxQuantity != -1) {
                currentQuantity = pCursor.getInt(idxQuantity);
                originalQuantity = currentQuantity;
            }
            pCursor.close();
        }

        // Загружаем последнюю инвентаризацию для получения даты, статуса, инвентаризаторов
        Cursor lastInv = dbHelper.getLastInventoryWithPeople(packageId);
        if (lastInv != null && lastInv.moveToFirst()) {
            int idIdx = lastInv.getColumnIndex("_id");
            currentInventoryId = (idIdx != -1) ? lastInv.getLong(idIdx) : -1;

            int idxDate = lastInv.getColumnIndex("inventory_date");
            if (selectedDateFromMain != null && !selectedDateFromMain.isEmpty()) {
                editedDate = selectedDateFromMain;
                isDateChangedByUser = false;
            } else if (idxDate != -1) {
                editedDate = lastInv.getString(idxDate);
                isDateChangedByUser = false;
            }
            addDateRow("Дата инвентаризации", editedDate);

            int idxStatus = lastInv.getColumnIndex("status");
            if (idxStatus != -1) {
                editedStatus = lastInv.getString(idxStatus);
            } else {
                editedStatus = "на месте";
            }

            int idxInventors = lastInv.getColumnIndex("inventors");
            String inventorsStr = (idxInventors != -1) ? lastInv.getString(idxInventors) : null;
            if (inventorsStr != null && !inventorsStr.isEmpty()) {
                selectedInventorIds.clear();
                String[] names = inventorsStr.split(", ");
                for (String name : names) {
                    for (DatabaseHelper.Inventor inv : allInventors) {
                        if (inv.name.equals(name) && !selectedInventorIds.contains(inv.id)) {
                            selectedInventorIds.add(inv.id);
                            break;
                        }
                    }
                }
                addInventorsRow("Инвентаризаторы", inventorsStr);
            } else {
                String displayText = buildInventorsDisplayText();
                addInventorsRow("Инвентаризаторы", displayText);
            }
            lastInv.close();
        } else {
            currentInventoryId = 0;
            editedDate = (selectedDateFromMain != null) ? selectedDateFromMain : "";
            isDateChangedByUser = false;
            addDateRow("Дата инвентаризации", editedDate);
            editedStatus = "на месте";
            addInventorsRow("Инвентаризаторы", buildInventorsDisplayText());
        }

        addLocationRow("Текущее место", getFullLocationPath(selectedLocationId));
        addQuantityRow("Количество пакетов", currentQuantity);
        addTableRow("ID пакета", String.valueOf(packageId));

        Cursor pc = dbHelper.getPackageById(packageId);
        if (pc != null && pc.moveToFirst()) {
            int idxProduct = pc.getColumnIndex("product_name");
            int idxBrand = pc.getColumnIndex("brand_name");
            int idxLength = pc.getColumnIndex("length");
            int idxWeight = pc.getColumnIndex("weight");
            if (idxProduct != -1) addTableRow("Продукция", pc.getString(idxProduct));
            if (idxBrand != -1) addTableRow("Марка", pc.getString(idxBrand));
            if (idxLength != -1) addTableRow("Длина", String.valueOf(pc.getDouble(idxLength)));
            if (idxWeight != -1) addTableRow("Вес", String.valueOf(pc.getDouble(idxWeight)));
            pc.close();
        }

        addStatusRow("Статус", editedStatus);

        Cursor lastInv2 = dbHelper.getLastInventoryWithPeople(packageId);
        if (lastInv2 != null && lastInv2.moveToFirst()) {
            int idxCreation = lastInv2.getColumnIndex("creation_date");
            if (idxCreation != -1 && !lastInv2.isNull(idxCreation)) {
                addTableRow("Дата создания записи", lastInv2.getString(idxCreation));
            } else {
                addTableRow("Дата создания записи", "нет");
            }
            lastInv2.close();
        } else {
            addTableRow("Дата создания записи", "нет");
        }

        Cursor histCursor = dbHelper.getLastTwoInventoryHistoryWithPeople(packageId);
        if (histCursor != null && histCursor.getCount() > 0) {
            addHeaderRow("=== История перемещений ===");
            List<HistoryItem> items = new ArrayList<>();
            while (histCursor.moveToNext()) {
                int idxLoc = histCursor.getColumnIndex("location_name");
                int idxDate = histCursor.getColumnIndex("inventory_date");
                int idxInv = histCursor.getColumnIndex("inventors");
                int idxStatus = histCursor.getColumnIndex("status");
                if (idxLoc == -1 || idxDate == -1) continue;
                String loc = histCursor.getString(idxLoc);
                String date = histCursor.getString(idxDate);
                String inventors = (idxInv != -1) ? histCursor.getString(idxInv) : "";
                String status = (idxStatus != -1) ? histCursor.getString(idxStatus) : "";
                items.add(new HistoryItem(loc, date, inventors, status));
            }
            histCursor.close();

            if (items.size() == 2) {
                HistoryItem oldItem = items.get(1);
                HistoryItem newItem = items.get(0);
                String oldDisplay = oldItem.location + " (" + oldItem.date + ", " + oldItem.status + ")";
                if (!TextUtils.isEmpty(oldItem.inventors)) oldDisplay += " [" + oldItem.inventors + "]";
                String newDisplay = newItem.location + " (" + newItem.date + ", " + newItem.status + ")";
                if (!TextUtils.isEmpty(newItem.inventors)) newDisplay += " [" + newItem.inventors + "]";
                addTableRow("Первоначально", oldDisplay);
                addTableRow("Перемещён", oldItem.location + " → " + newItem.location + " (" + newItem.date + ")");
            } else if (items.size() == 1) {
                HistoryItem onlyItem = items.get(0);
                String display = onlyItem.location + " (" + onlyItem.date + ", " + onlyItem.status + ")";
                if (!TextUtils.isEmpty(onlyItem.inventors)) display += " [" + onlyItem.inventors + "]";
                addTableRow("Первоначально", display);
            }
        } else if (histCursor != null) {
            histCursor.close();
        }

        Cursor actual = dbHelper.getLastInventoryWithPeople(packageId);
        if (actual != null && actual.moveToFirst()) {
            int idxActualId = actual.getColumnIndex("location_id");
            if (idxActualId != -1) {
                long actualLocId = actual.getLong(idxActualId);
                if (actualLocId != selectedLocationId && selectedLocationId != -1) {
                    String actualFullPath = getFullLocationPath(actualLocId);
                    addWarningRowWithWrap("Фактическое место хранения: " + actualFullPath +
                            " (отличается от выбранного)");
                }
            }
            actual.close();
        } else if (actual != null) {
            actual.close();
        }
    }

    private void addQuantityRow(String label, int value) {
        TableRow row = new TableRow(this);
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + ": ");
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setPadding(16, 12, 8, 12);
        row.addView(tvLabel);

        quantityTextView = new TextView(this);
        quantityTextView.setText(String.valueOf(value));
        quantityTextView.setPadding(8, 12, 16, 12);
        quantityTextView.setClickable(true);
        quantityTextView.setFocusable(true);
        quantityTextView.setBackgroundResource(android.R.drawable.editbox_background);
        quantityTextView.setOnClickListener(v -> showQuantityPicker());
        row.addView(quantityTextView);
        tableLayout.addView(row);
    }

    private void showQuantityPicker() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(currentQuantity));
        builder.setView(input);
        builder.setTitle("Изменить количество пакетов");
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                int newQuantity = Integer.parseInt(input.getText().toString());
                if (newQuantity > 0) {
                    currentQuantity = newQuantity;
                    quantityTextView.setText(String.valueOf(currentQuantity));
                } else {
                    Toast.makeText(this, "Количество должно быть больше 0", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Введите число", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private String buildInventorsDisplayText() {
        StringBuilder sb = new StringBuilder();
        for (DatabaseHelper.Inventor inv : allInventors) {
            if (selectedInventorIds.contains(inv.id)) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(inv.name);
            }
        }
        return sb.length() > 0 ? sb.toString() : "не выбраны";
    }

    private void addInventorsRow(String label, String value) {
        TableRow row = new TableRow(this);
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + ": ");
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setPadding(16, 12, 8, 12);
        row.addView(tvLabel);

        inventorsTextView = new TextView(this);
        inventorsTextView.setText(value);
        inventorsTextView.setPadding(8, 12, 16, 12);
        inventorsTextView.setClickable(true);
        inventorsTextView.setFocusable(true);
        inventorsTextView.setBackgroundResource(android.R.drawable.editbox_background);
        inventorsTextView.setOnClickListener(v -> showInventorPicker());
        row.addView(inventorsTextView);
        tableLayout.addView(row);
    }

    private void showInventorPicker() {
        if (allInventors == null) allInventors = dbHelper.getAllInventors();
        String[] names = new String[allInventors.size()];
        boolean[] checked = new boolean[allInventors.size()];
        for (int i = 0; i < allInventors.size(); i++) {
            names[i] = allInventors.get(i).name;
            checked[i] = selectedInventorIds.contains(allInventors.get(i).id);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выберите инвентаризаторов");
        builder.setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> {
            long id = allInventors.get(which).id;
            if (isChecked) {
                if (!selectedInventorIds.contains(id)) selectedInventorIds.add(id);
            } else {
                selectedInventorIds.remove(Long.valueOf(id));
            }
        });
        builder.setPositiveButton("OK", (dialog, which) -> {
            String newText = buildInventorsDisplayText();
            if (inventorsTextView != null) {
                inventorsTextView.setText(newText);
            } else {
                loadPackageData();
            }
        });
        builder.setNeutralButton("Добавить нового", (dialog, which) -> showAddInventorDialog());
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showAddInventorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setHint("ФИО");
        builder.setView(input);
        builder.setTitle("Новый инвентаризатор");
        builder.setPositiveButton("Добавить", (dialog, which) -> {
            String fio = input.getText().toString().trim();
            if (!fio.isEmpty()) {
                long id = dbHelper.addInventor(fio);
                if (id != -1) {
                    allInventors = dbHelper.getAllInventors();
                    selectedInventorIds.add(id);
                    String newText = buildInventorsDisplayText();
                    if (inventorsTextView != null) {
                        inventorsTextView.setText(newText);
                    }
                }
            }
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private String getFullLocationPath(long locationId) {
        return dbHelper.getFullLocationPath(locationId);
    }

    private void addTableRow(String label, String value) {
        TableRow row = new TableRow(this);
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + ": ");
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setPadding(16, 12, 8, 12);
        row.addView(tvLabel);
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setPadding(8, 12, 16, 12);
        row.addView(tvValue);
        tableLayout.addView(row);
    }

    private void addLocationRow(String label, String value) {
        TableRow row = new TableRow(this);
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + ": ");
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setPadding(16, 12, 8, 12);
        row.addView(tvLabel);
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setPadding(8, 12, 16, 12);
        tvValue.setSingleLine(false);
        row.addView(tvValue);
        tableLayout.addView(row);
    }

    private void addDateRow(String label, String value) {
        TableRow row = new TableRow(this);
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + ": ");
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setPadding(16, 12, 8, 12);
        row.addView(tvLabel);
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setPadding(8, 12, 16, 12);
        tvValue.setClickable(true);
        tvValue.setBackgroundResource(android.R.drawable.editbox_background);
        tvValue.setOnClickListener(v -> showDatePicker(tvValue));
        row.addView(tvValue);
        tableLayout.addView(row);
    }

    private void addStatusRow(String label, String value) {
        TableRow row = new TableRow(this);
        TextView tvLabel = new TextView(this);
        tvLabel.setText(label + ": ");
        tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        tvLabel.setPadding(16, 12, 8, 12);
        row.addView(tvLabel);
        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setPadding(8, 12, 16, 12);
        tvValue.setClickable(true);
        tvValue.setBackgroundResource(android.R.drawable.editbox_background);
        tvValue.setOnClickListener(v -> showStatusPicker(tvValue));
        row.addView(tvValue);
        tableLayout.addView(row);
    }

    private void addHeaderRow(String text) {
        TableRow row = new TableRow(this);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTypeface(null, android.graphics.Typeface.BOLD_ITALIC);
        tv.setPadding(16, 24, 8, 8);
        tv.setBackgroundColor(0xFFEEEEEE);
        row.addView(tv);
        tableLayout.addView(row);
    }

    private void addWarningRowWithWrap(String text) {
        TableRow row = new TableRow(this);
        TextView tvLabel = new TextView(this);
        tvLabel.setText("");
        row.addView(tvLabel);
        TextView tvValue = new TextView(this);
        tvValue.setText(text);
        tvValue.setTextColor(0xFFFF0000);
        tvValue.setPadding(8, 12, 16, 12);
        tvValue.setSingleLine(false);
        row.addView(tvValue);
        tableLayout.addView(row);
    }

    private void showDatePicker(TextView dateTextView) {
        Calendar calendar = Calendar.getInstance();
        try {
            if (editedDate != null && !editedDate.isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                calendar.setTime(sdf.parse(editedDate));
            }
        } catch (Exception e) {
            // ignore
        }
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, y, m, d) -> {
                    isDateChangedByUser = true;
                    editedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                    dateTextView.setText(editedDate);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void showStatusPicker(TextView statusTextView) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выберите статус")
                .setItems(statuses, (dialog, which) -> {
                    editedStatus = statuses[which];
                    statusTextView.setText(editedStatus);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveInventoryChanges(boolean isAutoContinue) {
        if (editedDate == null || editedDate.isEmpty()) {
            Toast.makeText(this, "Нет даты для сохранения", Toast.LENGTH_SHORT).show();
            return;
        }
        if (editedStatus == null || editedStatus.isEmpty()) {
            Toast.makeText(this, "Нет статуса для сохранения", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedLocationId == -1) {
            Toast.makeText(this, "Не выбран склад/полка", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentQuantity != originalQuantity) {
            dbHelper.updatePackageQuantity(packageId, currentQuantity);
            originalQuantity = currentQuantity;
        }

        String dateToSave;
        if (currentInventoryId == 0) {
            dateToSave = editedDate;
        } else {
            if (isDateChangedByUser) {
                dateToSave = editedDate;
            } else {
                dateToSave = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            }
        }

        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Сохранение...");
        progress.setCancelable(false);
        progress.show();

        boolean success;
        if (currentInventoryId == 0) {
            long newId = dbHelper.addInventory(packageId, selectedLocationId, editedStatus, dateToSave, selectedInventorIds);
            success = (newId != -1);
            if (success) {
                currentInventoryId = newId;
            }
        } else {
            success = dbHelper.updateInventory(currentInventoryId, editedStatus, dateToSave, selectedLocationId, selectedInventorIds);
        }
        progress.dismiss();
        if (success) {
            if (!isAutoContinue) {
                Toast.makeText(this, "Данные сохранены", Toast.LENGTH_LONG).show();
            }
            loadPackageData();
        } else {
            Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_LONG).show();
        }
    }

    private static class HistoryItem {
        String location, date, inventors, status;
        HistoryItem(String loc, String d, String inv, String stat) {
            location = loc;
            date = d;
            inventors = inv;
            status = stat;
        }
    }
}