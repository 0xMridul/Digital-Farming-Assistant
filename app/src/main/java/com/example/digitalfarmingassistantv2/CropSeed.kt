
package com.example.digitalfarmingassistantv2

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// Raise this number by 1 whenever you add or edit crops in CropSeed.kt.
private const val DB_VERSION = 1

// ---------------- Filter options shown in the Library ----------------
data class Opt(val code: String, val en: String, val bnText: String)

val CATEGORY_OPTS = listOf(
    Opt("", "All", "সব"),
    Opt("CEREAL", "Cereal", "শস্য"),
    Opt("VEG", "Vegetable", "সবজি"),
    Opt("PULSE", "Pulse", "ডাল"),
    Opt("OIL", "Oilseed", "তেলবীজ"),
    Opt("FRUIT", "Fruit", "ফল"),
    Opt("SPICE", "Spice", "মসলা"),
    Opt("CASH", "Cash crop", "অর্থকরী ফসল")
)

val SEASON_OPTS = listOf(
    Opt("", "Any season", "যেকোনো মৌসুম"),
    Opt("R", "Rabi (winter)", "রবি (শীত)"),
    Opt("K1", "Kharif-1 (summer)", "খরিফ-১ (গ্রীষ্ম)"),
    Opt("K2", "Kharif-2 (monsoon)", "খরিফ-২ (বর্ষা)")
)

val SOIL_OPTS = listOf(
    Opt("", "Any soil", "যেকোনো মাটি"),
    Opt("LOAM", "Loam", "দোআঁশ"),
    Opt("CLAY", "Clay", "এঁটেল"),
    Opt("SANDY", "Sandy", "বেলে"),
    Opt("SILT", "Silt / alluvial", "পলি")
)

fun optLabel(list: List<Opt>, code: String, b: Boolean): String {
    val o = list.firstOrNull { it.code == code } ?: return code
    return if (b) o.bnText else o.en
}

fun codesLabel(list: List<Opt>, csv: String, b: Boolean): String =
    csv.split(",").filter { it.isNotEmpty() }.joinToString(", ") { optLabel(list, it, b) }

fun CropInfo.passes(catCode: String, seasonCode: String, soilCode: String): Boolean {
    if (catCode.isNotEmpty() && this.category != catCode) return false
    if (seasonCode.isNotEmpty() && !("," + this.season + ",").contains("," + seasonCode + ",")) return false
    if (soilCode.isNotEmpty() && !("," + this.soil + ",").contains("," + soilCode + ",")) return false
    return true
}

// ---------------- SQLite database of crops ----------------
class CropDb(ctx: Context) : SQLiteOpenHelper(ctx, "crops.db", null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE crops (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "crop_key TEXT UNIQUE, category TEXT, season TEXT, soil TEXT, " +
                    "name_en TEXT, name_bn TEXT, " +
                    "growing_en TEXT, growing_bn TEXT, planting_en TEXT, planting_bn TEXT, " +
                    "fertilizer_en TEXT, fertilizer_bn TEXT, pests_en TEXT, pests_bn TEXT, " +
                    "diseases_en TEXT, diseases_bn TEXT, tips_en TEXT, tips_bn TEXT, " +
                    "search TEXT)"
        )
        db.execSQL("CREATE INDEX idx_crops_category ON crops(category)")
        db.beginTransaction()
        try {
            for (c in CROP_SEED) {
                val cv = ContentValues()
                cv.put("crop_key", c.name.en)
                cv.put("category", c.category)
                cv.put("season", c.season)
                cv.put("soil", c.soil)
                cv.put("name_en", c.name.en)
                cv.put("name_bn", c.name.bnText)
                cv.put("growing_en", c.growing.en)
                cv.put("growing_bn", c.growing.bnText)
                cv.put("planting_en", c.planting.en)
                cv.put("planting_bn", c.planting.bnText)
                cv.put("fertilizer_en", c.fertilizer.en)
                cv.put("fertilizer_bn", c.fertilizer.bnText)
                cv.put("pests_en", c.pests.en)
                cv.put("pests_bn", c.pests.bnText)
                cv.put("diseases_en", c.diseases.en)
                cv.put("diseases_bn", c.diseases.bnText)
                cv.put("tips_en", c.tips.en)
                cv.put("tips_bn", c.tips.bnText)
                cv.put(
                    "search",
                    listOf(
                        c.name.en, c.name.bnText, c.growing.en, c.growing.bnText,
                        c.planting.en, c.planting.bnText, c.fertilizer.en, c.fertilizer.bnText,
                        c.pests.en, c.pests.bnText, c.diseases.en, c.diseases.bnText,
                        c.tips.en, c.tips.bnText
                    ).joinToString(" ").lowercase()
                )
                db.insert("crops", null, cv)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS crops")
        onCreate(db)
    }

    fun count(): Int {
        val cur = readableDatabase.rawQuery("SELECT COUNT(*) FROM crops", null)
        return cur.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** Every word typed must appear somewhere in the crop's text (English or Bengali). */
    fun search(q: String, category: String, season: String, soil: String): List<CropInfo> {
        val sb = StringBuilder(
            "SELECT category, season, soil, name_en, name_bn, growing_en, growing_bn, " +
                    "planting_en, planting_bn, fertilizer_en, fertilizer_bn, pests_en, pests_bn, " +
                    "diseases_en, diseases_bn, tips_en, tips_bn FROM crops WHERE 1=1"
        )
        val args = ArrayList<String>()
        for (w in q.trim().split(" ")) {
            val word = w.replace("%", "").replace("_", "").lowercase()
            if (word.isNotEmpty()) {
                sb.append(" AND search LIKE ?")
                args.add("%" + word + "%")
            }
        }
        if (category.isNotEmpty()) {
            sb.append(" AND category = ?")
            args.add(category)
        }
        if (season.isNotEmpty()) {
            sb.append(" AND (',' || season || ',') LIKE ?")
            args.add("%," + season + ",%")
        }
        if (soil.isNotEmpty()) {
            sb.append(" AND (',' || soil || ',') LIKE ?")
            args.add("%," + soil + ",%")
        }
        sb.append(" ORDER BY name_en")

        val out = ArrayList<CropInfo>()
        val cur = readableDatabase.rawQuery(sb.toString(), args.toTypedArray())
        cur.use {
            while (it.moveToNext()) {
                out.add(
                    CropInfo(
                        Bi(it.getString(3), it.getString(4)),
                        Bi(it.getString(5), it.getString(6)),
                        Bi(it.getString(7), it.getString(8)),
                        Bi(it.getString(9), it.getString(10)),
                        Bi(it.getString(11), it.getString(12)),
                        Bi(it.getString(13), it.getString(14)),
                        Bi(it.getString(15), it.getString(16)),
                        it.getString(0), it.getString(1), it.getString(2)
                    )
                )
            }
        }
        return out
    }
}
