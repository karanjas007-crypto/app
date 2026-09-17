package com.perioperative.planner

import org.junit.Assert.*
import org.junit.Test

class DecisionSupportTest {
    private fun patient(extra:Map<String,String> = emptyMap())=Case(fields=mapOf(
        "age" to "50","height" to "170","weight" to "65","procedure" to "Short peripheral / superficial surgery",
        "position" to "Supine","urgency" to "Elective","duration" to "45","osa" to "No","renal" to "No","liver" to "No",
        "airway.opening" to "4","airway.thyromental" to "7","airway.mallampati" to "I","airway.neck" to "Normal",
        "airway.jaw" to "Normal","airway.bite" to "I","airway.dentition" to "Normal","airway.previous" to "No",
        "airway.aspiration" to "No","airway.ventilation" to "No","airway.apnea" to "No","airway.rescue" to "No",
        "airway.highPressure" to "No","airway.lungIsolation" to "No","airway.shared" to "No",
        "ext.awake" to "Yes","ext.ventilation" to "Yes","ext.protection" to "Yes","ext.stable" to "Yes",
        "ext.nmb" to "Yes","ext.tof" to "0.9","ext.difficult" to "No","ext.edema" to "No","ext.support" to "No",
        "pain.opioid" to "None","pain.allergies" to "Yes","pain.paracetamolContra" to "No","pain.nsaidContra" to "No"
    )+extra)
    @Test fun incompleteAirwayCannotBecomeSgaCandidate(){
        assertTrue(DecisionSupport.airway(patient()).recommendation.contains("supraglottic"))
        val d=DecisionSupport.airway(patient(mapOf("airway.rescue" to "")))
        assertTrue(d.recommendation.startsWith("Complete"));assertTrue(d.missing.isNotEmpty())
        assertFalse(DecisionSupport.airway(patient(mapOf("age" to "8"))).recommendation.contains("supraglottic"))
    }
    @Test fun anatomyPlusPhysiologicRiskPromptsAwakeStrategy(){
        val d=DecisionSupport.airway(patient(mapOf("airway.mallampati" to "III","airway.apnea" to "Yes")))
        assertTrue(d.recommendation.startsWith("Favor an awake"))
        val limited=DecisionSupport.airway(patient(mapOf("airway.opening" to "2")))
        assertTrue(limited.recommendation.contains("flexible-scope"))
    }
    @Test fun surgeryAndObesityModifyAirwayChoice(){
        assertTrue(DecisionSupport.airway(patient(mapOf("procedure" to "CABG"))).recommendation.contains("tracheal tube"))
        val obese=DecisionSupport.airway(patient(mapOf("weight" to "130")))
        assertFalse(obese.recommendation.contains("supraglottic"));assertTrue(obese.actions.any{it.contains("head-up")})
        assertTrue("soba" in obese.sources)
    }
    @Test fun extubationTofBoundaryAndRespiratorySupport(){
        assertTrue(DecisionSupport.extubation(patient()).recommendation.startsWith("Awake extubation is reasonable"))
        assertTrue(DecisionSupport.extubation(patient(mapOf("ext.tof" to "0.899"))).recommendation.startsWith("Defer"))
        assertTrue(DecisionSupport.extubation(patient(mapOf("ext.tof" to ""))).recommendation.contains("not established"))
        assertTrue(DecisionSupport.extubation(patient(mapOf("ext.support" to "Yes"))).recommendation.startsWith("Defer"))
        assertTrue(DecisionSupport.extubation(patient(mapOf("osa" to ""))).recommendation.contains("not established"))
    }
    @Test fun difficultReintubationGeneratesStagedPlan(){
        val d=DecisionSupport.extubation(patient(mapOf("ext.difficult" to "Yes")))
        assertTrue(d.recommendation.contains("reintubation strategy"));assertTrue(d.actions.any{it.contains("airway-exchange")})
    }
    @Test fun painPathwayChangesWithOperation(){
        assertTrue(DecisionSupport.analgesia(patient(mapOf("procedure" to "Knee replacement"))).recommendation.contains("adductor-canal"))
        assertTrue(DecisionSupport.analgesia(patient(mapOf("procedure" to "Hip replacement"))).recommendation.contains("PENG"))
        val cardiac=DecisionSupport.analgesia(patient(mapOf("procedure" to "CABG")))
        assertTrue(cardiac.actions.any{it.startsWith("Omit routine NSAID")})
    }
    @Test fun opioidHistoryChangesPainPlanWithoutInventingConversions(){
        val c=patient(mapOf("procedure" to "Knee replacement","pain.opioid" to "Buprenorphine"))
        val d=DecisionSupport.analgesia(c)
        assertTrue(d.actions.any{it.contains("Do not routinely stop")});assertTrue(d.missing.any{it.contains("actual dose")})
        val chronic=DecisionSupport.analgesia(c.set("pain.opioid","Daily full agonist"))
        assertTrue(chronic.actions.any{it.contains("baseline therapy")})
        assertTrue(DecisionSupport.analgesia(c.set("pain.opioid","Naltrexone")).actions.any{it.contains("formulation and last dose")})
    }
    @Test fun contraindicationsAndUnknownOrganFunctionPreventRoutineSuggestion(){
        val d=DecisionSupport.analgesia(patient(mapOf("pain.nsaidContra" to "Yes","liver" to "")))
        assertTrue(d.actions.any{it.startsWith("Omit routine NSAID")})
        assertFalse(d.actions.any{it.startsWith("Use paracetamol")})
        assertTrue(DecisionSupport.analgesia(patient(mapOf("age" to "8"))).recommendation.startsWith("Adult age required"))
    }
    @Test fun everyGeneratedCitationResolves(){
        Content.procedures.forEach{p->DecisionSupport.opioidOptions.forEach{o->
            val c=patient(mapOf("procedure" to p,"pain.opioid" to o,"weight" to "130","anti.drug" to "Apixaban","osa" to "Yes"))
            listOf(DecisionSupport.airway(c),DecisionSupport.extubation(c),DecisionSupport.analgesia(c)).forEach{d->
                d.sources.forEach{assertTrue(it,Sources.all.containsKey(it))}
            }
        }}
    }
}
