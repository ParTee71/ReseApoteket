package se.partee71.reseapoteket.core.engine

import se.partee71.reseapoteket.core.model.ItemUnit
import se.partee71.reseapoteket.core.model.PackingSection

/**
 * Svenska enhetsnamn och pluralböjning – en gång, för alla formatterare (skill packing-engine).
 */
object UnitNames {
    private data class Noun(val one: String, val many: String)

    private val units = mapOf(
        ItemUnit.TABLETT to Noun("tablett", "tabletter"),
        ItemUnit.KAPSEL to Noun("kapsel", "kapslar"),
        ItemUnit.ML to Noun("ml", "ml"),
        ItemUnit.DOS to Noun("dos", "doser"),
        ItemUnit.PUFF to Noun("puff", "puffar"),
        ItemUnit.ST to Noun("st", "st"),
        ItemUnit.TUB to Noun("tub", "tuber")
    )

    /** Vad en förpackning av enheten kallas ("karta à 10 tabletter"). */
    private val packages = mapOf(
        ItemUnit.TABLETT to Noun("karta", "kartor"),
        ItemUnit.KAPSEL to Noun("karta", "kartor"),
        ItemUnit.ML to Noun("flaska", "flaskor")
    )
    private val defaultPackage = Noun("förpackning", "förpackningar")

    private val days = Noun("dag", "dagar")
    private val travellers = Noun("resenär", "resenärer")
    private val things = Noun("sak", "saker")
    private val medicines = Noun("recept", "recept")
    private val supplements = Noun("kosttillskott", "kosttillskott")
    private val familyItems = Noun("familjeartikel", "familjeartiklar")

    private fun Noun.of(count: Int) = if (count == 1) one else many

    /** Enhetens namn för ett antal: "tablett", "tabletter" – också etiketten när enheten väljs. */
    fun name(count: Int, unit: ItemUnit): String = units.getValue(unit).of(count)

    /** "1 tablett", "18 tabletter". */
    fun quantity(count: Int, unit: ItemUnit): String = "$count ${name(count, unit)}"

    /** "2 kartor". */
    fun packages(count: Int, unit: ItemUnit): String = "$count ${(packages[unit] ?: defaultPackage).of(count)}"

    fun days(count: Int): String = "$count ${days.of(count)}"

    /** "per dag", "per 7 dagar". */
    fun perDays(count: Int): String = if (count <= 1) "per dag" else "per ${days(count)}"

    /** Mängd i en regel: "1 tub", men bara "1" för styck – "1 per resenär" läses bättre än "1 st per resenär". */
    fun amount(count: Int, unit: ItemUnit): String = if (unit == ItemUnit.ST) "$count" else quantity(count, unit)

    fun travellers(count: Int): String = "$count ${travellers.of(count)}"

    /** "16 saker", "1 sak". */
    fun things(count: Int): String = "$count ${things.of(count)}"

    /** Packlistans rader per sektion: "6 recept", "3 kosttillskott", "1 familjeartikel". */
    fun section(count: Int, section: PackingSection): String = "$count " + when (section) {
        PackingSection.MEDICINE -> medicines
        PackingSection.SUPPLEMENT -> supplements
        PackingSection.FAMILY -> familyItems
    }.of(count)

    /** Svenska ordningstal: 1:a, 2:a, 3:e … 11:e, 12:e … 21:a, 22:a. */
    fun ordinal(n: Int): String {
        val lastTwo = n % 100
        val suffix = if (lastTwo in 11..12) {
            "e"
        } else if (n % 10 in 1..2) {
            "a"
        } else {
            "e"
        }
        return "$n:$suffix"
    }
}
