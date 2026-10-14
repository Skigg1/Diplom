package com.example.evrazinventarizasiya;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DatabaseHelper";
    private static final String DB_NAME = "BD.db";
    private static final int DB_VERSION = 9;
    private final Context context;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
        copyDatabaseIfNeeded();
    }

    private void copyDatabaseIfNeeded() {
        File dbFile = context.getDatabasePath(DB_NAME);
        if (!dbFile.exists()) {
            Log.d(TAG, "Копирование БД из assets...");
            try (InputStream is = context.getAssets().open(DB_NAME);
                 OutputStream os = new FileOutputStream(dbFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
                Log.d(TAG, "БД скопирована");
            } catch (IOException e) {
                Log.e(TAG, "Ошибка копирования", e);
            }
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
        insertInitialData(db);
    }

    private void createTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS Места_хранения (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "Название TEXT NOT NULL," +
                "РодительскоеID INTEGER)");

        db.execSQL("CREATE TABLE IF NOT EXISTS Продукция (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "Наименование TEXT NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS Марки (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "Название TEXT NOT NULL)");

        db.execSQL("CREATE TABLE IF NOT EXISTS Пакеты (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ПродукцияID INTEGER NOT NULL," +
                "МаркаID INTEGER NOT NULL," +
                "Длина REAL CHECK(Длина > 0)," +
                "Вес REAL CHECK(Вес >= 0)," +
                "Количество INTEGER DEFAULT 1," +
                "UNIQUE(ПродукцияID, МаркаID, Длина, Вес))");

        db.execSQL("CREATE TABLE IF NOT EXISTS Инвентаризация (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ПакетID INTEGER NOT NULL," +
                "Статус TEXT," +
                "ДатаСоздания TEXT DEFAULT CURRENT_DATE," +
                "FOREIGN KEY(ПакетID) REFERENCES Пакеты(ID))");

        db.execSQL("CREATE TABLE IF NOT EXISTS Инвентаризаторы (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ФИО TEXT NOT NULL UNIQUE)");

        db.execSQL("CREATE TABLE IF NOT EXISTS ИнвентаризацияСессии (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ИнвентаризацияID INTEGER NOT NULL," +
                "ДатаИнвентаризации TEXT NOT NULL," +
                "МестоID INTEGER NOT NULL," +
                "FOREIGN KEY(ИнвентаризацияID) REFERENCES Инвентаризация(ID) ON DELETE CASCADE," +
                "FOREIGN KEY(МестоID) REFERENCES Места_хранения(ID))");

        db.execSQL("CREATE TABLE IF NOT EXISTS ИнвентаризацияСессииУчастники (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                "СессияID INTEGER NOT NULL," +
                "ИнвентаризаторID INTEGER NOT NULL," +
                "FOREIGN KEY(СессияID) REFERENCES ИнвентаризацияСессии(ID) ON DELETE CASCADE," +
                "FOREIGN KEY(ИнвентаризаторID) REFERENCES Инвентаризаторы(ID) ON DELETE CASCADE)");
    }

    private void insertInitialData(SQLiteDatabase db) {
        db.execSQL("INSERT OR IGNORE INTO Продукция (Наименование) VALUES ('Деталь корпусная')");
        db.execSQL("INSERT OR IGNORE INTO Продукция (Наименование) VALUES ('Шестерня')");

        db.execSQL("INSERT OR IGNORE INTO Марки (Название) VALUES ('Сталь 45')");
        db.execSQL("INSERT OR IGNORE INTO Марки (Название) VALUES ('Алюминий АМг6')");

        db.execSQL("INSERT OR IGNORE INTO Пакеты (ПродукцияID, МаркаID, Длина, Вес, Количество) VALUES (1,1,12.0,15,1)");
        db.execSQL("INSERT OR IGNORE INTO Пакеты (ПродукцияID, МаркаID, Длина, Вес, Количество) VALUES (2,2,15.0,20,1)");

        db.execSQL("INSERT OR IGNORE INTO Инвентаризаторы (ФИО) VALUES ('Неизвестный')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 9) {
            try {
                db.execSQL("ALTER TABLE Пакеты ADD COLUMN Количество INTEGER DEFAULT 1");
                Log.d(TAG, "Column Количество added");
            } catch (Exception e) {
                Log.d(TAG, "Column Количество already exists");
            }
        }
        if (oldVersion < 8) {
            db.execSQL("CREATE TABLE IF NOT EXISTS Инвентаризаторы (" +
                    "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "ФИО TEXT NOT NULL UNIQUE)");
            db.execSQL("CREATE TABLE IF NOT EXISTS ИнвентаризацияСессии (" +
                    "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "ИнвентаризацияID INTEGER NOT NULL," +
                    "ДатаИнвентаризации TEXT NOT NULL," +
                    "FOREIGN KEY(ИнвентаризацияID) REFERENCES Инвентаризация(ID) ON DELETE CASCADE)");
            db.execSQL("CREATE TABLE IF NOT EXISTS ИнвентаризацияСессииУчастники (" +
                    "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "СессияID INTEGER NOT NULL," +
                    "ИнвентаризаторID INTEGER NOT NULL," +
                    "FOREIGN KEY(СессияID) REFERENCES ИнвентаризацияСессии(ID) ON DELETE CASCADE," +
                    "FOREIGN KEY(ИнвентаризаторID) REFERENCES Инвентаризаторы(ID) ON DELETE CASCADE)");

            db.execSQL("INSERT OR IGNORE INTO Инвентаризаторы (ФИО) VALUES ('Неизвестный')");

            try {
                db.execSQL("ALTER TABLE ИнвентаризацияСессии ADD COLUMN МестоID INTEGER REFERENCES Места_хранения(ID)");
            } catch (Exception e) {
                Log.d(TAG, "Column МестоID already exists");
            }

            Cursor checkColInv = db.rawQuery("PRAGMA table_info(Инвентаризация)", null);
            boolean hasLocationInInv = false;
            while (checkColInv.moveToNext()) {
                if ("МестоID".equals(checkColInv.getString(1))) {
                    hasLocationInInv = true;
                    break;
                }
            }
            checkColInv.close();

            if (hasLocationInInv) {
                db.execSQL("UPDATE ИнвентаризацияСессии SET МестоID = " +
                        "(SELECT МестоID FROM Инвентаризация WHERE Инвентаризация.ID = ИнвентаризацияСессии.ИнвентаризацияID)");
            }

            db.execSQL("CREATE TABLE Инвентаризация_new (" +
                    "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "ПакетID INTEGER NOT NULL," +
                    "Статус TEXT," +
                    "ДатаСоздания TEXT DEFAULT CURRENT_DATE," +
                    "FOREIGN KEY(ПакетID) REFERENCES Пакеты(ID))");
            db.execSQL("INSERT INTO Инвентаризация_new (ID, ПакетID, Статус, ДатаСоздания) " +
                    "SELECT ID, ПакетID, Статус, ДатаСоздания FROM Инвентаризация");
            db.execSQL("DROP TABLE Инвентаризация");
            db.execSQL("ALTER TABLE Инвентаризация_new RENAME TO Инвентаризация");
        }
        if (oldVersion < 5) {
            db.execSQL("DROP TABLE IF EXISTS Пакеты");
            db.execSQL("CREATE TABLE Пакеты (" +
                    "ID INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "ПродукцияID INTEGER NOT NULL," +
                    "МаркаID INTEGER NOT NULL," +
                    "Длина REAL CHECK(Длина > 0)," +
                    "Вес REAL CHECK(Вес >= 0)," +
                    "UNIQUE(ПродукцияID, МаркаID, Длина, Вес))");
            db.execSQL("INSERT OR IGNORE INTO Пакеты (ПродукцияID, МаркаID, Длина, Вес) VALUES (1,1,12.0,15)");
            db.execSQL("INSERT OR IGNORE INTO Пакеты (ПродукцияID, МаркаID, Длина, Вес) VALUES (2,2,15.0,20)");
        }
    }

    // работа с полками
    public List<Location> getShelvesByLocation(long parentLocationId) {
        List<Location> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT ID, Название FROM Места_хранения WHERE РодительскоеID = ? ORDER BY Название",
                new String[]{String.valueOf(parentLocationId)});
        while (c.moveToNext()) {
            list.add(new Location(c.getLong(0), c.getString(1)));
        }
        c.close();
        db.close();
        return list;
    }

    public long addShelf(String name, long parentId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("Название", name);
        cv.put("РодительскоеID", parentId);
        long id = db.insert("Места_хранения", null, cv);
        db.close();
        return id;
    }

    public String getFullLocationPath(long locationId) {
        if (locationId == -1) return "не выбрано";
        SQLiteDatabase db = getReadableDatabase();
        String path = "";
        long currentId = locationId;
        while (currentId != -1) {
            Cursor c = db.rawQuery("SELECT Название, РодительскоеID FROM Места_хранения WHERE ID = ?",
                    new String[]{String.valueOf(currentId)});
            if (c.moveToFirst()) {
                String name = c.getString(0);
                long parentId = c.getLong(1);
                path = (path.isEmpty()) ? name : name + " / " + path;
                currentId = parentId;
            } else {
                currentId = -1;
            }
            c.close();
        }
        db.close();
        return path;
    }

    // инвентаризаторы
    public List<Inventor> getAllInventors() {
        List<Inventor> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT ID, ФИО FROM Инвентаризаторы ORDER BY ФИО", null);
        while (c.moveToNext()) {
            list.add(new Inventor(c.getLong(0), c.getString(1)));
        }
        c.close();
        db.close();
        return list;
    }

    public long addInventor(String fio) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("ФИО", fio);
        long id = db.insertWithOnConflict("Инвентаризаторы", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        if (id == -1) {
            Cursor c = db.rawQuery("SELECT ID FROM Инвентаризаторы WHERE ФИО = ?", new String[]{fio});
            if (c.moveToFirst()) id = c.getLong(0);
            c.close();
        }
        db.close();
        return id;
    }

    // инвентаризации
    public long addInventory(long packageId, long locationId, String status, String inventoryDate, List<Long> inventorIds) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues invVals = new ContentValues();
            invVals.put("ПакетID", packageId);
            invVals.put("Статус", status);
            long invId = db.insert("Инвентаризация", null, invVals);
            if (invId == -1) return -1;

            ContentValues sessVals = new ContentValues();
            sessVals.put("ИнвентаризацияID", invId);
            sessVals.put("ДатаИнвентаризации", inventoryDate);
            sessVals.put("МестоID", locationId);
            long sessId = db.insert("ИнвентаризацияСессии", null, sessVals);
            if (sessId == -1) return -1;

            if (inventorIds != null && !inventorIds.isEmpty()) {
                for (long invIdd : inventorIds) {
                    ContentValues partVals = new ContentValues();
                    partVals.put("СессияID", sessId);
                    partVals.put("ИнвентаризаторID", invIdd);
                    db.insert("ИнвентаризацияСессииУчастники", null, partVals);
                }
            } else {
                long unknown = getUnknownInventorId(db);
                if (unknown != -1) {
                    ContentValues partVals = new ContentValues();
                    partVals.put("СессияID", sessId);
                    partVals.put("ИнвентаризаторID", unknown);
                    db.insert("ИнвентаризацияСессииУчастники", null, partVals);
                }
            }
            db.setTransactionSuccessful();
            return invId;
        } catch (Exception e) {
            Log.e(TAG, "addInventory error", e);
            return -1;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public boolean updateInventory(long inventoryId, String newStatus, String newDate, long newLocationId, List<Long> inventorIds) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (newStatus != null) {
                ContentValues cv = new ContentValues();
                cv.put("Статус", newStatus);
                db.update("Инвентаризация", cv, "ID = ?", new String[]{String.valueOf(inventoryId)});
            }

            String sessionDate;
            if (newDate != null && !newDate.isEmpty()) {
                sessionDate = newDate;
            } else {
                sessionDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            }

            long finalLocationId = newLocationId;
            if (finalLocationId == -1) {
                finalLocationId = getAnyLocationId(db);
            }

            ContentValues sessVals = new ContentValues();
            sessVals.put("ИнвентаризацияID", inventoryId);
            sessVals.put("ДатаИнвентаризации", sessionDate);
            sessVals.put("МестоID", finalLocationId);
            long sessId = db.insert("ИнвентаризацияСессии", null, sessVals);
            if (sessId == -1) {
                throw new Exception("Failed to create session");
            }

            if (inventorIds != null && !inventorIds.isEmpty()) {
                for (long invId : inventorIds) {
                    ContentValues partVals = new ContentValues();
                    partVals.put("СессияID", sessId);
                    partVals.put("ИнвентаризаторID", invId);
                    db.insert("ИнвентаризацияСессииУчастники", null, partVals);
                }
            } else {
                long unknown = getUnknownInventorId(db);
                if (unknown != -1) {
                    ContentValues partVals = new ContentValues();
                    partVals.put("СессияID", sessId);
                    partVals.put("ИнвентаризаторID", unknown);
                    db.insert("ИнвентаризацияСессииУчастники", null, partVals);
                }
            }

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "updateInventory error", e);
            return false;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    private long getUnknownInventorId(SQLiteDatabase db) {
        long id = -1;
        Cursor c = db.rawQuery("SELECT ID FROM Инвентаризаторы WHERE ФИО = 'Неизвестный'", null);
        if (c.moveToFirst()) id = c.getLong(0);
        c.close();
        return id;
    }

    private long getAnyLocationId(SQLiteDatabase db) {
        long id = -1;
        Cursor c = db.rawQuery("SELECT ID FROM Места_хранения WHERE РодительскоеID IS NOT NULL LIMIT 1", null);
        if (c.moveToFirst()) id = c.getLong(0);
        c.close();
        return id;
    }

    public Cursor getLastInventoryWithPeople(long packageId) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT inv.ID as _id, inv.ПакетID, inv.Статус as status, inv.ДатаСоздания as creation_date, " +
                "sess.ID as session_id, sess.ДатаИнвентаризации as inventory_date, sess.МестоID as location_id, " +
                "мх.Название as location_name, " +
                "GROUP_CONCAT(inf.ФИО, ', ') as inventors " +
                "FROM Инвентаризация inv " +
                "LEFT JOIN ИнвентаризацияСессии sess ON inv.ID = sess.ИнвентаризацияID " +
                "LEFT JOIN Места_хранения мх ON sess.МестоID = мх.ID " +
                "LEFT JOIN ИнвентаризацияСессииУчастники part ON sess.ID = part.СессияID " +
                "LEFT JOIN Инвентаризаторы inf ON part.ИнвентаризаторID = inf.ID " +
                "WHERE inv.ПакетID = ? " +
                "GROUP BY sess.ID " +
                "ORDER BY sess.ДатаИнвентаризации DESC LIMIT 1";
        return db.rawQuery(sql, new String[]{String.valueOf(packageId)});
    }
    public Cursor getLastTwoInventoryHistoryWithPeople(long packageId) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT inv.ID, inv.Статус as status, " +
                "sess.ДатаИнвентаризации as inventory_date, sess.МестоID, " +
                "мх.Название as location_name, " +
                "GROUP_CONCAT(inf.ФИО, ', ') as inventors " +
                "FROM Инвентаризация inv " +
                "JOIN ИнвентаризацияСессии sess ON inv.ID = sess.ИнвентаризацияID " +
                "LEFT JOIN Места_хранения мх ON sess.МестоID = мх.ID " +
                "LEFT JOIN ИнвентаризацияСессииУчастники part ON sess.ID = part.СессияID " +
                "LEFT JOIN Инвентаризаторы inf ON part.ИнвентаризаторID = inf.ID " +
                "WHERE inv.ПакетID = ? " +
                "GROUP BY sess.ID " +
                "ORDER BY sess.ДатаИнвентаризации DESC " +
                "LIMIT 2";
        return db.rawQuery(sql, new String[]{String.valueOf(packageId)});
    }

    public List<Location> getLocationsWithParent() {
        List<Location> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT ID, Название FROM Места_хранения " +
                "WHERE РодительскоеID IN (SELECT ID FROM Места_хранения WHERE РодительскоеID IS NULL) " +
                "ORDER BY Название";
        Cursor c = db.rawQuery(sql, null);
        if (c.moveToFirst()) {
            do {
                list.add(new Location(c.getLong(0), c.getString(1)));
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return list;
    }

    public List<Location> getCehs() {
        List<Location> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT ID, Название FROM Места_хранения WHERE РодительскоеID IS NULL ORDER BY Название";
        Cursor c = db.rawQuery(sql, null);
        if (c.moveToFirst()) {
            do {
                list.add(new Location(c.getLong(0), c.getString(1)));
            } while (c.moveToNext());
        }
        c.close();
        db.close();
        return list;
    }

    // пакеты
    public Cursor findPackage(long productId, long brandId, double length, double weight) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT ID, ПродукцияID, МаркаID, Длина, Вес FROM Пакеты " +
                "WHERE ПродукцияID = ? AND МаркаID = ? " +
                "AND ABS(Длина - ?) < 0.0001 AND ABS(Вес - ?) < 0.0001";
        return db.rawQuery(sql, new String[]{
                String.valueOf(productId), String.valueOf(brandId),
                String.valueOf(length), String.valueOf(weight)
        });
    }

    public Cursor getPackageById(long packageId) {
        SQLiteDatabase db = getReadableDatabase();
        String sql = "SELECT " +
                "п.ID as _id, " +
                "пр.Наименование as product_name, " +
                "м.Название as brand_name, " +
                "п.Длина as length, " +
                "п.Вес as weight, " +
                "п.Количество as quantity " +
                "FROM Пакеты п " +
                "JOIN Продукция пр ON п.ПродукцияID = пр.ID " +
                "JOIN Марки м ON п.МаркаID = м.ID " +
                "WHERE п.ID = ?";
        return db.rawQuery(sql, new String[]{String.valueOf(packageId)});
    }

    public long addPackage(long productId, long brandId, double length, double weight, int quantity) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("ПродукцияID", productId);
        values.put("МаркаID", brandId);
        values.put("Длина", length);
        values.put("Вес", weight);
        values.put("Количество", quantity);
        long id = db.insertWithOnConflict("Пакеты", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (id == -1) {
            Cursor c = db.rawQuery("SELECT ID FROM Пакеты WHERE ПродукцияID=? AND МаркаID=? AND Длина=? AND Вес=?",
                    new String[]{String.valueOf(productId), String.valueOf(brandId),
                            String.valueOf(length), String.valueOf(weight)});
            if (c.moveToFirst()) id = c.getLong(0);
            c.close();
        }
        db.close();
        return id;
    }


    public long addPackage(long productId, long brandId, double length, double weight) {
        return addPackage(productId, brandId, length, weight, 1);
    }

    public boolean updatePackageQuantity(long packageId, int quantity) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("Количество", quantity);
        int rows = db.update("Пакеты", cv, "ID = ?", new String[]{String.valueOf(packageId)});
        db.close();
        return rows > 0;
    }


    public static class Location {
        public long id;
        public String name;
        public Location(long id, String name) {
            this.id = id;
            this.name = name;
        }
        @Override
        public String toString() { return name; }
    }

    public static class Inventor {
        public long id;
        public String name;
        public Inventor(long id, String name) {
            this.id = id;
            this.name = name;
        }
        @Override
        public String toString() { return name; }
    }
}