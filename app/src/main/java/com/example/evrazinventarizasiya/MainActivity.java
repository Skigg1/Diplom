package com.example.evrazinventarizasiya;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private Button reportButton;
    private TextView dateTextView;
    private Spinner valuesSpinner;
    private Calendar selectedDate;
    private DatabaseHelper dbHelper;
    private long selectedLocationId; // ID выбранной полки

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
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

                            Log.d(TAG, "QR data: productId=" + productId + ", brandId=" + brandId +
                                    ", length=" + length + ", weight=" + weight);

                            Cursor pCursor = dbHelper.findPackage(productId, brandId, length, weight);
                            long packageId = -1;
                            if (pCursor != null && pCursor.moveToFirst()) {
                                int idColumn = pCursor.getColumnIndex("ID");
                                if (idColumn != -1) {
                                    packageId = pCursor.getLong(idColumn);
                                }
                                pCursor.close();
                            } else {
                                if (pCursor != null) pCursor.close();
                                packageId = dbHelper.addPackage(productId, brandId, length, weight, 1);
                            }

                            if (packageId == -1) {
                                Toast.makeText(MainActivity.this, "Ошибка: не удалось определить пакет", Toast.LENGTH_LONG).show();
                                return;
                            }

                            String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                    .format(selectedDate.getTime());

                            Intent intent = new Intent(MainActivity.this, PackageDetailsActivity.class);
                            intent.putExtra(PackageDetailsActivity.EXTRA_PACKAGE_ID, packageId);
                            intent.putExtra("selected_location_id", selectedLocationId);
                            intent.putExtra("selected_date", currentDate);
                            startActivity(intent);

                        } catch (NumberFormatException e) {
                            Toast.makeText(this, "Неверный формат QR-кода (ожидается: ПродукцияID;МаркаID;Длина;Вес)", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(this, "QR-код должен содержать: ПродукцияID;МаркаID;Длина;Вес", Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(this, "Сканирование отменено", Toast.LENGTH_SHORT).show();
                }
            });

    private void showReportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report_params, null);

        TextView startDateTextView = dialogView.findViewById(R.id.startDateTextView);
        RadioGroup periodGroup = dialogView.findViewById(R.id.periodGroup);
        RadioGroup sortGroup = dialogView.findViewById(R.id.sortGroup);

        Calendar startCalendar = (Calendar) selectedDate.clone();
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        startDateTextView.setText(sdf.format(startCalendar.getTime()));

        startDateTextView.setOnClickListener(v -> {
            DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                    (view, year, month, dayOfMonth) -> {
                        startCalendar.set(year, month, dayOfMonth);
                        startDateTextView.setText(sdf.format(startCalendar.getTime()));
                    },
                    startCalendar.get(Calendar.YEAR),
                    startCalendar.get(Calendar.MONTH),
                    startCalendar.get(Calendar.DAY_OF_MONTH));
            datePickerDialog.show();
        });

        builder.setView(dialogView);
        builder.setTitle("Параметры отчёта");
        builder.setPositiveButton("Сформировать", (dialog, which) -> {
            int periodId = periodGroup.getCheckedRadioButtonId();
            int sortId = sortGroup.getCheckedRadioButtonId();
            String sortBy = (sortId == R.id.sortProduct) ? "product" : "location";

            String startDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(startCalendar.getTime());

            String endDate;
            if (periodId == R.id.periodDay) {
                endDate = startDate;
            } else {
                int monthsToAdd;
                if (periodId == R.id.periodMonth) {
                    monthsToAdd = 1;
                } else if (periodId == R.id.period3Months) {
                    monthsToAdd = 3;
                } else {
                    monthsToAdd = 12;
                }
                Calendar endCal = (Calendar) startCalendar.clone();
                endCal.add(Calendar.MONTH, monthsToAdd);
                endCal.add(Calendar.DAY_OF_MONTH, -1);
                endDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(endCal.getTime());
            }
            generateReport(startDate, endDate, sortBy);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void generateReport(String startDate, String endDate, String sortBy) {
        ReportGenerator rg = new ReportGenerator(this, dbHelper);
        File pdf = rg.generateMovementReport(startDate, endDate, sortBy);
        if (pdf != null && pdf.exists()) {
            Uri uri = FileProvider.getUriForFile(this, "com.example.evrazinventarizasiya.fileprovider", pdf);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/pdf");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(Intent.createChooser(shareIntent, "Отправить отчёт"));
        } else {
            Toast.makeText(this, "Ошибка генерации отчёта", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dateTextView = findViewById(R.id.dateTextView);
        valuesSpinner = findViewById(R.id.valuesSpinner);
        Button continueButton = findViewById(R.id.continueButton);

        selectedDate = Calendar.getInstance();
        updateDateText();
        dateTextView.setOnClickListener(v -> showDatePickerDialog());

        dbHelper = new DatabaseHelper(this);
        loadSpinner();

        continueButton.setOnClickListener(v -> {
            Object selected = valuesSpinner.getSelectedItem();
            if (selected == null || !(selected instanceof DatabaseHelper.Location)) {
                Toast.makeText(this, "Выберите склад", Toast.LENGTH_SHORT).show();
                return;
            }
            long selectedStoreId = ((DatabaseHelper.Location) selected).id;
            showShelfPicker(selectedStoreId);
        });

        reportButton = findViewById(R.id.reportButton);
        reportButton.setOnClickListener(v -> showReportDialog());
    }

    private void showShelfPicker(long storeId) {
        List<DatabaseHelper.Location> shelves = dbHelper.getShelvesByLocation(storeId);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Выберите место");

        if (!shelves.isEmpty()) {
            String[] shelfNames = new String[shelves.size()];
            for (int i = 0; i < shelves.size(); i++) {
                shelfNames[i] = shelves.get(i).name;
            }
            builder.setItems(shelfNames, (dialog, which) -> {
                selectedLocationId = shelves.get(which).id;
                Intent intent = new Intent(MainActivity.this, ScannerActivity.class);
                intent.putExtra("locationId", selectedLocationId);
                scanLauncher.launch(intent);
            });
        } else {
            builder.setMessage("Для этого склада ещё нет полок. Добавьте новую.");
        }

        builder.setNeutralButton("➕ Добавить полку", (dialog, which) -> {
            AlertDialog.Builder inputBuilder = new AlertDialog.Builder(this);
            final EditText input = new EditText(this);
            input.setHint("Название (например: Стеллаж 1, Полка А, Ряд 2)");
            inputBuilder.setView(input);
            inputBuilder.setTitle("Новая полка");
            inputBuilder.setPositiveButton("Добавить", (d, w) -> {
                String shelfName = input.getText().toString().trim();
                if (shelfName.length() == 0) {
                    Toast.makeText(this, "Введите название полки", Toast.LENGTH_SHORT).show();
                    return;
                }
                long newId = dbHelper.addShelf(shelfName, storeId);
                if (newId != -1) {
                    Toast.makeText(this, "Полка \"" + shelfName + "\" добавлена", Toast.LENGTH_LONG).show();
                    showShelfPicker(storeId); // обновляем диалог
                } else {
                    Toast.makeText(this, "Ошибка добавления", Toast.LENGTH_SHORT).show();
                }
            });
            inputBuilder.setNegativeButton("Отмена", null);
            inputBuilder.show();
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void loadSpinner() {
        List<DatabaseHelper.Location> locations = dbHelper.getLocationsWithParent();
        if (locations.isEmpty()) {
            locations.add(new DatabaseHelper.Location(-1, "Нет доступных складов"));
        }
        ArrayAdapter<DatabaseHelper.Location> adapter = new ArrayAdapter<>(
                this, R.layout.spinner_item, locations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        valuesSpinner.setAdapter(adapter);
    }

    private void updateDateText() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        String formattedDate = sdf.format(selectedDate.getTime());
        dateTextView.setText("Дата инвентаризации: " + formattedDate);
    }

    private void showDatePickerDialog() {
        int year = selectedDate.get(Calendar.YEAR);
        int month = selectedDate.get(Calendar.MONTH);
        int day = selectedDate.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, y, m, d) -> {
                    selectedDate.set(y, m, d);
                    updateDateText();
                },
                year, month, day
        );
        dialog.show();
    }
}