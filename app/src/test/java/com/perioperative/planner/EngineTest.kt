package com.perioperative.planner

import org.junit.Assert.*
import org.junit.Test

class EngineTest {
    private fun ac(extra:Map<String,String> = emptyMap())=Case(fields=mapOf(
        "anti.drug" to "Apixaban","anti.dose" to "2.5 mg twice daily","anti.indication" to "VTE prophylaxis",
        "anti.class" to "Low","anti.verified" to "Yes","anti.crcl" to "80","anti.other" to "No",
        "anti.bleeding" to "No","anti.thrombosis" to "Yes","anti.last" to "2026-09-01T08:00Z",
        "anti.insertion" to "2026-09-02T20:00Z","anti.catheter" to "No","anti.traumatic" to "No",
        "anti.hemostasis" to "Yes","anti.surgical" to "2026-09-02T20:00Z","anti.restart" to "2026-09-03T02:00Z"
    )+extra)
    @Test fun unknownIsNotNegative(){
        val s=Engine.score(Case(),Engine.rcri)
        assertEquals(6,s.missing);assertEquals(0,s.positive)
        assertEquals("Incomplete screening",Engine.osa(Case()))
        assertNull(Engine.pbw(Case()))
    }
    @Test fun incompleteOsaDoesNotBecomeLowRisk(){
        val c=Case(fields=Engine.stop.take(7).associateWith{"No"})
        assertEquals("Incomplete screening",Engine.osa(c))
    }
    @Test fun combinationOsaHighRisk(){
        val c=Case(fields=Engine.stop.associateWith{"No"}+mapOf("stop.snore" to "Yes","stop.tired" to "Yes","stop.male" to "Yes"))
        assertEquals("High screening risk",Engine.osa(c))
    }
    @Test fun pbwUsesHeightAndSexNotActualWeight(){
        val c=Case(fields=mapOf("age" to "50","height" to "170","weight" to "80","pbwSex" to "Female"))
        assertEquals(61.516,Engine.pbw(c)!!,0.0001)
        assertEquals(Engine.pbw(c)!!,Engine.pbw(c.set("weight","160"))!!,0.0001)
        assertNull(Engine.pbw(c.set("age","8")))
    }
    @Test fun concentrationArithmeticRejectsInvalidInputs(){
        assertEquals(2.4,Engine.volume(12.0,5.0)!!,0.0001)
        assertNull(Engine.volume(12.0,0.0));assertNull(Engine.volume(Double.NaN,5.0))
        assertNull(Engine.volume(12.0,Double.POSITIVE_INFINITY))
    }
    @Test fun timeRequiresOffsetAndValidDate(){
        assertNull(Engine.time("2026-09-01T08:00"))
        assertNull(Engine.time("2026-02-30T08:00Z"))
        assertEquals(Engine.time("2026-09-01T15:00Z"),Engine.time("2026-09-01T08:00-07:00"))
    }
    @Test fun lowDoseAtBoundary(){
        val t=Engine.timeline(ac())
        assertEquals("Timing criterion met",t.steps[0].state)
        assertEquals(Engine.time("2026-09-03T02:00Z"),t.steps[2].earliest)
        assertEquals("Not applicable",t.steps[1].state)
    }
    @Test fun oneSecondBeforeBoundaryFails(){
        val t=Engine.timeline(ac(mapOf("anti.insertion" to "2026-09-02T19:59:59Z")))
        assertEquals("Timing criterion not met",t.steps[0].state);assertNull(t.steps[2].earliest)
    }
    @Test fun highDoseSeparateIntervals(){
        val t=Engine.timeline(ac(mapOf("anti.class" to "High","anti.dose" to "5 mg twice daily",
            "anti.insertion" to "2026-09-04T08:00Z","anti.restart" to "2026-09-05T08:00Z")))
        assertEquals(Engine.time("2026-09-04T08:00Z"),t.steps[0].earliest)
        assertEquals(Engine.time("2026-09-05T08:00Z"),t.steps[2].earliest)
    }
    @Test fun missingAdditionalMedicationOrRenalInputsBlock(){
        for(e in listOf("anti.other" to "","anti.other" to "Yes","anti.crcl" to "29","anti.crcl" to "NaN","anti.verified" to "")){
            assertNull(Engine.timeline(ac(mapOf(e))).steps[0].earliest)
        }
    }
    @Test fun restartRequiresHemostasisPunctureStatusAndSurgicalPlan(){
        for(k in listOf("anti.hemostasis","anti.traumatic","anti.surgical")){
            assertNull(Engine.timeline(ac(mapOf(k to ""))).steps[2].earliest)
        }
    }
    @Test fun traumaAndSurgicalLimitDelayRestart(){
        assertEquals(Engine.time("2026-09-04T20:00Z"),Engine.timeline(ac(mapOf("anti.traumatic" to "Yes"))).steps[2].earliest)
        assertEquals(Engine.time("2026-09-06T08:00Z"),Engine.timeline(ac(mapOf("anti.surgical" to "2026-09-06T08:00Z"))).steps[2].earliest)
    }
    @Test fun exposureUsesCatheterRemovalInterval(){
        val c=ac(mapOf("anti.catheter" to "Yes","anti.exposed" to "Yes","anti.insertion" to "2026-08-30T08:00Z","anti.removal" to "2026-09-02T20:00Z"))
        assertEquals(Engine.time("2026-09-02T20:00Z"),Engine.timeline(c).steps[1].earliest)
        assertEquals(Engine.time("2026-09-03T02:00Z"),Engine.timeline(c).steps[2].earliest)
        assertNull(Engine.timeline(c.set("anti.removal","2026-08-29T08:00Z")).steps[2].earliest)
    }
    @Test fun inconsistentExposureHistoryDoesNotProduceRemovalTime(){
        val c=ac(mapOf("anti.catheter" to "Yes","anti.exposed" to "Yes","anti.removal" to "2026-09-03T20:00Z"))
        assertNull(Engine.timeline(c).steps[1].earliest)
    }
    @Test fun staleExposureCannotBypassInsertionWhenNoCatheter(){
        val c=ac(mapOf("anti.exposed" to "Yes","anti.insertion" to "2026-09-01T09:00Z"))
        assertNull(Engine.timeline(c).steps[2].earliest)
    }
    @Test fun otherDrugsRequireExplicitManualRules(){
        assertNull(Engine.timeline(ac(mapOf("anti.drug" to "Warfarin"))).steps[0].earliest)
    }
    @Test fun drugDoseOrRenalChangeInvalidatesVerification(){
        for(k in listOf("anti.drug","anti.dose","anti.indication","anti.crcl","anti.class")){
            assertEquals("",ac().set(k,"changed").v("anti.verified"))
        }
    }
    @Test fun assessmentRevisionInvalidatesReviewAndKeepsPlanText(){
        val c=Case(fields=mapOf("plan.reason" to "Original"),revision=4,reviewed=true)
        val updated=c.set("age","68")
        assertEquals(5,updated.revision);assertFalse(updated.reviewed);assertEquals("Original",updated.v("plan.reason"))
        assertEquals(4,c.set("plan.reason","Edited").revision)
    }
    @Test fun airwayRestrictionKeepsTechniqueOpenAndNICEHasScope(){
        val airway=Content.advice(Case(fields=mapOf("airway.opening" to "2"))).first{it.id=="airway-access"}
        assertTrue(airway.missing.isNotBlank());assertFalse(airway.text.contains("must use"))
        val base=mapOf("age" to "68","urgency" to "Elective","complexity" to "Major / complex")
        assertTrue(Content.advice(Case(fields=base+("procedure" to "Knee replacement"))).any{it.id=="fbc"})
        assertFalse(Content.advice(Case(fields=base+("procedure" to "CABG"))).any{it.id=="fbc"})
        assertFalse(Engine.rcriEligible(Case(fields=base+("procedure" to "CABG"))))
    }
}
