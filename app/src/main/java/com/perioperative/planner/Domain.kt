package com.perioperative.planner

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class Action(val id: String = UUID.randomUUID().toString(), val rule: String, val title: String,
    val text: String, val source: String, val revision: Int, val owner: String = "", val due: String = "",
    val trigger: String = "", val missing: String = "")
data class Case(val id: String = UUID.randomUUID().toString(), val fields: Map<String, String> = emptyMap(),
    val checks: Set<String> = emptySet(), val actions: List<Action> = emptyList(),
    val revision: Int = 0, val reviewed: Boolean = false) {
    fun v(k: String) = fields[k].orEmpty()
    fun display(k: String) = v(k).ifBlank { "Unknown" }
    fun yes(k: String) = v(k) == "Yes"
    fun no(k: String) = v(k) == "No"
    fun num(k: String, min: Double, max: Double) = v(k).toDoubleOrNull()?.takeIf { it.isFinite() && it in min..max }
    fun set(k: String, value: String): Case {
        if (v(k) == value) return this
        var values = fields + (k to value)
        if(k == "anti.drug") values = values + ("anti.regimen" to "")
        if (k in listOf("anti.drug", "anti.dose", "anti.indication", "anti.crcl", "anti.class"))
            values = values + ("anti.verified" to "") + ("anti.manualVerified" to "")
        return copy(fields = values, revision = revision + if (k.startsWith("plan.") || k.startsWith("recovery.")) 0 else 1, reviewed = false)
    }
}
data class Source(val name: String, val edition: String, val url: String)
object Sources {
    val all = mapOf(
        "asra" to Source("ASRA antithrombotic guideline", "5th edition, 2025", "https://rapm.bmj.com/lookup/doi/10.1136/rapm-2024-105766"),
        "asa" to Source("ASA difficult airway", "2022", "https://doi.org/10.1097/ALN.0000000000004002"),
        "dasAti" to Source("DAS awake tracheal intubation", "2020", "https://doi.org/10.1111/anae.14904"),
        "soba" to Source("SOBA airway management in obesity", "2025", "https://doi.org/10.1111/anae.16647"),
        "nmb" to Source("ASA neuromuscular blockade monitoring / antagonism", "2023", "https://csa-online.org/wp-content/uploads/2023/02/NMB23.pdf"),
        "prospectKnee" to Source("PROSPECT total knee arthroplasty", "2022", "https://pmc.ncbi.nlm.nih.gov/articles/PMC9891300/"),
        "prospectHip" to Source("PROSPECT total hip arthroplasty update", "2026", "https://doi.org/10.1111/anae.70299"),
        "prospectCS" to Source("PROSPECT elective caesarean analgesia", "2021", "https://doi.org/10.1111/anae.15339"),
        "prospectSternotomy" to Source("PROSPECT median sternotomy", "2023", "https://doi.org/10.1097/EJA.0000000000001881"),
        "erasPD" to Source("ERAS pancreatoduodenectomy", "2019 recommendations, published 2020", "https://doi.org/10.1007/s00268-020-05462-w"),
        "opioidConsensus" to Source("Faculty of Pain Medicine: Surgery and opioids", "2021", "https://www.cpoc.org.uk/sites/cpoc/files/documents/2021-03/surgery-and-opioids-2021.pdf"),
        "methadone" to Source("UKCPA perioperative methadone", "Handbook; accessed 17 Sep 2026", "https://periop-handbook.ukclinicalpharmacy.org/drug/methadone-2/"),
        "naltrexone" to Source("Vivitrol (depot naltrexone) prescribing information", "Jan 2026; pain management section 5.6", "https://labeling.alkermes.com/uspi_vivitrol.pdf"),
        "buprenorphine" to Source("Multisociety buprenorphine recommendations", "2021", "https://rapm.bmj.com/content/46/10/840"),
        "sasm" to Source("SASM OSA assessment", "2016", "https://pmc.ncbi.nlm.nih.gov/articles/PMC4956681/"),
        "stop" to Source("STOP-Bang screening", "Classic eight factors", "https://pubmed.ncbi.nlm.nih.gov/26378880/"),
        "ada" to Source("ADA hospital care", "2026, section 16", "https://pmc.ncbi.nlm.nih.gov/articles/PMC12690180/"),
        "aha" to Source("AHA/ACC cardiovascular assessment", "2024, noncardiac surgery", "https://professional.heart.org/-/media/PHD-Files-2/Science-News/2/2024/2024-Guideline-for-Perioperative-Cardiovascular-Management-slide-set.pdf"),
        "nice" to Source("NICE preoperative testing", "NG45, elective adult scope", "https://www.ncbi.nlm.nih.gov/books/NBK367919/"),
        "mh" to Source("MHAUS", "Clinical resources", "https://www.mhaus.org/healthcare-professionals/"),
        "pbw" to Source("ARDS Network", "Adult predicted body weight equation", "https://www.ardsnet.org/files/ventilator_protocol_2008-07.pdf"),
        "regional" to Source("PROSPECT", "Procedure-specific analgesia", "https://esraeurope.org/prospect/"),
        "local" to Source("Clinical planning synthesis", "Local / operation-specific pathway required", "")
    )
    fun get(k: String) = all[k] ?: all.getValue("local")
}
data class Score(val positive: Int, val missing: Int, val total: Int) {
    val text get() = if (missing == 0) "$positive / $total" else "$positive–${positive + missing} / $total · $missing unknown"
}
data class Advice(val id: String, val section: String, val title: String, val trigger: String,
    val pre: String, val intra: String, val post: String, val source: String, val missing: String = "") {
    val text get() = "Preoperative: $pre\n\nIntraoperative: $intra\n\nPostoperative: $post"
}
data class Timing(val title: String, val state: String, val earliest: Instant? = null, val detail: String = "")
data class Timeline(val steps: List<Timing>, val missing: List<String>, val source: String)

