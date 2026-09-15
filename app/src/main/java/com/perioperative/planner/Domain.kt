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
        "sasm" to Source("SASM OSA assessment", "2016", "https://pmc.ncbi.nlm.nih.gov/articles/PMC4956681/"),
        "stop" to Source("STOP-Bang screening", "Classic eight factors", "https://pubmed.ncbi.nlm.nih.gov/26378880/"),
        "ada" to Source("ADA hospital care", "2026, section 16", "https://pmc.ncbi.nlm.nih.gov/articles/PMC12690180/"),
        "aha" to Source("AHA/ACC cardiovascular assessment", "2024, noncardiac surgery", "https://professional.heart.org/-/media/PHD-Files-2/Science-News/2/2024/2024-Guideline-for-Perioperative-Cardiovascular-Management-slide-set.pdf"),
        "nice" to Source("NICE preoperative testing", "NG45, elective adult scope", "https://www.ncbi.nlm.nih.gov/books/NBK367919/"),
        "mh" to Source("MHAUS", "Clinical resources", "https://www.mhaus.org/healthcare-professionals/"),
        "pbw" to Source("ARDS Network", "Adult predicted body weight equation", "https://www.ardsnet.org/files/ventilator_protocol_2008-07.pdf"),
        "regional" to Source("PROSPECT", "Procedure-specific analgesia", "https://esraeurope.org/prospect/"),
        "local" to Source("Planning prompt", "Local protocol required", "")
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
    fun timeline(c: Case): Timeline {
        val missing = mutableListOf<String>()
        fun requireInput(ok: Boolean, label: String) { if (!ok) missing += label }
        val last = time(c.v("anti.last")); val insert = time(c.v("anti.insertion"))
        val removal = time(c.v("anti.removal")); val planned = time(c.v("anti.restart"))
        val surgical = time(c.v("anti.surgical")); val catheter = c.yes("anti.catheter")
        val exposed = catheter && c.yes("anti.exposed")
        val auto = c.v("anti.drug") == "Apixaban"
        requireInput(c.v("anti.drug").isNotBlank(), "Medication")
        requireInput(c.v("anti.dose").isNotBlank(), "Dose and frequency")
        requireInput(c.v("anti.indication").isNotBlank(), "Indication")
        requireInput(last != null, "Last administration with UTC offset")
        requireInput(c.num("anti.crcl", 1.0, 200.0) != null, "Creatinine clearance (mL/min)")
        requireInput(c.no("anti.other"), "Additional antithrombotics require individual review")
        requireInput(c.no("anti.bleeding"), "Active bleeding status")
        requireInput(c.yes("anti.thrombosis"), "Thrombosis/interruption review")
        requireInput(c.v("anti.catheter") in listOf("Yes", "No"), "Catheter status")
        val hold: Long?; val after: Long?
        if (auto) {
            hold = when(c.v("anti.class")) { "Low" -> 36; "High" -> 72; else -> null }
            after = when(c.v("anti.class")) { "Low" -> 6; "High" -> 24; else -> null }
            requireInput(hold != null && c.yes("anti.verified"), "ASRA dose class verified against indication/dose/renal table")
            requireInput((c.num("anti.crcl", 1.0, 200.0) ?: 0.0) >= 30, "CrCl <30 or unknown: outside automatic scope")
        } else {
            hold = c.num("anti.hold", 0.0, 720.0)?.takeIf { it % 1 == 0.0 }?.toLong()
            after = c.num("anti.after", 0.0, 720.0)?.takeIf { it % 1 == 0.0 }?.toLong()
            requireInput(hold != null && after != null && c.v("anti.source").isNotBlank() && c.yes("anti.manualVerified"),
                "Verified drug-specific insertion/restart intervals, source and edition")
        }
        fun status(t: Instant?, lower: Instant?) = when { lower == null -> "Cannot determine"; t == null -> "Event time needed"; t < lower -> "Timing criterion not met"; else -> "Timing criterion met" }
        val baseReady = missing.isEmpty()
        val lower = if (baseReady && last != null && hold != null) last.plusSeconds(hold*3600) else null
        val insertion = if (exposed) Timing("Insertion", "Historical assessment needed", detail = "The last dose was given with a catheter present; verify pre-insertion history separately.")
            else Timing("Insertion", status(insert, lower), lower, "One time criterion only; it does not establish neuraxial suitability.")
        val removeInterval = if (auto) hold else c.num("anti.removeHours", 0.0, 720.0)?.takeIf { it % 1 == 0.0 }?.toLong()
        val exposureOrder = exposed && last != null && insert != null && last >= insert
        val removeLower = if (baseReady && exposureOrder && removeInterval != null) last!!.plusSeconds(removeInterval*3600) else null
        val removeStep = when {
            c.no("anti.catheter") -> Timing("Catheter removal", "Not applicable")
            !catheter -> Timing("Catheter removal", "Unknown")
            exposed -> Timing("Catheter removal", status(removal, removeLower), removeLower, "Unanticipated exposure branch; assay alternatives and combined agents need specialist review.")
            c.no("anti.exposed") -> Timing("Catheter removal", "Clinical review required", detail = "Confirm uninterrupted withholding and current hemostasis; specify removal time.")
            else -> Timing("Catheter removal", "Exposure history unknown")
        }
        val event = if (catheter) removal else insert
        val order = insert != null && (!catheter || removal != null && removal >= insert) &&
            (if (exposed) exposureOrder && removeLower != null && removal != null && removal >= removeLower
             else lower != null && insert >= lower)
        var restart = if (baseReady && order && event != null && after != null && surgical != null &&
            c.yes("anti.hemostasis") && c.v("anti.traumatic") in listOf("Yes", "No") &&
            (!catheter || c.v("anti.exposed") in listOf("Yes", "No"))) maxOf(event.plusSeconds(after*3600), surgical) else null
        if (restart != null && c.yes("anti.traumatic")) restart =
            if (auto) maxOf(restart, insert!!.plusSeconds(48*3600)) else null
        requireInput(insert != null, "Insertion date/time")
        if (catheter) requireInput(removal != null, "Removal date/time")
        requireInput(surgical != null, "Restart time allowed by surgical/bleeding plan")
        requireInput(c.yes("anti.hemostasis"), "Restart: hemostasis confirmation")
        requireInput(c.v("anti.traumatic") in listOf("Yes", "No"), "Traumatic puncture status")
        requireInput(planned != null, "Proposed restart date/time")
        if (insert != null) requireInput(order, "Review event order and insertion/removal timing")
        return Timeline(listOf(insertion, removeStep, Timing("Subsequent medication", status(planned, restart), restart,
            "Neuraxial minimum plus documented surgical restrictions. A timestamp is not a prescription.")), missing.distinct(), if(auto) "asra" else "local")
    }
}