object Engine {
    val stop = listOf("stop.snore", "stop.tired", "stop.observed", "stop.pressure", "stop.bmi", "stop.age", "stop.neck", "stop.male")
    val rcri = listOf("rcri.surgery", "rcri.ihd", "rcri.hf", "rcri.stroke", "rcri.insulin", "rcri.creatinine")
    fun score(c: Case, keys: List<String>) = Score(keys.count { c.yes(it) }, keys.count { c.v(it) !in listOf("Yes", "No") }, keys.size)
    fun rcriEligible(c: Case) = c.num("age", 18.0, 120.0) != null && c.v("procedure") !in listOf("", "CABG", "Liver transplantation", "Cesarean delivery", "Trauma")
    fun osa(c: Case): String {
        val s = score(c, stop)
        if (s.missing != 0) return "Incomplete screening"
        val combination = stop.take(4).count { c.yes(it) } >= 2 && stop.takeLast(4).filter { it != "stop.age" }.any { c.yes(it) }
        return when { s.positive >= 5 || combination -> "High screening risk"; s.positive >= 3 -> "Intermediate screening risk"; else -> "Low screening risk" }
    }
    fun bmi(c: Case): Double? {
        val h = c.num("height", 100.0, 230.0) ?: return null
        return c.num("weight", 25.0, 400.0)?.div((h/100)*(h/100))
    }
    fun pbw(c: Case): Double? {
        c.num("age", 18.0, 120.0) ?: return null
        val h = c.num("height", 100.0, 230.0) ?: return null
        val b = when(c.v("pbwSex")) { "Male" -> 50.0; "Female" -> 45.5; else -> return null }
        return (b + 0.91*(h-152.4)).takeIf { it > 0 }
    }
    fun volume(dose: Double?, concentration: Double?): Double? =
        if (dose == null || concentration == null || !dose.isFinite() || !concentration.isFinite() || dose <= 0 || concentration <= 0) null
        else (dose/concentration).takeIf { it.isFinite() }
    fun time(s: String): Instant? = try { OffsetDateTime.parse(s.trim()).toInstant() } catch (_: Exception) { null }
    fun stamp(i: Instant) = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm XXX").withZone(ZoneId.systemDefault()).format(i)
    fun timeline(c: Case): Timeline = Anticoagulation.evaluate(c)
}
